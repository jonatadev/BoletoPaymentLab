package org.example.samplemicroservice.boleto.events;

import org.example.samplemicroservice.boleto.application.BoletoLifecycle;
import org.example.samplemicroservice.boleto.application.BoletoTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.event.ConsumerStoppedEvent;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class BoletoJobs {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoletoJobs.class);
    private final BoletoLifecycle lifecycle;
    private final OutboxRelay outbox;
    private final BoletoTelemetry telemetry;
    private final boolean expiryEnabled;
    private final boolean eventsEnabled;

    BoletoJobs(BoletoLifecycle lifecycle, OutboxRelay outbox, BoletoTelemetry telemetry,
            @Value("${bpl.expiry.enabled}") boolean expiryEnabled,
            @Value("${bpl.events.enabled}") boolean eventsEnabled) {
        this.lifecycle = lifecycle;
        this.outbox = outbox;
        this.telemetry = telemetry;
        this.expiryEnabled = expiryEnabled;
        this.eventsEnabled = eventsEnabled;
    }

    @EventListener
    void consumerStopped(ConsumerStoppedEvent event) {
        if (event.getReason() == ConsumerStoppedEvent.Reason.AUTH) {
            telemetry.count("kafka.consumer.security.stops");
        }
    }

    @Scheduled(fixedDelayString = "${bpl.expiry.delay}", initialDelayString = "${bpl.expiry.delay}", scheduler = "taskScheduler")
    void expire() {
        if (expiryEnabled) {
            for (var id : lifecycle.dueForExpiry()) {
                try {
                    lifecycle.expire(id);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Expiry reconciliation deferred");
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${bpl.events.poll-delay}", initialDelayString = "${bpl.events.poll-delay}",
            scheduler = "taskScheduler")
    void publish() {
        if (eventsEnabled) {
            try {
                for (int i = 0; i < 4 && outbox.processOne(); i++) {
                    // Bound each polling turn.
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Outbox polling deferred");
            }
        }
    }

    @Bean
    DefaultErrorHandler boletoConsumerErrors() {
        var backoff = new ExponentialBackOff(1000, 2.0);
        backoff.setMaxInterval(30_000);
        var handler = new DefaultErrorHandler((record, exception) -> LOGGER.warn("Unsupported boleto event rejected"),
                backoff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
