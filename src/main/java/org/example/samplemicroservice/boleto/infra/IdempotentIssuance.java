package org.example.samplemicroservice.boleto.infra;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.util.UUID;

@Entity
public class IdempotentIssuance {
    @Id private UUID id;
    private String customerId;
    private String operation;
    private String requestKey;

    @OneToOne(optional = false)
    @JoinColumn(name = "boleto_id")
    private Boleto boleto;

    protected IdempotentIssuance() {}

    public IdempotentIssuance(String customerId, String operation, String requestKey, Boleto boleto) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.operation = operation;
        this.requestKey = requestKey;
        this.boleto = boleto;
    }

    public Boleto boleto() {
        return boleto;
    }
}
