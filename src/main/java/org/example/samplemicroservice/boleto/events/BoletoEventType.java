package org.example.samplemicroservice.boleto.events;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;

enum BoletoEventType {
    BOLETO_ISSUED,
    BOLETO_PAID,
    BOLETO_EXPIRED,
    BOLETO_CANCELED;

    private static final Set<String> WIRE_VALUES = Arrays.stream(values())
            .map(BoletoEventType::wireValue)
            .collect(Collectors.toUnmodifiableSet());

    static BoletoEventType fromStatus(BoletoStatus status) {
        return switch (status) {
            case EMITIDO -> BOLETO_ISSUED;
            case PAGO -> BOLETO_PAID;
            case VENCIDO -> BOLETO_EXPIRED;
            case CANCELADO -> BOLETO_CANCELED;
        };
    }

    static boolean supports(String value) {
        return value != null && WIRE_VALUES.contains(value);
    }

    // These names are persisted and published by the v1 event contract.
    String wireValue() {
        return name();
    }
}
