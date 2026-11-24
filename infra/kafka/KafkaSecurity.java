import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.ScramCredentialInfo;
import org.apache.kafka.clients.admin.ScramMechanism;
import org.apache.kafka.clients.admin.UserScramCredentialUpsertion;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;

public final class KafkaSecurity {
    static final String TOPIC = "boleto.lifecycle.v1";
    static final String GROUP = "boleto-lab-educational";
    static final String APP = "svc-boleto-api";
    static final String UI = "svc-kafka-ui";
    private static final int ITERATIONS = 8192;
    private static final Set<AclBinding> POLICY = Set.of(
            allow(APP, ResourceType.TOPIC, TOPIC, AclOperation.READ),
            allow(APP, ResourceType.TOPIC, TOPIC, AclOperation.WRITE),
            allow(APP, ResourceType.GROUP, GROUP, AclOperation.READ),
            allow(UI, ResourceType.TOPIC, TOPIC, AclOperation.DESCRIBE),
            allow(UI, ResourceType.GROUP, GROUP, AclOperation.DESCRIBE));

    public static void main(String[] args) {
        String stage = "connect to administrative listener";
        try {
            Path mount = Path.of("/run/kafka");
            try (Admin admin = Admin.create(adminProperties(mount))) {
                stage = "verify existing ACLs";
                verifyPolicy(admin, false);
                stage = "verify lifecycle topic";
                ensureTopic(admin);
                stage = "verify SCRAM credentials";
                for (String principal : List.of(APP, UI)) {
                    ensureCredential(admin, principal, Files.readString(mount.resolve(principal + ".password")).strip());
                }
                var missing = new HashSet<>(POLICY);
                stage = "reconcile ACLs";
                missing.removeAll(managedPolicy(admin));
                if (!missing.isEmpty()) {
                    admin.createAcls(missing).all().get();
                    missing.forEach(acl -> System.out.printf("ACL added by kafka-security-admin: %s %s %s%n",
                            acl.entry().principal(), acl.entry().operation(), acl.pattern().name()));
                }
                verifyPolicy(admin, true);
                for (String principal : List.of(APP, UI)) {
                    stage = "verify client authentication and topic metadata for " + principal;
                    String password = Files.readString(mount.resolve(principal + ".password")).strip();
                    try (Admin client = Admin.create(scram(mount, principal, password))) {
                        client.describeTopics(List.of(TOPIC)).allTopicNames().get();
                    }
                }
            }
            System.out.println("Kafka identities, topic and ACLs verified.");
        } catch (Exception failure) {
            var cause = failure instanceof ExecutionException ? failure.getCause() : failure;
            System.err.println("Kafka provisioning failed during " + stage + ": " + cause.getClass().getSimpleName()
                    + (cause instanceof IllegalStateException ? " " + cause.getMessage() : ""));
            System.exit(1);
        }
    }

    static Properties adminProperties(Path mount) throws IOException {
        var config = load(mount.resolve("admin.properties"));
        config.setProperty("default.api.timeout.ms", "45000");
        config.setProperty("request.timeout.ms", "15000");
        return config;
    }

    static Properties load(Path path) throws IOException {
        var result = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            result.load(reader);
        }
        return result;
    }

    static Properties scram(Path mount, String principal, String password) {
        if (!principal.matches("[a-z0-9-]+") || !password.matches("[a-f0-9]{64}")) {
            throw new IllegalStateException("Invalid local SCRAM identity material.");
        }
        var properties = new Properties();
        properties.setProperty("bootstrap.servers", "kafka:29092");
        properties.setProperty("security.protocol", "SASL_SSL");
        properties.setProperty("sasl.mechanism", "SCRAM-SHA-512");
        properties.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.scram.ScramLoginModule required "
                + "username=\"" + principal + "\" password=\"" + password + "\";");
        properties.setProperty("ssl.endpoint.identification.algorithm", "https");
        properties.setProperty("ssl.truststore.type", "PEM");
        properties.setProperty("ssl.truststore.location", mount.resolve("ca.pem").toString());
        properties.setProperty("request.timeout.ms", "15000");
        properties.setProperty("default.api.timeout.ms", "45000");
        return properties;
    }

    static AclBinding allow(String principal, ResourceType type, String name, AclOperation operation) {
        return new AclBinding(new ResourcePattern(type, name, PatternType.LITERAL),
                new AccessControlEntry("User:" + principal, "*", operation, AclPermissionType.ALLOW));
    }

    private static void ensureTopic(Admin admin) throws Exception {
        try {
            admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1))).all().get();
        } catch (ExecutionException failure) {
            if (!(failure.getCause() instanceof TopicExistsException)) {
                throw failure;
            }
        }
        var partitions = admin.describeTopics(List.of(TOPIC)).topicNameValues().get(TOPIC).get().partitions();
        if (partitions.size() != 3 || partitions.stream().anyMatch(p -> p.replicas().size() != 1)) {
            throw new IllegalStateException("Unexpected lifecycle topic partition or replication configuration.");
        }
    }

    private static void ensureCredential(Admin admin, String principal, String password) throws Exception {
        scram(Path.of("/run/kafka"), principal, password);
        var required = new ScramCredentialInfo(ScramMechanism.SCRAM_SHA_512, ITERATIONS);
        try {
            var description = admin.describeUserScramCredentials(List.of(principal)).description(principal).get();
            if (!description.credentialInfos().equals(List.of(required))) {
                throw new IllegalStateException("SCRAM configuration drift for " + principal + ".");
            }
        } catch (ExecutionException failure) {
            if (!(failure.getCause() instanceof ResourceNotFoundException)) {
                throw failure;
            }
            admin.alterUserScramCredentials(List.of(new UserScramCredentialUpsertion(principal, required, password)))
                    .all().get();
        }
    }

    private static Set<AclBinding> managedPolicy(Admin admin) throws Exception {
        var bindings = new HashSet<>(admin.describeAcls(AclBindingFilter.ANY).values().get());
        bindings.removeIf(acl -> !Set.of("User:" + APP, "User:" + UI, "User:*").contains(acl.entry().principal()));
        return bindings;
    }

    private static void verifyPolicy(Admin admin, boolean complete) throws Exception {
        var current = managedPolicy(admin);
        if (!POLICY.containsAll(current) || (complete && !current.equals(POLICY))) {
            throw new IllegalStateException("Kafka ACL drift detected for managed principals or wildcard user.");
        }
    }
}
