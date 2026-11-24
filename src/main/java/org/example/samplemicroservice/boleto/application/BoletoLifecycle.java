package org.example.samplemicroservice.boleto.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.example.samplemicroservice.boleto.events.BoletoJournal;
import org.example.samplemicroservice.boleto.events.BoletoJournal.AuditEntry;
import org.example.samplemicroservice.boleto.infra.Boleto;
import org.example.samplemicroservice.boleto.infra.BoletoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BoletoLifecycle {
    private final BoletoRepository boletos;
    private final BoletoJournal journal;
    private final Clock clock;
    private final BoletoTelemetry telemetry;
    private final TransactionTemplate transactions;

    BoletoLifecycle(
            BoletoRepository boletos,
            BoletoJournal journal,
            Clock clock,
            BoletoTelemetry telemetry,
            PlatformTransactionManager transactionManager) {
        this.boletos = boletos;
        this.journal = journal;
        this.clock = clock;
        this.telemetry = telemetry;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setTimeout(7);
    }

    public Snapshot detail(UUID id) {
        Boleto current = boletos.findById(id).orElseThrow(BoletoFailure.BOLETO_NOT_FOUND::exception);
        LocalDate today = LocalDate.now(clock);
        if (current.status() != current.status().effective(current.dueDate(), today)) {
            return expire(id);
        }
        return Snapshot.from(current, today);
    }

    public List<AuditEntry> history(UUID id) {
        detail(id);
        return journal.history(id);
    }

    public Snapshot change(UUID id, BoletoStatus target) {
        return telemetry.observe(
                "lifecycle.change",
                () -> {
                    Optional<Snapshot> changed =
                            transactions.execute(transaction -> {
                                Boleto boleto = locked(id);
                                LocalDate today = LocalDate.now(clock);
                                materializeExpiry(boleto, today);
                                if (boleto.status() != BoletoStatus.EMITIDO) {
                                    return Optional.empty();
                                }
                                recordChange(boleto, target, "API");
                                return Optional.of(Snapshot.from(boleto, today));
                            });
                    return changed.orElseThrow(BoletoFailure.INVALID_STATE_TRANSITION::exception);
                });
    }

    public Snapshot expire(UUID id) {
        return transactions.execute(transaction -> {
            Boleto boleto = locked(id);
            LocalDate today = LocalDate.now(clock);
            materializeExpiry(boleto, today);
            return Snapshot.from(boleto, today);
        });
    }

    public List<UUID> dueForExpiry() {
        return boletos.findDueIds(BoletoStatus.EMITIDO, LocalDate.now(clock), PageRequest.of(0, 50));
    }

    private Boleto locked(UUID id) {
        return boletos.findForUpdate(id).orElseThrow(BoletoFailure.BOLETO_NOT_FOUND::exception);
    }

    private void materializeExpiry(Boleto boleto, LocalDate today) {
        if (boleto.status() != boleto.status().effective(boleto.dueDate(), today)) {
            telemetry.observe(
                    "lifecycle.expire",
                    () -> {
                        recordChange(boleto, BoletoStatus.VENCIDO, "SYSTEM");
                        return null;
                    });
        }
    }

    private void recordChange(Boleto boleto, BoletoStatus target, String source) {
        BoletoStatus previous = boleto.status();
        boleto.transitionTo(target);
        boletos.flush();
        journal.record(boleto, previous, source, clock.instant());
    }
}
