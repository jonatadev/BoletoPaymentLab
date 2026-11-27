package org.example.samplemicroservice.boleto.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class BoletoTelemetry {
    private final ObservationRegistry observations;
    private final MeterRegistry meters;

    public BoletoTelemetry(ObservationRegistry observations, MeterRegistry meters) {
        this.observations = observations;
        this.meters = meters;
    }

    public <T, E extends Exception> T observe(String operation, Operation<T, E> action) throws E {
        return observe(operation, operation, action);
    }

    public <T, E extends Exception> T observe(String name, String operation, Operation<T, E> action) throws E {
        var observation =
                Observation.createNotStarted("bpl." + name, observations)
                        .lowCardinalityKeyValue("operation", operation)
                        .start();
        String outcome = "failure";
        try (var scope = observation.openScope()) {
            T result = action.run();
            outcome = "success";
            return result;
        } finally {
            observation.lowCardinalityKeyValue("outcome", outcome);
            observation.stop();
        }
    }

    public void count(String metric) {
        meters.counter("bpl." + metric).increment();
    }

    public void countAfterCommit(String metric) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        count(metric);
                    }
                });
    }

    @FunctionalInterface
    public interface Operation<T, E extends Exception> {
        T run() throws E;
    }
}
