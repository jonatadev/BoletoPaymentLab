package org.example.samplemicroservice.boleto.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public record IssuanceInput(String customerId, BigDecimal amount, LocalDate dueDate) {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999.99");

    public IssuanceInput {
        if (customerId == null
                || !customerId.matches("[A-Za-z0-9._-]{1,120}")
                || dueDate == null
                || amount == null
                || amount.signum() <= 0
                || amount.compareTo(MAX_AMOUNT) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw BoletoFailure.INVALID_REQUEST.exception();
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
