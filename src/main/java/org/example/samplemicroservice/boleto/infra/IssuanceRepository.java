package org.example.samplemicroservice.boleto.infra;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IssuanceRepository extends JpaRepository<IdempotentIssuance, UUID> {
    Optional<IdempotentIssuance> findByCustomerIdAndOperationAndRequestKey(
            String customerId, String operation, String requestKey);

    @Query(value = "select pg_try_advisory_xact_lock(:lockId)", nativeQuery = true)
    boolean tryLock(long lockId);
}
