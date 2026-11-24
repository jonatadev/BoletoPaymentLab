package org.example.samplemicroservice.boleto.events;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.example.samplemicroservice.boleto.infra.Boleto;
import org.example.samplemicroservice.boleto.infra.BoletoRevisionListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class BoletoJournal {
    private final JdbcClient jdbc;
    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;

    BoletoJournal(JdbcClient jdbc, ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.jdbc = jdbc;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public void record(Boleto boleto, BoletoStatus previous, String source, Instant time) {
        UUID eventId = UUID.randomUUID();
        String eventType = BoletoEventType.fromStatus(boleto.status()).wireValue();
        var activeTracer = tracer.getIfAvailable();
        var span = activeTracer == null ? null : activeTracer.currentSpan();
        String correlation =
                BoletoRevisionListener.correlate(span == null ? eventId.toString() : span.context().traceId());
        var headers = new HashMap<String, String>();
        var contextPropagation = propagator.getIfAvailable();
        if (span != null && contextPropagation != null) {
            contextPropagation.inject(span.context(), headers, HashMap::put);
        }
        var at = time.atOffset(ZoneOffset.UTC);
        jdbc.sql(
                        """
                        insert into boleto_audit
                            (event_id, boleto_id, event_type, occurred_at, source, previous_status, new_status,
                             aggregate_version, correlation_id)
                        values (:event, :boleto, :type, :at, :source, :previous, :next, :version, :correlation)
                        """)
                .param("event", eventId)
                .param("boleto", boleto.id())
                .param("type", eventType)
                .param("at", at)
                .param("source", source)
                .param("previous", previous == null ? null : previous.name(), Types.VARCHAR)
                .param("next", boleto.status().name())
                .param("version", boleto.version())
                .param("correlation", correlation)
                .update();
        jdbc.sql(
                        """
                        insert into boleto_outbox
                            (event_id, aggregate_id, event_type, occurred_at, aggregate_version, correlation_id,
                             traceparent, next_attempt_at)
                        values (:event, :boleto, :type, :at, :version, :correlation, :trace, :at)
                        """)
                .param("event", eventId)
                .param("boleto", boleto.id())
                .param("type", eventType)
                .param("at", at)
                .param("version", boleto.version())
                .param("correlation", correlation)
                .param("trace", headers.get("traceparent"), Types.VARCHAR)
                .update();
    }

    public List<AuditEntry> history(UUID boletoId) {
        return jdbc.sql(
                        """
                        select event_id, event_type, occurred_at, source, previous_status, new_status, correlation_id
                        from boleto_audit where boleto_id = :id order by aggregate_version
                        """)
                .param("id", boletoId)
                .query((row, index) ->
                        new AuditEntry(
                                row.getObject("event_id", UUID.class),
                                row.getString("event_type"),
                                row.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                                row.getString("source"),
                                row.getString("previous_status"),
                                row.getString("new_status"),
                                row.getString("correlation_id")))
                .list();
    }

    public record AuditEntry(
            UUID eventId,
            String eventType,
            Instant timestamp,
            String source,
            String previousStatus,
            String newStatus,
            String correlationId) {}
}
