package org.example.samplemicroservice.boleto.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

@Component
public class BoletoTelemetry {
    private final ObservationRegistry observations;
    private final MeterRegistry meters;

    public BoletoTelemetry(ObservationRegistry observations, MeterRegistry meters) {
        this.observations = observations;
        this.meters = meters;
    }

    public <T, E extends Exception> T observe(String operation, Operation<T, E> action) throws E {
        var observation = Observation.start("bpl." + operation, observations);
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

    @FunctionalInterface
    public interface Operation<T, E extends Exception> {
        T run() throws E;
    }
}
