package org.example.samplemicroservice.boleto.application;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.example.samplemicroservice.boleto.infra.Boleto;
import org.example.samplemicroservice.boleto.infra.BoletoRevision;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, timeout = 7)
public class BoletoRevisions {
    private final EntityManager entities;

    BoletoRevisions(EntityManager entities) {
        this.entities = entities;
    }

    public List<Metadata> list(UUID id, Long before, int size) {
        if (size < 1 || size > 100 || (before != null && before < 1)) {
            throw BoletoFailure.INVALID_REQUEST.exception();
        }
        AuditQuery query = revisions(id);
        if (before != null) {
            query.add(AuditEntity.revisionNumber().lt(before));
        }
        List<?> rows = query.addOrder(AuditEntity.revisionNumber().desc()).setMaxResults(size).getResultList();
        return rows.stream().map(row -> metadata((Object[]) row)).toList();
    }

    public Detail detail(UUID id, long revision) {
        return load(id, revision);
    }

    public Comparison compare(UUID id, long from, long to) {
        State original = load(id, from).state();
        State updated = load(id, to).state();
        if (original == null || updated == null) {
            return new Comparison(from, to, original == updated ? List.of() : List.of("state"));
        }
        List<String> fields = new ArrayList<>();
        changed(fields, "status", original.status(), updated.status());
        if (original.amount().compareTo(updated.amount()) != 0) {
            fields.add("amount");
        }
        changed(fields, "dueDate", original.dueDate(), updated.dueDate());
        changed(fields, "issuedAt", original.issuedAt(), updated.issuedAt());
        changed(fields, "version", original.version(), updated.version());
        changed(fields, "createdAt", original.createdAt(), updated.createdAt());
        changed(fields, "updatedAt", original.updatedAt(), updated.updatedAt());
        changed(fields, "createdBy", original.createdBy(), updated.createdBy());
        changed(fields, "updatedBy", original.updatedBy(), updated.updatedBy());
        return new Comparison(from, to, List.copyOf(fields));
    }

    private Detail load(UUID id, long revision) {
        if (revision < 1) {
            throw BoletoFailure.INVALID_REQUEST.exception();
        }
        List<?> rows = revisions(id).add(AuditEntity.revisionNumber().eq(revision)).setMaxResults(1).getResultList();
        if (rows.isEmpty()) {
            throw BoletoFailure.REVISION_NOT_FOUND.exception();
        }
        Object[] row = (Object[]) rows.getFirst();
        Boleto boleto = (Boleto) row[0];
        Metadata metadata = metadata(row);
        State state = metadata.operation() == RevisionType.DEL ? null : new State(
                boleto.status(), boleto.amount(), boleto.dueDate(), boleto.issuedAt(), boleto.version(),
                boleto.createdAt(), boleto.updatedAt(), boleto.createdBy(), boleto.updatedBy());
        return new Detail(metadata, state);
    }

    private AuditQuery revisions(UUID id) {
        return AuditReaderFactory.get(entities).createQuery()
                .forRevisionsOfEntity(Boleto.class, false, true)
                .add(AuditEntity.id().eq(id));
    }

    private static Metadata metadata(Object[] row) {
        BoletoRevision revision = (BoletoRevision) row[1];
        return new Metadata(revision.id(), revision.timestamp(), revision.actor(), revision.correlationId(),
                (RevisionType) row[2]);
    }

    private static void changed(List<String> fields, String field, Object original, Object updated) {
        if (!Objects.equals(original, updated)) {
            fields.add(field);
        }
    }

    public record Metadata(long revision, Instant timestamp, String actor, String correlationId,
            RevisionType operation) {}

    public record State(BoletoStatus status, BigDecimal amount, LocalDate dueDate, Instant issuedAt,
            Long version, Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {}

    public record Detail(Metadata metadata, State state) {}

    public record Comparison(long from, long to, List<String> changedFields) {}
}
