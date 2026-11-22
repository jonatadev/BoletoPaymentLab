package org.example.samplemicroservice.boleto.domain;

import java.time.LocalDate;

public enum BoletoStatus {
    EMITIDO, PAGO, VENCIDO, CANCELADO;

    public BoletoStatus effective(LocalDate dueDate, LocalDate today) {
        return this == EMITIDO && dueDate.isBefore(today) ? VENCIDO : this;
    }
}
