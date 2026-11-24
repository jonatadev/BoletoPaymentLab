package org.example.samplemicroservice.boleto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.samplemicroservice.boleto.application.BoletoService;
import org.example.samplemicroservice.boleto.application.IssuanceInput;
import org.example.samplemicroservice.boleto.application.Snapshot;
import org.example.samplemicroservice.boleto.document.PdfBoxRenderer;
import org.example.samplemicroservice.boleto.document.PdfRenderer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(BoletoPostgresTests.TestBeans.class)
@ExtendWith(OutputCaptureExtension.class)
class BoletoPostgresTests extends PostgresIntegrationTest {
    private static final String SCHEMA = "bpl_it_" + UUID.randomUUID().toString().replace("-", "");
    private static final Instant ISSUE_TIME = Instant.parse("2026-01-02T01:30:00Z");
    private static final LocalDate DUE_DATE = LocalDate.parse("2026-01-03");
    private static IntegrationIdentity identity;
    private static String allPermissions;
    private final HttpClient client = HttpClient.newHttpClient();
    @Autowired private ObjectMapper json;
    @Autowired private MutableClock clock;
    @Autowired private ControlledRenderer renderer;
    @Autowired private BoletoService service;
    @Autowired private JdbcClient jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @LocalServerPort private int port;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) throws Exception {
        identity = new IntegrationIdentity();
        allPermissions =
                identity.token(
                        "boletos:issue boletos:read boletos:list boletos:pay boletos:cancel boletos:audit ops:metrics",
                        "boleto-api");
        properties.add("spring.datasource.url", () -> postgresUrl(SCHEMA));
        properties.add("spring.datasource.username", PostgresIntegrationTest::postgresUser);
        properties.add("spring.datasource.password", PostgresIntegrationTest::postgresPassword);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        properties.add("bpl.security.issuer", () -> IntegrationIdentity.ISSUER);
        properties.add("bpl.security.jwks", () -> identity.url() + "/jwks");
        properties.add("bpl.expiry.enabled", () -> false);
        properties.add("management.tracing.export.enabled", () -> true);
        properties.add("management.opentelemetry.tracing.export.otlp.endpoint", () -> identity.url() + "/v1/traces");
    }

    @AfterAll
    static void stopIdentity() {
        if (identity != null) {
            identity.close();
        }
    }

    @BeforeEach
    void resetTestControls() {
        clock.time.set(ISSUE_TIME);
        renderer.calls.set(0);
        renderer.fail = false;
    }

    @Test
    void shouldIssueConsultAndRegenerateEducationalPdf() throws Exception {
        String key = key();
        var issued = post(key, body("customer-a", "10.00", DUE_DATE));
        assertThat(issued.statusCode()).isEqualTo(201);
        assertThat(renderer.calls).hasValue(0);
        String id = field(issued, "id");
        assertThat(issued.headers().firstValue("Location")).contains("/api/v1/boletos/" + id);
        var lookup = get("/" + id);
        assertThat(lookup.statusCode()).isEqualTo(200);
        assertThat(json.readTree(lookup.body())).isEqualTo(json.readTree(issued.body()));
        var pdf = get("/" + id + "/pdf");
        assertThat(pdf.statusCode()).isEqualTo(200);
        assertThat(pdf.headers().firstValue("Content-Type")).contains("application/pdf");
        try (var document = Loader.loadPDF(pdf.body())) {
            assertThat(new PDFTextStripper().getText(document))
                    .contains("SEM VALIDADE PARA PAGAMENTO", "BRL 10.00", id, "customer-a");
        }
        assertThat(get("/" + id + "/pdf").statusCode()).isEqualTo(200);
        assertThat(renderer.calls).hasValue(2);
        String unknown = "/" + UUID.randomUUID();
        assertProblem(get(unknown), 404, "BOLETO_NOT_FOUND");
        assertProblem(get(unknown + "/pdf"), 404, "BOLETO_NOT_FOUND");
        assertProblem(get("/1-1-1-1-1"), 400, "INVALID_REQUEST");
    }

    @Test
    void shouldExposeAuthorizedCorrelatedTechnicalHistoryWithoutMutatingState() throws Exception {
        String requestKey = key();
        String payload = body("revision-synthetic", "10.00", DUE_DATE);
        var issued = post(requestKey, payload);
        assertThat(issued.statusCode()).isEqualTo(201);
        String id = field(issued, "id");
        String path = "/" + id + "/revisions";
        var before = get(path);
        assertThat(before.statusCode()).isEqualTo(200);
        var initial = json.readTree(before.body()).get(0);
        long first = initial.get("revision").asLong();
        assertThat(initial.get("actor").asText()).isEqualTo("synthetic-client");
        assertThat(initial.get("timestamp").asText()).isNotBlank();
        assertThat(initial.get("operation").asText()).isEqualTo("ADD");
        assertThat(post(requestKey, payload).statusCode()).isEqualTo(200);
        assertThat(json.readTree(get(path).body()).size()).isEqualTo(1);

        var unauthenticated = client.send(raw(path).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertProblem(unauthenticated, 401, "UNAUTHORIZED");
        var readOnly = identity.token("boletos:read", "boleto-api");
        var forbidden =
                client.send(
                        raw(path + "/" + first).header("Authorization", "Bearer " + readOnly).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        assertProblem(forbidden, 403, "FORBIDDEN");

        Instant paymentTime = ISSUE_TIME.plusSeconds(60);
        clock.time.set(paymentTime);
        var paid =
                client.send(
                        request("/" + id + "/payments").POST(HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        assertThat(paid.statusCode()).isEqualTo(200);
        var history = json.readTree(get(path).body());
        assertThat(history.size()).isEqualTo(2);
        var latest = history.get(0);
        long second = latest.get("revision").asLong();
        assertThat(latest.get("operation").asText()).isEqualTo("MOD");

        var original = json.readTree(get(path + "/" + first).body()).get("state");
        var updated = json.readTree(get(path + "/" + second).body()).get("state");
        assertThat(original.get("status").asText()).isEqualTo("EMITIDO");
        assertThat(updated.get("status").asText()).isEqualTo("PAGO");
        assertThat(updated.get("createdAt").asText()).isEqualTo(ISSUE_TIME.toString());
        assertThat(updated.get("updatedAt").asText()).isEqualTo(paymentTime.toString());
        assertThat(updated.get("createdBy").asText()).isEqualTo("synthetic-client");
        assertThat(updated.get("updatedBy").asText()).isEqualTo("synthetic-client");
        assertThat(updated.has("customerId")).isFalse();
        var comparison = json.readTree(get(path + "/compare?from=" + first + "&to=" + second).body());
        assertThat(comparison.get("changedFields").toString()).isEqualTo("[\"status\",\"version\",\"updatedAt\"]");
        assertThat(json.readTree(get(path + "?before=" + second + "&size=1").body()).get(0)).isEqualTo(initial);
        assertProblem(get(path + "/" + Long.MAX_VALUE), 404, "REVISION_NOT_FOUND");

        var eventCorrelations =
                jdbc.sql(
                                """
                                select a.correlation_id
                                from boleto_audit a join boleto_outbox o on o.event_id = a.event_id
                                where a.boleto_id = :id and a.correlation_id = o.correlation_id
                                order by a.aggregate_version
                                """)
                        .param("id", UUID.fromString(id))
                        .query(String.class)
                        .list();
        assertThat(eventCorrelations)
                .containsExactly(initial.get("correlationId").asText(), latest.get("correlationId").asText());

        var expiring = post(key(), body("revision-expiry", "10.00", DUE_DATE));
        String expiringId = field(expiring, "id");
        clock.time.set(Instant.parse("2026-01-05T04:00:00Z"));
        assertThat(json.readTree(get("/" + expiringId + "/revisions").body()).size()).isEqualTo(1);
        assertThat(
                        jdbc.sql("select status from boleto where id = :id")
                                .param("id", UUID.fromString(expiringId))
                                .query(String.class)
                                .single())
                .isEqualTo("EMITIDO");
    }

    @Test
    void shouldReplayCanonicalPayloadEvenAfterDueDate() throws Exception {
        String key = key();
        var original = post(key, body("customer-replay", "10.00", DUE_DATE));
        assertThat(original.statusCode()).isEqualTo(201);
        clock.time.set(Instant.parse("2026-01-05T04:00:00Z"));
        var replay = post(key, body("customer-replay", "10", DUE_DATE));
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(field(replay, "id")).isEqualTo(field(original, "id"));
        assertProblem(post(key, body("customer-replay", "11", DUE_DATE)), 409, "IDEMPOTENCY_CONFLICT");
        assertThat(countFor("customer-replay")).isEqualTo(1);
    }

    @Test
    void shouldTemporarilyRejectConcurrentRequestsAndResolveAfterCommit() throws Exception {
        String key = key();
        String customer = "customer-concurrent";
        String matching = body(customer, "10.00", DUE_DATE);
        String different = body(customer, "12.00", DUE_DATE);
        var hold = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = holdIssuance(customer, key, hold, release, false);
        try {
            assertThat(hold.await(10, TimeUnit.SECONDS)).isTrue();
            assertInProgress(post(key, matching));
            assertInProgress(post(key, different));
        } finally {
            release.countDown();
        }
        var created = first.get(10, TimeUnit.SECONDS);
        assertThat(created.created()).isTrue();
        var replay = post(key, matching);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(field(replay, "id")).isEqualTo(created.boleto().id().toString());
        var rejected = post(key, different);
        assertProblem(rejected, 409, "IDEMPOTENCY_CONFLICT");
        assertThat(rejected.headers().firstValue("Retry-After")).isEmpty();
        assertThat(countFor(customer)).isEqualTo(1);
    }

    @Test
    void shouldAllowNewIssuanceAfterOriginalTransactionRollsBack() throws Exception {
        String key = key();
        String customer = "customer-rollback";
        var hold = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = holdIssuance(customer, key, hold, release, true);
        try {
            assertThat(hold.await(10, TimeUnit.SECONDS)).isTrue();
            assertInProgress(post(key, body(customer, "10.00", DUE_DATE)));
        } finally {
            release.countDown();
        }
        var rolledBack = first.get(10, TimeUnit.SECONDS);
        assertThat(countFor(customer)).isZero();
        assertThat(eventCount(rolledBack.boleto().id())).isZero();
        var retry = post(key, body(customer, "10.00", DUE_DATE));
        assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(field(retry, "id")).isNotEqualTo(rolledBack.boleto().id().toString());
        assertThat(countFor(customer)).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateScopeAtTheDatabaseBoundary() throws Exception {
        String key = key();
        String payload = body("customer-db-constraint", "10", DUE_DATE);
        var original = post(key, payload);
        var other = post(key(), payload);
        assertThat(original.statusCode()).isEqualTo(201);
        assertThat(other.statusCode()).isEqualTo(201);
        UUID otherId = UUID.fromString(field(other, "id"));
        assertThatThrownBy(() ->
                        jdbc.sql(
                                        """
                                        update idempotent_issuance set request_key = :key where boleto_id = :id
                                        """)
                                .param("key", key)
                                .param("id", otherId)
                                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_issuance_scope");
    }

    @Test
    void shouldPreserveIdentityAcrossPdfFailure() throws Exception {
        String key = key();
        String customer = "customer-render-failure";
        var issued = post(key, body(customer, "10.00", DUE_DATE));
        assertThat(issued.statusCode()).isEqualTo(201);
        String id = field(issued, "id");
        renderer.fail = true;
        assertProblem(get("/" + id + "/pdf"), 500, "PDF_RENDER_FAILED");
        renderer.fail = false;
        assertThat(get("/" + id + "/pdf").statusCode()).isEqualTo(200);
        assertThat(field(get("/" + id), "id")).isEqualTo(id);
        assertThat(countFor(customer)).isEqualTo(1);
    }

    @Test
    void shouldValidateInputAndIsolateKeysByCustomer() throws Exception {
        String customer = "customer-validation";
        String key = key();
        assertProblem(post(null, body(customer, "10", DUE_DATE)), 400, "INVALID_REQUEST");
        assertProblem(post(key, body(customer, "0", DUE_DATE)), 400, "INVALID_REQUEST");
        assertProblem(post(key, body(customer, "0.011", DUE_DATE)), 400, "INVALID_REQUEST");
        assertProblem(post(key, body(customer, "1000000", DUE_DATE)), 400, "INVALID_REQUEST");
        assertProblem(post(key, body(customer, "10", LocalDate.parse("2026-01-01"))), 400, "INVALID_REQUEST");
        assertProblem(post(key, body("", "10", DUE_DATE)), 400, "INVALID_REQUEST");
        assertProblem(
                post(key, body(customer, "10", DUE_DATE).replace("2026-01-03", "2026-02-30")), 400, "INVALID_REQUEST");
        assertProblem(
                post(key, body(customer, "10", DUE_DATE).replace("\"amount\":10", "\"amount\":10,\"amount\":11")),
                400,
                "INVALID_REQUEST");
        assertThat(countFor(customer)).isZero();
        var first = post(key, body(customer, "0.01", LocalDate.parse("2026-01-02")));
        var second = post(key, body("customer-other", "999999.99", DUE_DATE));
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(field(first, "id")).isNotEqualTo(field(second, "id"));
    }

    private CompletableFuture<BoletoService.IssueResult> holdIssuance(
            String customer, String key, CountDownLatch hold, CountDownLatch release, boolean rollback) {
        return CompletableFuture.supplyAsync(() ->
                new TransactionTemplate(transactions)
                        .execute(status -> {
                            var result =
                                    service.issue(
                                            key, new IssuanceInput(customer, new java.math.BigDecimal("10"), DUE_DATE));
                            hold.countDown();
                            try {
                                if (!release.await(20, TimeUnit.SECONDS)) {
                                    throw new AssertionError("Timed out waiting for the HTTP contender");
                                }
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(exception);
                            }
                            if (rollback) {
                                status.setRollbackOnly();
                            }
                            return result;
                        }));
    }

    private void assertInProgress(HttpResponse<byte[]> response) {
        assertProblem(response, 409, "OPERATION_IN_PROGRESS");
        assertThat(response.headers().firstValue("Retry-After")).contains("1");
    }

    private void assertProblem(HttpResponse<byte[]> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("application/problem+json"));
        assertThat(field(response, "code")).isEqualTo(code);
    }

    private int countFor(String customer) {
        return jdbc.sql("select count(*) from boleto where customer_id = :customer")
                .param("customer", customer)
                .query(Integer.class)
                .single();
    }

    private int eventCount(UUID boleto) {
        return jdbc.sql("select count(*) from boleto_outbox where aggregate_id = :boleto")
                .param("boleto", boleto)
                .query(Integer.class)
                .single();
    }

    @Test
    void shouldEnforceJwtAndOperationScopes() throws Exception {
        assertProblem(authenticatedGet(null), 401, "UNAUTHORIZED");
        assertProblem(authenticatedGet("invalid"), 401, "UNAUTHORIZED");
        assertProblem(authenticatedGet(identity.token("boletos:list", "different-api")), 401, "UNAUTHORIZED");
        assertProblem(authenticatedGet(identity.token("boletos:read", "boleto-api")), 403, "FORBIDDEN");
        assertThat(get("").statusCode()).isEqualTo(200);
    }

    private HttpResponse<byte[]> authenticatedGet(String token) throws Exception {
        var builder = raw("");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void shouldKeepAuditAndOutboxConsistentAcrossConcurrentTerminalCommands() throws Exception {
        var issued = post(key(), body("customer-transition", "10", DUE_DATE));
        String id = field(issued, "id");
        var pay =
                client.sendAsync(
                        request("/" + id + "/payments").POST(HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        var cancel =
                client.sendAsync(
                        request("/" + id + "/cancellations").POST(HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        var replies = java.util.List.of(pay.get(10, TimeUnit.SECONDS), cancel.get(10, TimeUnit.SECONDS));
        assertThat(replies).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
        var rejected = replies.stream().filter(reply -> reply.statusCode() == 409).findFirst().orElseThrow();
        assertProblem(rejected, 409, "INVALID_STATE_TRANSITION");
        var history = json.readTree(get("/" + id + "/history").body());
        assertThat(history.size()).isEqualTo(2);
        assertThat(history.get(0).path("eventType").stringValue()).isEqualTo("BOLETO_ISSUED");
        assertThat(history.get(1).path("previousStatus").stringValue()).isEqualTo("EMITIDO");
        assertThat(history.get(1).path("newStatus").stringValue()).isIn("PAGO", "CANCELADO");
        assertThat(field(get("/" + id), "dueDate")).isEqualTo(DUE_DATE.toString());
        assertThat(eventCount(UUID.fromString(id))).isEqualTo(2);
    }

    @Test
    void shouldFilterAndMaterializeEffectiveExpiryOnce() throws Exception {
        var issued = post(key(), body("customer-expiry", "10", DUE_DATE));
        String id = field(issued, "id");
        post(key(), body("customer-expiry", "20", DUE_DATE));
        clock.time.set(Instant.parse("2026-01-05T00:00:00Z"));
        var listing =
                get("?customerId=customer-expiry&status=VENCIDO&from=2026-01-01&to=2026-01-01&size=1&sort=amount,asc");
        assertThat(listing.statusCode()).isEqualTo(200);
        var list = json.readTree(listing.body());
        assertThat(list.path("totalElements").intValue()).isEqualTo(2);
        assertThat(list.path("content").get(0).path("id").stringValue()).isEqualTo(id);
        assertThat(eventCount(UUID.fromString(id))).isEqualTo(1);
        assertProblem(get("?size=101"), 400, "INVALID_REQUEST");
        assertProblem(get("?sort=customerId,asc"), 400, "INVALID_REQUEST");
        assertThat(field(get("/" + id), "status")).isEqualTo("VENCIDO");
        assertThat(field(get("/" + id), "status")).isEqualTo("VENCIDO");
        assertProblem(
                client.send(
                        request("/" + id + "/payments").POST(HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofByteArray()),
                409,
                "INVALID_STATE_TRANSITION");
        assertThat(eventCount(UUID.fromString(id))).isEqualTo(2);
    }

    @Test
    void shouldCommitExpiryWhenTheFirstAccessIsARejectedPayment() throws Exception {
        var issued = post(key(), body("customer-expired-payment", "10", DUE_DATE));
        assertThat(issued.statusCode()).isEqualTo(201);
        String id = field(issued, "id");
        clock.time.set(Instant.parse("2026-01-05T00:00:00Z"));

        assertProblem(
                client.send(
                        request("/" + id + "/payments").POST(HttpRequest.BodyPublishers.noBody()).build(),
                        HttpResponse.BodyHandlers.ofByteArray()),
                409,
                "INVALID_STATE_TRANSITION");

        UUID boletoId = UUID.fromString(id);
        assertThat(
                        jdbc.sql("select status from boleto where id = :id")
                                .param("id", boletoId)
                                .query(String.class)
                                .single())
                .isEqualTo("VENCIDO");
        assertThat(
                        jdbc.sql("select event_type from boleto_audit where boleto_id = :id order by aggregate_version")
                                .param("id", boletoId)
                                .query(String.class)
                                .list())
                .containsExactly("BOLETO_ISSUED", "BOLETO_EXPIRED");
        assertThat(
                        jdbc.sql(
                                        "select event_type from boleto_outbox where aggregate_id = :id order by aggregate_version")
                                .param("id", boletoId)
                                .query(String.class)
                                .list())
                .containsExactly("BOLETO_ISSUED", "BOLETO_EXPIRED");
    }

    @Test
    void shouldCorrelateRequestWhenTheOtlpExporterFails(CapturedOutput output) throws Exception {
        String trace = "12345678901234567890123456789012";
        int previousFailures = identity.failedExports.get();
        var reply =
                client.send(
                        request("").header("traceparent", "00-" + trace + "-1234567890123456-01").GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
        assertThat(reply.statusCode()).isEqualTo(200);
        assertThat(reply.headers().firstValue("X-Trace-Id")).contains(trace);
        assertThat(output.getOut()).contains(trace, "spanId", "Boleto request completed");
        await().atMost(Duration.ofSeconds(12))
                .untilAsserted(() -> assertThat(identity.failedExports.get()).isGreaterThan(previousFailures));
        assertThat(get("").statusCode()).isEqualTo(200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "BPL_IT_JAEGER_QUERY", matches = ".+")
    void shouldExportTracesToJaeger() throws Exception {
        String trace = UUID.randomUUID().toString().replace("-", "");
        identity.forwardTracesTo =
                URI.create(System.getenv().getOrDefault("BPL_IT_JAEGER_OTLP", "http://127.0.0.1:4318/v1/traces"));
        try {
            var response =
                    client.send(
                            request("").header("traceparent", "00-" + trace + "-1234567890123456-01").GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("X-Trace-Id")).contains(trace);
            var lookup =
                    HttpRequest.newBuilder(URI.create(System.getenv("BPL_IT_JAEGER_QUERY") + "/api/traces/" + trace))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build();
            await().atMost(Duration.ofSeconds(20))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        var exported = client.send(lookup, HttpResponse.BodyHandlers.ofString());
                        assertThat(exported.statusCode()).isEqualTo(200);
                        assertThat(exported.body()).contains(trace);
                    });
        } finally {
            identity.forwardTracesTo = null;
        }
    }

    private String field(HttpResponse<byte[]> response, String field) {
        return json.readTree(response.body()).path(field).stringValue();
    }

    private HttpResponse<byte[]> post(String key, String body) throws Exception {
        var request = request("").header("Content-Type", "application/json");
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return client.send(
                request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> get(String suffix) throws Exception {
        return client.send(request(suffix).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest.Builder request(String suffix) {
        return raw(suffix).header("Authorization", "Bearer " + allPermissions);
    }

    private HttpRequest.Builder raw(String suffix) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/boletos" + suffix))
                .timeout(Duration.ofSeconds(6));
    }

    private String key() {
        return UUID.randomUUID().toString();
    }

    private String body(String customer, String amount, LocalDate date) {
        return "{\"customerId\":\"%s\",\"amount\":%s,\"dueDate\":\"%s\"}".formatted(customer, amount, date);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }

        @Bean
        @Primary
        ControlledRenderer testRenderer(PdfBoxRenderer renderer) {
            return new ControlledRenderer(renderer);
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(ISSUE_TIME);

        @Override
        public ZoneId getZone() {
            return ZoneId.of("America/Sao_Paulo");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return time.get();
        }
    }

    static class ControlledRenderer implements PdfRenderer {
        private final PdfBoxRenderer renderer;
        private final AtomicInteger calls = new AtomicInteger();
        private volatile boolean fail;

        ControlledRenderer(PdfBoxRenderer renderer) {
            this.renderer = renderer;
        }

        @Override
        public byte[] render(Snapshot boleto) throws IOException {
            calls.incrementAndGet();
            if (fail) {
                throw new IOException("Synthetic rendering failure");
            }
            return renderer.render(boleto);
        }
    }
}
