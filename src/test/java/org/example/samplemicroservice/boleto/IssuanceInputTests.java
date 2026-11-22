package org.example.samplemicroservice.boleto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.example.samplemicroservice.boleto.application.BoletoFailure.FailureException;
import org.example.samplemicroservice.boleto.application.IssuanceInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IssuanceInputTests {
    private final LocalDate dueDate = LocalDate.of(2099, 1, 2);

    @Test
    void shouldNormalizeEquivalentAmountsWithoutRounding() {
        var whole = new IssuanceInput("synthetic", new BigDecimal("10"), dueDate);
        var decimal = new IssuanceInput("synthetic", new BigDecimal("10.00"), dueDate);
        assertThat(whole).isEqualTo(decimal);
        assertThat(whole.amount().scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "999999.991", "1000000"})
    void shouldRejectAmountsOutsideBoundsOrRequiringRounding(String amount) {
        assertThatThrownBy(() -> new IssuanceInput("synthetic", new BigDecimal(amount), dueDate))
                .isInstanceOf(FailureException.class);
    }
}
