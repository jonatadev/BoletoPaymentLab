package org.example.samplemicroservice.boleto.events;

import java.time.Instant;
import java.util.UUID;

public record BoletoEvent(
        UUID eventId,
        String eventType,
        UUID aggregateId,
        Instant timestamp,
        int version,
        long aggregateVersion,
        String correlationId) {}
