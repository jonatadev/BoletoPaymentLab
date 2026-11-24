package org.example.samplemicroservice.boleto.events;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.SslAuthenticationException;
import org.example.samplemicroservice.boleto.application.BoletoTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class KafkaOutboxTransport implements OutboxTransport {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;
    private final BoletoTelemetry telemetry;
    private final String topic;

    KafkaOutboxTransport(
            KafkaTemplate<String, String> kafka,
            ObjectMapper json,
            ObjectProvider<Tracer> tracer,
            ObjectProvider<Propagator> propagator,
            BoletoTelemetry telemetry,
            @Value("${bpl.events.topic}") String topic) {
        this.kafka = kafka;
        this.json = json;
        this.tracer = tracer;
        this.propagator = propagator;
        this.telemetry = telemetry;
        this.topic = topic;
    }

    @Override
    public void publish(BoletoEvent event, String traceparent) throws PublishFailure {
        var record = new ProducerRecord<>(topic, event.aggregateId().toString(), json.writeValueAsString(event));
        var activeTracer = tracer.getIfAvailable();
        var contextPropagation = propagator.getIfAvailable();
        if (activeTracer == null || contextPropagation == null) {
            send(record);
            return;
        }
        var source = traceparent == null ? Map.<String, String>of() : Map.of("traceparent", traceparent);
        var span = contextPropagation.extract(source, Map::get).name("bpl.outbox.publish").start();
        try (var scope = activeTracer.withSpan(span)) {
            contextPropagation.inject(
                    span.context(),
                    record,
                    (message, key, value) -> message.headers().add(key, value.getBytes(StandardCharsets.US_ASCII)));
            send(record);
        } catch (PublishFailure exception) {
            span.tag("outcome", "failure");
            throw exception;
        } finally {
            span.end();
        }
    }

    private void send(ProducerRecord<String, String> record) throws PublishFailure {
        try {
            kafka.send(record).get(6, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PublishFailure();
        } catch (ExecutionException | TimeoutException | KafkaException exception) {
            countSecurityFailure(exception);
            throw new PublishFailure();
        }
    }

    private void countSecurityFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SslAuthenticationException) {
                telemetry.count("kafka.publish.tls.failures");
                return;
            }
            if (cause instanceof AuthenticationException) {
                telemetry.count("kafka.publish.authentication.failures");
                return;
            }
            if (cause instanceof AuthorizationException) {
                telemetry.count("kafka.publish.authorization.failures");
                return;
            }
        }
    }
}
