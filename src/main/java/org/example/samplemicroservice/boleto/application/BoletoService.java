package org.example.samplemicroservice.boleto.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.example.samplemicroservice.boleto.events.BoletoJournal;
import org.example.samplemicroservice.boleto.infra.Boleto;
import org.example.samplemicroservice.boleto.infra.BoletoRepository;
import org.example.samplemicroservice.boleto.infra.IdempotentIssuance;
import org.example.samplemicroservice.boleto.infra.IssuanceRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BoletoService {
    private static final String OPERATION = "ISSUE_BOLETO";
    private final BoletoRepository boletos;
    private final IssuanceRepository issuances;
    private final Clock clock;
    private final BoletoJournal journal;
    private final BoletoTelemetry telemetry;
    private final TransactionTemplate issuanceTransaction;
    private final TransactionTemplate recoveryTransaction;

    public BoletoService(
            BoletoRepository boletos,
            IssuanceRepository issuances,
            Clock clock,
            PlatformTransactionManager transactions,
            BoletoJournal journal,
            BoletoTelemetry telemetry) {
        this.boletos = boletos;
        this.issuances = issuances;
        this.clock = clock;
        this.journal = journal;
        this.telemetry = telemetry;
        issuanceTransaction = new TransactionTemplate(transactions);
        issuanceTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        issuanceTransaction.setTimeout(7);
        recoveryTransaction = new TransactionTemplate(transactions);
        recoveryTransaction.setReadOnly(true);
        recoveryTransaction.setTimeout(7);
        recoveryTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public IssueResult issue(String key, IssuanceInput input) {
        if (key == null || !key.matches("[!-~]{1,160}")) {
            throw BoletoFailure.INVALID_REQUEST.exception();
        }
        try {
            return Objects.requireNonNull(
                    issuanceTransaction.execute(
                            status -> telemetry.observe("idempotency", () -> issueInTransaction(key, input))));
        } catch (DataIntegrityViolationException exception) {
            if (!(exception.getCause() instanceof ConstraintViolationException violation)
                    || !"uq_issuance_scope".equalsIgnoreCase(violation.getConstraintName())) {
                throw exception;
            }
            return Objects.requireNonNull(
                    recoveryTransaction.execute(status ->
                            issuances
                                    .findByCustomerIdAndOperationAndRequestKey(input.customerId(), OPERATION, key)
                                    .map(existing -> replay(input, existing.boleto()))
                                    .orElseThrow(() -> exception)));
        }
    }

    private IssueResult issueInTransaction(String key, IssuanceInput input) {
        String customerId = input.customerId();
        var completed = issuances.findByCustomerIdAndOperationAndRequestKey(customerId, OPERATION, key);
        if (completed.isPresent()) {
            return replay(input, completed.get().boleto());
        }
        if (!issuances.tryLock(lockId(customerId, key))) {
            throw BoletoFailure.OPERATION_IN_PROGRESS.exception();
        }
        return issuances
                .findByCustomerIdAndOperationAndRequestKey(customerId, OPERATION, key)
                .map(existing -> replay(input, existing.boleto()))
                .orElseGet(() -> create(input, key));
    }

    private IssueResult replay(IssuanceInput input, Boleto boleto) {
        if (boleto.amount().compareTo(input.amount()) != 0 || !boleto.dueDate().equals(input.dueDate())) {
            throw BoletoFailure.IDEMPOTENCY_CONFLICT.exception();
        }
        return new IssueResult(Snapshot.from(boleto, LocalDate.now(clock)), false);
    }

    private IssueResult create(IssuanceInput input, String key) {
        if (!input.dueDate().isAfter(LocalDate.now(clock))) {
            throw BoletoFailure.INVALID_REQUEST.exception();
        }
        return telemetry.observe(
                "issuance.persist",
                () -> {
                    Instant now = clock.instant();
                    Boleto boleto =
                            boletos.save(
                                    new Boleto(
                                            UUID.randomUUID(),
                                            input.customerId(),
                                            input.amount(),
                                            input.dueDate(),
                                            now));
                    issuances.saveAndFlush(new IdempotentIssuance(input.customerId(), OPERATION, key, boleto));
                    journal.record(boleto, null, "API", now);
                    telemetry.countAfterCommit("boletos.issued");
                    return new IssueResult(Snapshot.from(boleto, LocalDate.now(clock)), true);
                });
    }

    private static long lockId(String customerId, String key) {
        try {
            String scope = OPERATION + "\0" + customerId + "\0" + key;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssueResult(Snapshot boleto, boolean created) {}
}
