package org.example.samplemicroservice.boleto.infra;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "boletoAuditor", dateTimeProviderRef = "boletoAuditTime")
class BoletoAuditing {
    @Bean
    AuditorAware<String> boletoAuditor() {
        return () -> Optional.of(actor());
    }

    @Bean
    DateTimeProvider boletoAuditTime(Clock clock) {
        return () -> Optional.of(clock.instant());
    }

    static String actor() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && jwt.isAuthenticated()) {
            String subject = jwt.getToken().getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }
        return "system:boleto";
    }
}
