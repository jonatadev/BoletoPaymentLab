package org.example.samplemicroservice.boleto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.example.samplemicroservice.boleto.application.BoletoRevisions;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.example.samplemicroservice.boleto.infra.Boleto;
import org.example.samplemicroservice.boleto.infra.BoletoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "bpl.expiry.enabled=false")
class BoletoRevisionsTests {
    @Autowired private BoletoRepository boletos;
    @Autowired private BoletoRevisions revisions;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void shouldAuditOnlyCommittedWritesAndRejectStaleUpdates() {
        UUID id = UUID.randomUUID();
        boletos.saveAndFlush(
                new Boleto(
                        id,
                        "synthetic-customer",
                        BigDecimal.TEN,
                        LocalDate.of(2099, 1, 1),
                        Instant.parse("2026-01-01T00:00:00Z")));
        Boleto stale = boletos.findById(id).orElseThrow();

        var initial = revisions.list(id, null, 20).getFirst();
        var original = revisions.detail(id, initial.revision()).state();
        assertThat(original.createdAt()).isNotNull().isEqualTo(original.updatedAt());
        assertThat(original.createdBy()).isEqualTo("system:boleto").isEqualTo(original.updatedBy());
        assertThat(initial.actor()).isEqualTo("system:boleto");
        assertThat(initial.timestamp()).isNotNull();
        assertThat(UUID.fromString(initial.correlationId())).isNotNull();

        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            boletos.findById(id).orElseThrow().transitionTo(BoletoStatus.CANCELADO);
            boletos.flush();
            status.setRollbackOnly();
        });
        assertThat(revisions.list(id, null, 20)).containsExactly(initial);

        transaction.executeWithoutResult(status -> boletos.findById(id).orElseThrow().transitionTo(BoletoStatus.PAGO));
        var history = revisions.list(id, null, 20);
        assertThat(history).hasSize(2);
        var paid = revisions.detail(id, history.getFirst().revision()).state();
        assertThat(paid.status()).isEqualTo(BoletoStatus.PAGO);
        assertThat(paid.version()).isEqualTo(original.version() + 1);
        assertThat(paid.createdAt()).isEqualTo(original.createdAt());
        assertThat(paid.updatedAt()).isAfterOrEqualTo(original.updatedAt());
        assertThat(original.status()).isEqualTo(BoletoStatus.EMITIDO);

        stale.transitionTo(BoletoStatus.CANCELADO);
        assertThatThrownBy(() -> boletos.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(revisions.list(id, null, 20)).isEqualTo(history);
    }
}
