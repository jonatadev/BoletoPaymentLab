import java.io.DataOutputStream;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.errors.GroupAuthorizationException;
import org.apache.kafka.common.errors.SaslAuthenticationException;
import org.apache.kafka.common.errors.SslAuthenticationException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.apache.kafka.common.resource.ResourceType;

public final class KafkaSecurityChecks {
    private static final int CHECK_TIMEOUT_SECONDS = 45;
    private static final String TOPIC = KafkaSecurity.TOPIC;
    private static final String GROUP = KafkaSecurity.GROUP;
    private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);
    private static final Path MOUNT = Path.of("/run/kafka");

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            snapshot(args[0]);
            return;
        }
        Properties app = credentials(KafkaSecurity.APP);
        Properties ui = credentials(KafkaSecurity.UI);
        String token = UUID.randomUUID().toString();

        publish(app, TOPIC, token, true);
        passed("KAF-01");
        denied(TopicAuthorizationException.class, () -> publish(ui, TOPIC, token, false));
        passed("KAF-02");
        try (var consumer = consumer(app, GROUP)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
            boolean found = false;
            while (!found && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    found |= token.equals(record.value());
                }
            }
            if (!found) {
                throw new AssertionError("KAF-03 did not receive its marker. Assigned partitions: "
                        + consumer.assignment().size());
            }
            consumer.commitSync(Duration.ofSeconds(CHECK_TIMEOUT_SECONDS));
        }
        passed("KAF-03");
        try (var consumer = consumer(ui, GROUP)) {
            consumer.assign(List.of(PARTITION));
            consumer.seek(PARTITION, 0);
            denied(TopicAuthorizationException.class, () -> pollUntilRejected(consumer));
        }
        passed("KAF-04");
        try (var consumer = consumer(app, GROUP + "-denied")) {
            consumer.subscribe(List.of(TOPIC));
            denied(GroupAuthorizationException.class, () -> pollUntilRejected(consumer));
        }
        passed("KAF-05");

        String disposableTopic = "authorization-check-" + token;
        try (Admin admin = Admin.create(KafkaSecurity.adminProperties(MOUNT))) {
            admin.createTopics(List.of(new NewTopic(disposableTopic, 1, (short) 1))).all()
                    .get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        try (Admin admin = Admin.create(app)) {
            denied(TopicAuthorizationException.class,
                    () -> admin.createTopics(List.of(new NewTopic(disposableTopic + "-denied", 1, (short) 1)))
                            .all().get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            denied(TopicAuthorizationException.class,
                    () -> admin.deleteTopics(List.of(disposableTopic)).all().get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        passed("KAF-06");
        denied(SaslAuthenticationException.class, () -> describe(KafkaSecurity.scram(
                MOUNT, KafkaSecurity.APP, "0".repeat(64))));
        passed("KAF-07");
        verifyAnonymousConnectionClosed();
        passed("KAF-08");
        var untrusted = copy(app);
        untrusted.remove("ssl.truststore.location");
        untrusted.remove("ssl.truststore.type");
        denied(SslAuthenticationException.class, () -> describe(untrusted));
        var wrongHost = copy(app);
        wrongHost.setProperty("bootstrap.servers", "kafka-bad-hostname:29092");
        denied(SslAuthenticationException.class, () -> describe(wrongHost));
        passed("KAF-09");

        var binding = KafkaSecurity.allow("acl-check-" + token, ResourceType.TOPIC, TOPIC, AclOperation.DESCRIBE);
        try (Admin admin = Admin.create(KafkaSecurity.adminProperties(MOUNT))) {
            admin.createAcls(List.of(binding)).all().get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            try {
                require(admin.describeAcls(binding.toFilter()).values()
                        .get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS).contains(binding));
            } finally {
                admin.deleteAcls(List.of(binding.toFilter())).all().get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            require(admin.describeAcls(binding.toFilter()).values()
                    .get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS).isEmpty());
            System.out.println("kafka-security-admin created and revoked a dedicated test ACL.");
        }
        passed("KAF-10");
    }

    private static void verifyAnonymousConnectionClosed() throws Exception {
        var trusted = KeyStore.getInstance(KeyStore.getDefaultType());
        trusted.load(null);
        try (var certificate = Files.newInputStream(MOUNT.resolve("ca.pem"))) {
            trusted.setCertificateEntry("local-ca", CertificateFactory.getInstance("X.509")
                    .generateCertificate(certificate));
        }
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        managers.init(trusted);
        var tls = SSLContext.getInstance("TLS");
        tls.init(null, managers.getTrustManagers(), null);
        try (var socket = (SSLSocket) tls.getSocketFactory().createSocket("kafka", 29092)) {
            socket.setSoTimeout(CHECK_TIMEOUT_SECONDS * 1000);
            var parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            var request = new DataOutputStream(socket.getOutputStream());
            request.writeInt(14);
            request.writeShort(3); // Metadata API key
            request.writeShort(0);
            request.writeInt(1);
            request.writeShort(0);
            request.writeInt(0);
            request.flush();
            require(socket.getInputStream().read() == -1);
        }
    }

    private static void snapshot(String mode) throws Exception {
        boolean legacy = "legacy".equals(mode);
        require(legacy || "snapshot".equals(mode));
        Properties config = legacy ? new Properties() : credentials(KafkaSecurity.APP);
        if (legacy) {
            config.setProperty("bootstrap.servers", "kafka:29092");
        }
        String marker = "synthetic-kafka-migration-marker";
        try (Admin client = Admin.create(config)) {
            if (legacy) {
                client.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1))).all().get(20, TimeUnit.SECONDS);
                publish(config, TOPIC, marker, true);
            }
            try (var reader = consumer(config, GROUP)) {
                reader.assign(List.of(PARTITION));
                reader.seek(PARTITION, 0);
                long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
                boolean found = false;
                while (!found && System.nanoTime() < deadline) {
                    for (var record : reader.poll(Duration.ofMillis(500))) {
                        found |= record.offset() == 0 && marker.equals(record.value());
                    }
                }
                require(found);
                if (legacy) {
                    reader.commitSync(Map.of(PARTITION,
                            new org.apache.kafka.clients.consumer.OffsetAndMetadata(1)));
                }
                require(reader.committed(java.util.Set.of(PARTITION)).get(PARTITION).offset() == 1);
            }
            var cluster = client.describeCluster().clusterId().get(12, TimeUnit.SECONDS);
            var topic = client.describeTopics(List.of(TOPIC)).topicNameValues().get(TOPIC)
                    .get(12, TimeUnit.SECONDS).topicId();
            System.out.printf("KAF-11 SNAPSHOT %s %s offset=1 marker=verified%n", cluster, topic);
        }
    }

    private static Properties credentials(String principal) throws Exception {
        return KafkaSecurity.scram(MOUNT, principal, Files.readString(MOUNT.resolve(principal + ".password")).strip());
    }

    private static void describe(Properties props) throws Exception {
        try (Admin admin = Admin.create(props)) {
            admin.describeTopics(List.of(TOPIC)).allTopicNames().get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static void publish(Properties props, String topic, String message, boolean idempotent) throws Exception {
        var config = copy(props);
        config.setProperty("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        config.setProperty("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        config.setProperty("enable.idempotence", Boolean.toString(idempotent));
        config.setProperty("acks", "all");
        config.setProperty("max.block.ms", Integer.toString(CHECK_TIMEOUT_SECONDS * 1000));
        try (var producer = new KafkaProducer<String, String>(config)) {
            producer.send(new ProducerRecord<>(topic, 0, null, message)).get(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private static KafkaConsumer<String, String> consumer(Properties props, String group) {
        var config = copy(props);
        config.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.setProperty("auto.offset.reset", "earliest");
        config.setProperty("enable.auto.commit", "false");
        config.setProperty("group.id", group);
        return new KafkaConsumer<>(config);
    }

    private static void pollUntilRejected(KafkaConsumer<String, String> consumer) {
        long deadline = System.nanoTime() + Duration.ofSeconds(CHECK_TIMEOUT_SECONDS).toNanos();
        while (System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(300));
        }
    }

    private static Properties copy(Properties source) {
        var config = new Properties();
        config.putAll(source);
        return config;
    }

    private static void denied(Class<? extends Exception> expected, Operation action) throws Exception {
        try {
            action.execute();
        } catch (Exception failure) {
            Throwable cause = failure instanceof ExecutionException ? failure.getCause() : failure;
            if (!expected.isInstance(cause)) {
                throw new AssertionError("Expected " + expected.getSimpleName() + ", received "
                        + cause.getClass().getSimpleName());
            }
            return;
        }
        throw new AssertionError("Expected " + expected.getSimpleName() + ", operation succeeded");
    }

    private static void require(boolean expectation) {
        if (!expectation) {
            throw new AssertionError("Kafka security check failed");
        }
    }

    private static void passed(String id) {
        System.out.println(id + " passed");
    }

    @FunctionalInterface
    private interface Operation {
        void execute() throws Exception;
    }
}
