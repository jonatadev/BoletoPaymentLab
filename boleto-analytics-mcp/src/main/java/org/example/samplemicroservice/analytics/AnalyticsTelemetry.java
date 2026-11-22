package org.example.samplemicroservice.analytics;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
class AnalyticsTelemetry {
    private final MeterRegistry meterRegistry;

    AnalyticsTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    <T> T observe(String tool, Supplier<T> action) {
        var timer = meterRegistry.timer("bpl.mcp.analytics.tool.duration", "tool", tool);
        try {
            T result = timer.record(action);
            meterRegistry.counter("bpl.mcp.analytics.tool.calls", "tool", tool).increment();
            return result;
        } catch (RuntimeException exception) {
            meterRegistry.counter("bpl.mcp.analytics.tool.errors", "tool", tool).increment();
            throw exception;
        }
    }
}
