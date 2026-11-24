package org.example.samplemicroservice.boleto.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.example.samplemicroservice.boleto.PostgresIntegrationTest;
import org.example.samplemicroservice.boleto.application.BoletoLifecycle;
import org.example.samplemicroservice.boleto.application.BoletoService;
import org.example.samplemicroservice.boleto.application.IssuanceInput;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(BoletoEventsPostgresTests.TestBeans.class)
class BoletoEventsPostgresTests extends PostgresIntegrationTest {
    private static final String RUN = UUID.randomUUID().toString().replace("-", "");
    private static final String SCHEMA = "bpl_it_" + RUN;
    private static final String SECURE_KAFKA = System.getenv("BPL_IT_SECURE_KAFKA_BOOTSTRAP");
    private static final String TOPIC = SECURE_KAFKA == null ? "boleto-it-" + RUN : "boleto.lifecycle.v1";
    private static final KafkaContainer KAFKA =
            SECURE_KAFKA == null ? new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.2")) : null;

    static {
        if (KAFKA != null) {
            KAFKA.start();
            Runtime.getRuntime().addShutdownHook(new Thread(KAFKA::stop, "kafka-integration-test-stop"));
            try (Admin admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
                admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1))).all().get(30, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }

    @Autowired private BoletoService service;
    @Autowired private BoletoLifecycle lifecycle;
    @Autowired private OutboxRelay relay;
    @Autowired private BoletoEventConsumer consumer;
    @Autowired private ControlledTransport transport;
    @Autowired private JdbcClient jdbc;
    @Autowired private Clock clock;
    @Autowired private ObjectMapper json;
    @Autowired private MeterRegistry meters;
    @Autowired private PlatformTransactionManager transactions;

    @DynamicPropertySource
    static void integration(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> postgresUrl(SCHEMA));
        properties.add("spring.datasource.username", PostgresIntegrationTest::postgresUser);
        properties.add("spring.datasource.password", PostgresIntegrationTest::postgresPassword);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("bpl.events.enabled", () -> true);
        properties.add("bpl.events.poll-delay", () -> "1h");
        properties.add("bpl.events.topic", () -> TOPIC);
        properties.add("spring.kafka.consumer.group-id",
                () -> SECURE_KAFKA == null ? "boleto-it-" + RUN : "boleto-lab-educational");
        properties.add("spring.kafka.bootstrap-servers",
                () -> SECURE_KAFKA == null ? KAFKA.getBootstrapServers() : SECURE_KAFKA);
        if (SECURE_KAFKA == null) {
            properties.add("spring.kafka.properties[security.protocol]", () -> "PLAINTEXT");
        }
    }

    @Test
    void shouldRecoverPendingEventsAfterTransportFailureAndDeduplicateDelivery() throws Exception {
        var original = service.issue("outbox-test", new IssuanceInput(
                "customer-outbox", new BigDecimal("12.34"), LocalDate.now(clock).plusDays(1))).boleto();
        lifecycle.change(original.id(), BoletoStatus.PAGO);
        assertThat(relay.processOne()).isFalse();
        assertThat(jdbc.sql("select count(*) from boleto_outbox where published_at is null")
                .query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("select attempts from boleto_outbox where event_type = 'BOLETO_ISSUED'")
                .query(Integer.class).single()).isEqualTo(1);
        jdbc.sql("update boleto_outbox set next_attempt_at = :due")
                .param("due", clock.instant().minusSeconds(1).atOffset(ZoneOffset.UTC)).update();
        await().atMost(Duration.ofSeconds(45)).until(relay::processOne);
        await().atMost(Duration.ofSeconds(45)).until(relay::processOne);
        assertThat(transport.sent).extracting(BoletoEvent::eventType).containsExactly("BOLETO_ISSUED", "BOLETO_PAID");
        assertThat(jdbc.sql("select count(*) from boleto_outbox where published_at is not null")
                .query(Integer.class).single()).isEqualTo(2);
        var event = transport.sent.getFirst();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(receipts(event.eventId())).isEqualTo(1));
        var duplicates = meters.counter("bpl.events.duplicates");
        double duplicatesBeforeReplay = duplicates.count();
        transport.delegate.publish(event, null);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(duplicates.count()).isGreaterThan(duplicatesBeforeReplay));
        assertThat(receipts(event.eventId())).isEqualTo(1);
        assertThat(meters.counter("bpl.events.duplicates").count()).isEqualTo(1);

        var held = service.issue("outbox-held", new IssuanceInput(
                "customer-held", new BigDecimal("2.00"), LocalDate.now(clock).plusDays(1))).boleto();
        var free = service.issue("outbox-free", new IssuanceInput(
                "customer-free", new BigDecimal("3.00"), LocalDate.now(clock).plusDays(1))).boleto();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var worker = CompletableFuture.runAsync(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.sql("select event_id from boleto_outbox where aggregate_id = :id for update")
                    .param("id", held.id()).query(UUID.class).single();
            locked.countDown();
            try {
                if (!release.await(15, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting for the competing relay");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
        }));
        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(relay.processOne()).isTrue();
            assertThat(transport.sent.getLast().aggregateId()).isEqualTo(free.id());
        } finally {
            release.countDown();
        }
        worker.get(10, TimeUnit.SECONDS);
        assertThat(relay.processOne()).isTrue();
        assertThat(transport.sent.getLast().aggregateId()).isEqualTo(held.id());
    }

    private int receipts(UUID eventId) {
        return jdbc.sql("select count(*) from processed_boleto_event where event_id = :id")
                .param("id", eventId).query(Integer.class).single();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean @Primary
        ControlledTransport controlledTransport(KafkaOutboxTransport delegate) {
            return new ControlledTransport(delegate);
        }
    }

    static class ControlledTransport implements OutboxTransport {
        private final KafkaOutboxTransport delegate;
        private final List<BoletoEvent> sent = new ArrayList<>();
        private boolean fail = true;

        ControlledTransport(KafkaOutboxTransport delegate) {
            this.delegate = delegate;
        }

        @Override
        public void publish(BoletoEvent event, String traceparent) throws PublishFailure {
            if (fail) {
                fail = false;
                throw new PublishFailure();
            }
            delegate.publish(event, traceparent);
            sent.add(event);
        }
    }
}
