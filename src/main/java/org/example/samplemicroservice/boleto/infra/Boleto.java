package org.example.samplemicroservice.boleto.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Audited
@AuditTable("boleto_revision_state")
@EntityListeners(AuditingEntityListener.class)
public class Boleto {
    @Id private UUID id;
    @NotAudited private String customerId;
    private BigDecimal amount;
    private LocalDate dueDate;
    private Instant issuedAt;

    @Enumerated(EnumType.STRING)
    private BoletoStatus status;

    @Version private Long version;

    @CreatedDate
    @Column(updatable = false)
    private Instant createdAt;

    @LastModifiedDate private Instant updatedAt;

    @CreatedBy
    @Column(updatable = false, columnDefinition = "text")
    private String createdBy;

    @LastModifiedBy
    @Column(columnDefinition = "text")
    private String updatedBy;

    protected Boleto() {}

    public Boleto(UUID id, String customerId, BigDecimal amount, LocalDate dueDate, Instant issuedAt) {
        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
        this.dueDate = dueDate;
        this.issuedAt = issuedAt;
        this.status = BoletoStatus.EMITIDO;
    }

    public UUID id() {
        return id;
    }

    public String customerId() {
        return customerId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public BoletoStatus status() {
        return status;
    }

    public Long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public String updatedBy() {
        return updatedBy;
    }

    public void transitionTo(BoletoStatus target) {
        if (status != BoletoStatus.EMITIDO || target == BoletoStatus.EMITIDO) {
            throw new IllegalStateException("Transition requires an issued boleto and a terminal target");
        }
        status = target;
    }
}
