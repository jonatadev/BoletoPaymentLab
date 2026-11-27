package org.example.samplemicroservice.boleto.events;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.example.samplemicroservice.boleto.application.BoletoTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class OutboxRelay {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);
    private final JdbcClient jdbc;
    private final OutboxTransport transport;
    private final Clock clock;
    private final BoletoTelemetry telemetry;
    private final TransactionTemplate transactions;

    OutboxRelay(
            JdbcClient jdbc,
            OutboxTransport transport,
            Clock clock,
            BoletoTelemetry telemetry,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transport = transport;
        this.clock = clock;
        this.telemetry = telemetry;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setTimeout(12);
    }

    boolean processOne() {
        var outcome = transactions.execute(status -> sendNext());
        return outcome == Outcome.PUBLISHED;
    }

    private Outcome sendNext() {
        var next =
                jdbc.sql(
                                """
                                select e.event_id, e.aggregate_id, e.event_type, e.occurred_at, e.aggregate_version,
                                       e.correlation_id, e.traceparent, e.attempts
                                from boleto_outbox e
                                where e.published_at is null and e.next_attempt_at <= :now
                                  and not exists (
                                      select 1 from boleto_outbox earlier where earlier.aggregate_id = e.aggregate_id
                                        and earlier.aggregate_version < e.aggregate_version and earlier.published_at is null
                                  )
                                order by e.next_attempt_at, e.event_id
                                limit 1 for update of e skip locked
                                """)
                        .param("now", now())
                        .query((row, index) ->
                                new Pending(
                                        new BoletoEvent(
                                                row.getObject("event_id", UUID.class),
                                                row.getString("event_type"),
                                                row.getObject("aggregate_id", UUID.class),
                                                row.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                                                1,
                                                row.getLong("aggregate_version"),
                                                row.getString("correlation_id")),
                                        row.getString("traceparent"),
                                        row.getInt("attempts")))
                        .optional();
        if (next.isEmpty()) {
            return Outcome.EMPTY;
        }

        var pending = next.get();
        try {
            transport.publish(pending.event(), pending.traceparent());
        } catch (OutboxTransport.PublishFailure exception) {
            int attempts = Math.min(pending.attempts(), Integer.MAX_VALUE - 1) + 1;
            long cap = Math.min(60_000L, 1_000L << Math.min(attempts - 1, 6));
            long delay = ThreadLocalRandom.current().nextLong(cap / 2, cap + 1);
            jdbc.sql(
                            """
                            update boleto_outbox set attempts = :attempts, next_attempt_at = :next where event_id = :id
                            """)
                    .param("attempts", attempts)
                    .param("next", now().plusNanos(delay * 1_000_000L))
                    .param("id", pending.event().eventId())
                    .update();
            LOGGER.warn("Outbox publication deferred");
            telemetry.countAfterCommit("outbox.errors");
            return Outcome.DEFERRED;
        }

        jdbc.sql("update boleto_outbox set published_at = :now where event_id = :id")
                .param("now", now())
                .param("id", pending.event().eventId())
                .update();
        telemetry.countAfterCommit("outbox.published");
        return Outcome.PUBLISHED;
    }

    private OffsetDateTime now() {
        return clock.instant().atOffset(ZoneOffset.UTC);
    }

    private record Pending(BoletoEvent event, String traceparent, int attempts) {}

    private enum Outcome {
        EMPTY,
        PUBLISHED,
        DEFERRED
    }
}
