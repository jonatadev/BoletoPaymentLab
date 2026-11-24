package org.example.samplemicroservice.boleto;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

final class IntegrationIdentity implements AutoCloseable {
    static final String ISSUER = "http://issuer.example.test";
    private final HttpServer server;
    private final RSAKey key;
    final AtomicInteger failedExports = new AtomicInteger();
    volatile URI forwardTracesTo;

    IntegrationIdentity() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("integration").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/jwks",
                exchange -> {
                    byte[] payload = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                    try (exchange) {
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, payload.length);
                        exchange.getResponseBody().write(payload);
                    }
                });
        server.createContext(
                "/v1/traces",
                exchange -> {
                    try (exchange) {
                        URI destination = forwardTracesTo;
                        if (destination != null) {
                            byte[] body = exchange.getRequestBody().readAllBytes();
                            var request =
                                    HttpRequest.newBuilder(destination)
                                            .timeout(Duration.ofSeconds(3))
                                            .header(
                                                    "Content-Type",
                                                    exchange.getRequestHeaders().getFirst("Content-Type"));
                            String encoding = exchange.getRequestHeaders().getFirst("Content-Encoding");
                            if (encoding != null) {
                                request.header("Content-Encoding", encoding);
                            }
                            try {
                                var response =
                                        HttpClient.newHttpClient()
                                                .send(
                                                        request.POST(HttpRequest.BodyPublishers.ofByteArray(body))
                                                                .build(),
                                                        HttpResponse.BodyHandlers.ofByteArray());
                                exchange.getResponseHeaders().set("Content-Type", "application/x-protobuf");
                                exchange.sendResponseHeaders(response.statusCode(), response.body().length);
                                exchange.getResponseBody().write(response.body());
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                exchange.sendResponseHeaders(503, -1);
                            }
                            return;
                        }
                        exchange.getRequestBody().transferTo(java.io.OutputStream.nullOutputStream());
                        failedExports.incrementAndGet();
                        exchange.sendResponseHeaders(503, -1);
                    }
                });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    String token(String scopes, String audience) throws Exception {
        var token =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                        new JWTClaimsSet.Builder()
                                .issuer(ISSUER)
                                .audience(audience)
                                .subject("synthetic-client")
                                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                                .claim("scope", scopes)
                                .build());
        token.sign(new RSASSASigner(key));
        return token.serialize();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
