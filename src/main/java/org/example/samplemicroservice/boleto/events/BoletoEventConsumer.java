package org.example.samplemicroservice.boleto.events;

import java.time.Clock;
import java.time.ZoneOffset;
import org.example.samplemicroservice.boleto.application.BoletoTelemetry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
class BoletoEventConsumer {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final BoletoTelemetry telemetry;

    BoletoEventConsumer(JdbcClient jdbc, ObjectMapper json, Clock clock, BoletoTelemetry telemetry) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.telemetry = telemetry;
    }

    @KafkaListener(topics = "${bpl.events.topic}", autoStartup = "${bpl.events.enabled}")
    @Transactional(timeout = 7)
    public void receive(String message) {
        BoletoEvent event;
        try {
            event = json.readValue(message, BoletoEvent.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Malformed boleto event");
        }
        if (event == null || event.eventId() == null || event.aggregateId() == null
                || event.timestamp() == null || event.version() != 1 || event.aggregateVersion() < 0
                || !BoletoEventType.supports(event.eventType())
                || event.correlationId() == null || event.correlationId().isBlank()) {
            throw new IllegalArgumentException("Unsupported boleto event");
        }
        int inserted = jdbc.sql("""
                insert into processed_boleto_event (event_id, aggregate_id, event_type, processed_at)
                values (:event, :aggregate, :type, :now) on conflict (event_id) do nothing
                """).param("event", event.eventId()).param("aggregate", event.aggregateId())
                .param("type", event.eventType()).param("now", clock.instant().atOffset(ZoneOffset.UTC)).update();
        if (inserted == 0) {
            telemetry.count("events.duplicates");
        }
    }
}
