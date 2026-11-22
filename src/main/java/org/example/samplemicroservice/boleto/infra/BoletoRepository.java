package org.example.samplemicroservice.boleto.infra;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BoletoRepository extends JpaRepository<Boleto, UUID>, JpaSpecificationExecutor<Boleto> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Boleto b where b.id = :id")
    Optional<Boleto> findForUpdate(UUID id);

    @Query("select b.id from Boleto b where b.status = :status and b.dueDate < :today order by b.dueDate, b.id")
    List<UUID> findDueIds(BoletoStatus status, LocalDate today, Pageable page);
}
