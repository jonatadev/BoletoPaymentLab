package org.example.samplemicroservice.boleto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.example.samplemicroservice.boleto.infra.Boleto;
import org.junit.jupiter.api.Test;

class BoletoStatusTests {
    private final LocalDate due = LocalDate.of(2099, 1, 10);

    @Test
    void shouldRemainIssuedOnTheDueDateAndExpireTheFollowingDay() {
        assertThat(BoletoStatus.EMITIDO.effective(due, due)).isEqualTo(BoletoStatus.EMITIDO);
        assertThat(BoletoStatus.EMITIDO.effective(due, due.plusDays(1))).isEqualTo(BoletoStatus.VENCIDO);
        assertThat(BoletoStatus.PAGO.effective(due, due.plusDays(1))).isEqualTo(BoletoStatus.PAGO);
        assertThat(BoletoStatus.CANCELADO.effective(due, due.plusDays(1))).isEqualTo(BoletoStatus.CANCELADO);
    }

    @Test
    void shouldRejectFurtherChangesToATerminalState() {
        var boleto = new Boleto(UUID.randomUUID(), "synthetic", BigDecimal.TEN, due, Instant.EPOCH);
        boleto.transitionTo(BoletoStatus.PAGO);
        assertThatThrownBy(() -> boleto.transitionTo(BoletoStatus.CANCELADO))
                .isInstanceOf(IllegalStateException.class);
        assertThat(boleto.status()).isEqualTo(BoletoStatus.PAGO);
        assertThat(boleto.dueDate()).isEqualTo(due);
    }
}
