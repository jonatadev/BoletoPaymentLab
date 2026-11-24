package org.example.samplemicroservice.analytics;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class McpContractTests {
    private static final String ISSUER = "https://issuer.example.test";
    private static final String AUDIENCE = "boleto-analytics-mcp";
    private static final String RESOURCE = "http://127.0.0.1:8090";
    private static final RSAKey KEY = key();
    private static final HttpServer JWKS = jwks();
    private static final String INITIALIZE =
            """
            {"jsonrpc":"2.0","id":1,"method":"initialize",
             "params":{"protocolVersion":"2025-03-26","capabilities":{},
             "clientInfo":{"name":"analytics-test","version":"1.0"}}}
            """;

    @Autowired private MockMvc mvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AnalyticsPostgresFixture.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> AnalyticsPostgresFixture.READER);
        registry.add("spring.datasource.password", () -> AnalyticsPostgresFixture.READER_PASSWORD);
        registry.add("mcp.analytics.auth-issuer", () -> ISSUER);
        registry.add("mcp.analytics.auth-jwks", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
        registry.add("mcp.analytics.auth-audience", () -> AUDIENCE);
        registry.add("mcp.oauth.enabled", () -> true);
        registry.add("mcp.oauth.resource", () -> RESOURCE);
        registry.add("mcp.oauth.tunnel-discovery", () -> true);
    }

    @Test
    void shouldRejectAnonymousMcpRequests() throws Exception {
        mvc.perform(post("/mcp").contentType("application/json").content(INITIALIZE))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        header().string(
                                        "WWW-Authenticate",
                                        Matchers.containsString(RESOURCE + "/.well-known/oauth-protected-resource")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp"})
    void shouldPublishTheSameTunnelDiscoveryMetadata(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value(RESOURCE))
                .andExpect(jsonPath("$.authorization_servers[0]").value(ISSUER))
                .andExpect(jsonPath("$.scopes_supported[0]").value("analytics:read"));
    }

    @Test
    void shouldRejectTokensWithoutTheAnalyticsScope() throws Exception {
        mvc.perform(
                        post("/mcp")
                                .header(
                                        "Authorization",
                                        bearer(ISSUER, AUDIENCE, "boletos:read", Instant.now().plusSeconds(300), KEY))
                                .contentType("application/json")
                                .content(INITIALIZE))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAcceptTheAnalyticsScopeForMcpInitialization() throws Exception {
        mvc.perform(
                        post("/mcp")
                                .header("Authorization", validBearer())
                                .contentType("application/json")
                                .accept("application/json, text/event-stream")
                                .content(INITIALIZE))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDiscoverOnlyTheReadOnlyAnalyticsTools() throws Exception {
        var authority = validBearer();
        var initialization =
                mvc.perform(
                                post("/mcp")
                                        .header("Authorization", authority)
                                        .contentType("application/json")
                                        .accept("application/json, text/event-stream")
                                        .content(INITIALIZE))
                        .andExpect(status().isOk())
                        .andReturn();
        var sessionId = initialization.getResponse().getHeader("Mcp-Session-Id");

        var tools =
                mvc.perform(
                                post("/mcp")
                                        .header("Authorization", authority)
                                        .header("Mcp-Session-Id", sessionId)
                                        .contentType("application/json")
                                        .accept("application/json, text/event-stream")
                                        .content(
                                                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var names = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(tools);
        Assertions.assertThat(names.results().map(match -> match.group(1)).toList())
                .containsExactlyInAnyOrder(
                        "get_boleto_summary",
                        "get_boletos_by_day",
                        "get_boletos_by_status",
                        "get_processing_metrics",
                        "get_outbox_metrics",
                        "get_database_health");

        mvc.perform(
                        post("/mcp")
                                .header("Authorization", authority)
                                .header("Mcp-Session-Id", sessionId)
                                .contentType("application/json")
                                .accept("application/json, text/event-stream")
                                .content(
                                        """
                                        {"jsonrpc":"2.0","id":3,"method":"tools/call",
                                         "params":{"name":"get_database_health","arguments":{}}}
                                        """))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("available")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "expiration", "signature"})
    void shouldRejectInvalidTokens(String invalidClaim) throws Exception {
        var token =
                bearer(
                        invalidClaim.equals("issuer") ? "https://wrong.example.test" : ISSUER,
                        invalidClaim.equals("audience") ? "wrong" : AUDIENCE,
                        "analytics:read",
                        Instant.now().plusSeconds(invalidClaim.equals("expiration") ? -300 : 300),
                        invalidClaim.equals("signature") ? key() : KEY);
        mvc.perform(post("/mcp").header("Authorization", token).contentType("application/json").content(INITIALIZE))
                .andExpect(status().isUnauthorized());
    }

    private static String validBearer() throws JOSEException {
        return bearer(ISSUER, AUDIENCE, "analytics:read", Instant.now().plusSeconds(300), KEY);
    }

    private static String bearer(String issuer, String audience, String scope, Instant expires, RSAKey key)
            throws JOSEException {
        var jwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256)
                                .type(JOSEObjectType.JWT)
                                .keyID(KEY.getKeyID())
                                .build(),
                        new JWTClaimsSet.Builder()
                                .subject("synthetic-user")
                                .issuer(issuer)
                                .audience(audience)
                                .claim("scope", scope)
                                .expirationTime(Date.from(expires))
                                .build());
        jwt.sign(new RSASSASigner(key));
        return "Bearer " + jwt.serialize();
    }

    private static RSAKey key() {
        try {
            return new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (JOSEException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static HttpServer jwks() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var body = ("{\"keys\":[" + KEY.toPublicJWK().toJSONString() + "]}").getBytes(StandardCharsets.UTF_8);
            server.createContext(
                    "/jwks",
                    exchange -> {
                        try (exchange) {
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                            exchange.sendResponseHeaders(200, body.length);
                            exchange.getResponseBody().write(body);
                        }
                    });
            server.start();
            return server;
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @AfterAll
    static void stopJwks() {
        JWKS.stop(0);
    }
}
