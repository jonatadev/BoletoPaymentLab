package org.example.samplemicroservice.boleto.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.example.samplemicroservice.boleto.infra.Boleto;

public record Snapshot(
        UUID id,
        String customerId,
        BigDecimal amount,
        String currency,
        LocalDate dueDate,
        BoletoStatus status,
        Instant issuedAt) {
    static Snapshot from(Boleto boleto, LocalDate today) {
        return new Snapshot(
                boleto.id(),
                boleto.customerId(),
                boleto.amount(),
                "BRL",
                boleto.dueDate(),
                boleto.status().effective(boleto.dueDate(), today),
                boleto.issuedAt());
    }
}
