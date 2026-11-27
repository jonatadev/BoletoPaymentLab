package org.example.samplemicroservice.boleto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "BPL_IT_ALLOY_OTLP", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IssuanceTraceTests.TopicConfig.class)
class IssuanceTraceTests extends PostgresIntegrationTest {
    @Container static final KafkaContainer kafka = new KafkaContainer("apache/kafka:4.2.2");
    private static final String DATABASE = "trace_" + UUID.randomUUID().toString().replace("-", "");
    private static final String TOPIC = "boleto.trace.lab";
    private static IntegrationIdentity identity;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();

    @TestConfiguration(proxyBeanMethods = false)
    static class TopicConfig {
        @Bean
        NewTopic traceTopic() {
            return new NewTopic(TOPIC, 1, (short) 1);
        }
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry p) throws Exception {
        identity = new IntegrationIdentity();
        p.add("spring.datasource.url", () -> postgresUrl(DATABASE));
        p.add("spring.datasource.username", PostgresIntegrationTest::postgresUser);
        p.add("spring.datasource.password", PostgresIntegrationTest::postgresPassword);
        p.add("spring.flyway.schemas", () -> DATABASE);
        p.add("spring.jpa.properties.hibernate.default_schema", () -> DATABASE);
        p.add("bpl.security.issuer", () -> IntegrationIdentity.ISSUER);
        p.add("bpl.security.jwks", () -> identity.url() + "/jwks");
        p.add("bpl.events.enabled", () -> true);
        p.add("bpl.events.topic", () -> TOPIC);
        p.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        p.add("spring.kafka.properties[security.protocol]", () -> "PLAINTEXT");
        p.add("management.tracing.export.enabled", () -> true);
        p.add("management.tracing.sampling.probability", () -> 1.0);
        p.add("management.opentelemetry.tracing.export.otlp.endpoint", () -> System.getenv("BPL_IT_ALLOY_OTLP"));
    }

    @AfterAll
    static void closeIdentity() {
        if (identity != null) identity.close();
    }

    @Test
    void shouldCarryIssuanceContextThroughKafkaAndPersistReceipt() throws Exception {
        String trace = UUID.randomUUID().toString().replace("-", "");
        String parent = "abcdef0123456789";
        String requestKey = UUID.randomUUID().toString();
        String payload =
                """
                {"customerId":"synthetic-trace-check","amount":10,"dueDate":"%s"}
                """
                        .formatted(LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(2));
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/boletos"))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + identity.token("boletos:issue", "boleto-api"))
                        .header("traceparent", "00-" + trace + "-" + parent + "-01")
                        .header("Idempotency-Key", requestKey)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
        var issued = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(issued.statusCode()).isEqualTo(201);
        assertThat(issued.headers().firstValue("X-Trace-Id")).contains(trace);
        UUID id = UUID.fromString(json.readTree(issued.body()).path("id").asText());
        String queryBase = System.getenv().getOrDefault("BPL_IT_JAEGER_QUERY", "http://127.0.0.1:16686");
        var query =
                HttpRequest.newBuilder(URI.create(queryBase + "/api/traces/" + trace))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
        await().atMost(Duration.ofSeconds(75))
                .untilAsserted(() -> {
                    var record =
                            jdbc.sql(
                                            """
                                            select o.traceparent from boleto_outbox o join processed_boleto_event p
                                            on p.event_id=o.event_id and p.aggregate_id=o.aggregate_id
                                            where o.aggregate_id=:id and o.correlation_id=:trace and o.published_at is not null
                                            """)
                                    .param("id", id)
                                    .param("trace", trace)
                                    .query(String.class)
                                    .optional();
                    assertThat(record).isPresent();
                    var response = client.send(query, HttpResponse.BodyHandlers.ofString());
                    assertThat(response.statusCode()).isEqualTo(200);
                    Map<String, String> parents = new HashMap<>();
                    Map<String, String> operations = new HashMap<>();
                    for (JsonNode span : json.readTree(response.body()).path("data").path(0).path("spans")) {
                        assertThat(span.path("traceID").asText()).isEqualTo(trace);
                        String spanId = span.path("spanID").asText();
                        operations.put(span.path("operationName").asText(), spanId);
                        for (JsonNode ref : span.path("references")) {
                            if ("CHILD_OF".equals(ref.path("refType").asText()))
                                parents.put(spanId, ref.path("spanID").asText());
                        }
                        for (JsonNode tag : span.path("tags")) {
                            if ("span.kind".equals(tag.path("key").asText()))
                                operations.put(tag.path("value").asText(), spanId);
                        }
                    }
                    assertThat(operations)
                            .containsKeys(
                                    "server",
                                    "consumer",
                                    "bpl.issuance.validate",
                                    "bpl.issuance.persist",
                                    "bpl.outbox.append",
                                    "bpl.outbox.publish");
                    assertThat(parents.get(operations.get("server"))).isEqualTo(parent);
                    assertThat(record.get())
                            .isEqualTo("00-" + trace + "-" + operations.get("bpl.outbox.append") + "-01");
                    assertThat(parents.get(operations.get("bpl.outbox.publish")))
                            .isEqualTo(operations.get("bpl.outbox.append"));
                    assertThat(parents.get(operations.get("consumer"))).isEqualTo(operations.get("bpl.outbox.publish"));
                    assertThat(response.body()).doesNotContain(requestKey, "synthetic-trace-check");
                });
        System.out.println("Verified distributed trace " + trace);
    }
}
