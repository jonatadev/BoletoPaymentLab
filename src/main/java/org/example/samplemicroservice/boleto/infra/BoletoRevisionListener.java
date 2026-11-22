package org.example.samplemicroservice.boleto.infra;

import java.util.Optional;
import java.util.UUID;
import org.hibernate.envers.RevisionListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class BoletoRevisionListener implements RevisionListener {
    @Override
    public void newRevision(Object revision) {
        ((BoletoRevision) revision).attribute(BoletoAuditing.actor(),
                correlation().orElseGet(() -> UUID.randomUUID().toString()));
    }

    public static String correlate(String candidate) {
        return correlation().orElseGet(() -> {
            TransactionSynchronizationManager.registerSynchronization(new Correlation(candidate));
            return candidate;
        });
    }

    private static Optional<String> correlation() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return Optional.empty();
        }
        return TransactionSynchronizationManager.getSynchronizations().stream()
                .filter(Correlation.class::isInstance)
                .map(Correlation.class::cast)
                .map(Correlation::value)
                .findFirst();
    }

    private record Correlation(String value) implements TransactionSynchronization {}
}
