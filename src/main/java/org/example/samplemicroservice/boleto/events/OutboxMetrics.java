package org.example.samplemicroservice.boleto.events;

import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
@Profile("observability")
class OutboxMetrics {
    @Bean
    MeterBinder outboxBacklog(JdbcClient jdbc) {
        return registry ->
                registry.gauge(
                        "bpl.outbox.pending",
                        jdbc,
                        client ->
                                client.sql("select count(*) from boleto_outbox where published_at is null")
                                        .query(Long.class)
                                        .single());
    }
}
