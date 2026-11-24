package org.example.samplemicroservice.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class BoletoSecurity {
    private static final String BOLETOS = "/api/v1/boletos";

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http, ObjectMapper json, @Value("${springdoc.api-docs.enabled:false}") boolean openApiEnabled)
            throws Exception {
        AuthenticationEntryPoint unauthorized =
                (request, response, exception) ->
                        problem(response, json, 401, "Unauthorized", "UNAUTHORIZED", "Bearer");
        // The resource server exempts Bearer requests from CSRF checks before validating the JWT.
        return http.csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(access -> {
                    if (openApiEnabled) {
                        access.requestMatchers(
                                        HttpMethod.GET,
                                        "/v3/api-docs",
                                        "/v3/api-docs.yaml",
                                        "/v3/api-docs/swagger-config",
                                        "/swagger-ui.html",
                                        "/swagger-ui/**")
                                .permitAll();
                    }
                    access.dispatcherTypeMatchers(DispatcherType.ERROR)
                            .permitAll()
                            .requestMatchers(
                                    HttpMethod.GET,
                                    "/actuator/health",
                                    "/actuator/health/liveness",
                                    "/actuator/health/readiness")
                            .permitAll()
                            .requestMatchers(HttpMethod.GET, "/actuator/metrics", "/actuator/metrics/*")
                            .hasAuthority("SCOPE_ops:metrics")
                            .requestMatchers(HttpMethod.POST, BOLETOS)
                            .hasAuthority("SCOPE_boletos:issue")
                            .requestMatchers(HttpMethod.GET, BOLETOS)
                            .hasAuthority("SCOPE_boletos:list")
                            .requestMatchers(
                                    HttpMethod.GET,
                                    BOLETOS + "/*/history",
                                    BOLETOS + "/*/revisions",
                                    BOLETOS + "/*/revisions/*")
                            .hasAuthority("SCOPE_boletos:audit")
                            .requestMatchers(HttpMethod.GET, BOLETOS + "/*", BOLETOS + "/*/pdf")
                            .hasAuthority("SCOPE_boletos:read")
                            .requestMatchers(HttpMethod.POST, BOLETOS + "/*/payments")
                            .hasAuthority("SCOPE_boletos:pay")
                            .requestMatchers(HttpMethod.POST, BOLETOS + "/*/cancellations")
                            .hasAuthority("SCOPE_boletos:cancel")
                            .anyRequest()
                            .denyAll();
                })
                .exceptionHandling(errors ->
                        errors.authenticationEntryPoint(unauthorized)
                                .accessDeniedHandler((request, response, exception) -> {
                                    if (exception instanceof CsrfException) {
                                        unauthorized.commence(
                                                request,
                                                response,
                                                new InsufficientAuthenticationException("Bearer token required"));
                                        return;
                                    }
                                    problem(
                                            response,
                                            json,
                                            403,
                                            "Forbidden",
                                            "FORBIDDEN",
                                            "Bearer error=\"insufficient_scope\"");
                                }))
                .oauth2ResourceServer(
                        server -> server.authenticationEntryPoint(unauthorized).jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    @SuppressWarnings("removal") // Spring Security's JWKS builder currently accepts RestOperations.
    JwtDecoder jwtDecoder(
            @Value("${bpl.security.issuer}") String issuer,
            @Value("${bpl.security.jwks}") String jwks,
            @Value("${bpl.security.audience}") String audience) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(2));
        requests.setReadTimeout(Duration.ofSeconds(3));
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).restOperations(new RestTemplate(requests)).build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        jwt ->
                                jwt.getExpiresAt() != null
                                                && jwt.getAudience() != null
                                                && jwt.getAudience().contains(audience)
                                        ? OAuth2TokenValidatorResult.success()
                                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        return decoder;
    }

    private static void problem(
            HttpServletResponse response, ObjectMapper json, int status, String title, String code, String challenge)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("WWW-Authenticate", challenge);
        json.writeValue(
                response.getOutputStream(),
                Map.of("type", "about:blank", "status", status, "title", title, "code", code));
    }
}
