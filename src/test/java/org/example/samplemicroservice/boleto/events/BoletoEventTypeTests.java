package org.example.samplemicroservice.boleto.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BoletoEventTypeTests {
    @ParameterizedTest
    @CsvSource({"EMITIDO, BOLETO_ISSUED", "PAGO, BOLETO_PAID", "VENCIDO, BOLETO_EXPIRED", "CANCELADO, BOLETO_CANCELED"})
    void shouldKeepThePublishedAndAcceptedEventName(BoletoStatus status, String wireValue) {
        assertThat(BoletoEventType.fromStatus(status).wireValue()).isEqualTo(wireValue);
        assertThat(BoletoEventType.supports(wireValue)).isTrue();
    }

    @Test
    void shouldRejectMissingAndUnknownEventNames() {
        assertThat(BoletoEventType.supports(null)).isFalse();
        assertThat(BoletoEventType.supports("BOLETO_UNKNOWN")).isFalse();
    }
}
