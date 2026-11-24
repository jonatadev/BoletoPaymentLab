package org.example.samplemicroservice.boleto.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

@Entity
@Table(name = "boleto_revision")
@RevisionEntity(BoletoRevisionListener.class)
public class BoletoRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    private Long id;

    @RevisionTimestamp
    @Column(nullable = false)
    private long revisedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String actor;

    @Column(nullable = false, length = 36)
    private String correlationId;

    protected BoletoRevision() {}

    void attribute(String actor, String correlationId) {
        this.actor = actor;
        this.correlationId = correlationId;
    }

    public long id() {
        return id;
    }

    public Instant timestamp() {
        return Instant.ofEpochMilli(revisedAt);
    }

    public String actor() {
        return actor;
    }

    public String correlationId() {
        return correlationId;
    }
}
