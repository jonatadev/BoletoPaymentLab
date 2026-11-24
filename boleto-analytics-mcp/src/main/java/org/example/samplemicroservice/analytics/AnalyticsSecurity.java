package org.example.samplemicroservice.analytics;

import jakarta.servlet.DispatcherType;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnalyticsSecurity.OAuthSettings.class)
class AnalyticsSecurity {
    private static final URI TUNNEL_LOCAL_RESOURCE = URI.create("http://127.0.0.1:8090");

    @Bean
    SecurityFilterChain analyticsSecurityFilterChain(HttpSecurity http,
            OAuthSettings oauth, AnalyticsProperties analytics) throws Exception {
        var entryPoint = new BearerTokenAuthenticationEntryPoint();
        if (oauth.enabled()) {
            if (!oauth.tunnelDiscovery() || !TUNNEL_LOCAL_RESOURCE.equals(oauth.resource())) {
                requireHttps(oauth.resource());
            }
            requireHttps(URI.create(analytics.authIssuer()));
            entryPoint.setResourceMetadataParameterResolver(request ->
                    oauth.resource().resolve("/.well-known/oauth-protected-resource").toASCIIString());
        }
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(access -> access
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/mcp", "/mcp/**").hasAuthority("SCOPE_analytics:read")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(server -> {
                    server.jwt(Customizer.withDefaults());
                    if (oauth.enabled()) {
                        server.protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(
                                builder -> builder.resource(oauth.resource().toASCIIString())
                                        .authorizationServers(servers -> {
                                            servers.clear();
                                            servers.add(analytics.authIssuer());
                                        })
                                        .scope("analytics:read")));
                        server.authenticationEntryPoint(entryPoint);
                    }
                })
                .build();
    }

    private static void requireHttps(URI uri) {
        if (uri == null || !"https".equals(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
            throw new IllegalArgumentException("OAuth identifiers must be HTTPS URIs without user info, query or fragment");
        }
    }

    @ConfigurationProperties("mcp.oauth")
    record OAuthSettings(boolean enabled, URI resource, boolean tunnelDiscovery) {
    }

    @Bean
    @SuppressWarnings("removal")
    JwtDecoder jwtDecoder(AnalyticsProperties properties) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(2));
        requests.setReadTimeout(Duration.ofSeconds(3));
        var decoder = NimbusJwtDecoder.withJwkSetUri(properties.authJwks())
                .restOperations(new RestTemplate(requests))
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.authIssuer()),
                jwt -> jwt.getExpiresAt() != null && jwt.getAudience() != null
                                && jwt.getAudience().contains(properties.authAudience())
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        return decoder;
    }
}
