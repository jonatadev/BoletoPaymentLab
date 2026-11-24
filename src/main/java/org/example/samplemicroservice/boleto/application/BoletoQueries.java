package org.example.samplemicroservice.boleto.application;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.example.samplemicroservice.boleto.infra.Boleto;
import org.example.samplemicroservice.boleto.infra.BoletoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BoletoQueries {
    private static final List<String> SORT_FIELDS = List.of("issuedAt", "dueDate", "amount", "id");
    private final BoletoRepository boletos;
    private final Clock clock;

    BoletoQueries(BoletoRepository boletos, Clock clock) {
        this.boletos = boletos;
        this.clock = clock;
    }

    @Transactional(readOnly = true, timeout = 7)
    public Snapshot find(UUID id) {
        return boletos.findById(id)
                .map(boleto -> Snapshot.from(boleto, LocalDate.now(clock)))
                .orElseThrow(BoletoFailure.BOLETO_NOT_FOUND::exception);
    }

    @Transactional(readOnly = true, timeout = 7)
    public PageResult list(
            String customer, BoletoStatus status, LocalDate from, LocalDate to, int page, int size, String sort) {
        String[] order = sort.split(",", -1);
        if (size < 1
                || size > 100
                || page < 0
                || (long) page * size > 100_000
                || order.length != 2
                || !SORT_FIELDS.contains(order[0])
                || !List.of("asc", "desc").contains(order[1])
                || (from != null && to != null && from.isAfter(to))
                || (customer != null && !customer.matches("[A-Za-z0-9._-]{1,120}"))) {
            throw BoletoFailure.INVALID_REQUEST.exception();
        }
        LocalDate today = LocalDate.now(clock);
        var spec = Specification.<Boleto>unrestricted();
        if (customer != null) {
            spec = spec.and((row, query, cb) -> cb.equal(row.get("customerId"), customer));
        }
        if (status != null) {
            spec =
                    spec.and((row, query, cb) -> {
                        var persisted = row.<BoletoStatus>get("status");
                        var due = row.<LocalDate>get("dueDate");
                        return switch (status) {
                            case VENCIDO ->
                                    cb.or(
                                            cb.equal(persisted, BoletoStatus.VENCIDO),
                                            cb.and(cb.equal(persisted, BoletoStatus.EMITIDO), cb.lessThan(due, today)));
                            case EMITIDO -> cb.and(cb.equal(persisted, status), cb.greaterThanOrEqualTo(due, today));
                            case PAGO, CANCELADO -> cb.equal(persisted, status);
                        };
                    });
        }
        try {
            if (from != null) {
                var start = from.atStartOfDay(clock.getZone()).toInstant();
                spec = spec.and((row, query, cb) -> cb.greaterThanOrEqualTo(row.get("issuedAt"), start));
            }
            if (to != null) {
                var end = to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
                spec = spec.and((row, query, cb) -> cb.lessThan(row.get("issuedAt"), end));
            }
            var sorting = Sort.by(Sort.Direction.fromString(order[1]), order[0]);
            if (!order[0].equals("id")) {
                sorting = sorting.and(Sort.by("id"));
            }
            var result = boletos.findAll(spec, PageRequest.of(page, size, sorting));
            return new PageResult(
                    result.map(boleto -> Snapshot.from(boleto, today)).getContent(),
                    page,
                    size,
                    result.getTotalElements(),
                    result.getTotalPages());
        } catch (DateTimeException exception) {
            throw BoletoFailure.INVALID_REQUEST.exception();
        }
    }

    public record PageResult(List<Snapshot> content, int page, int size, long totalElements, int totalPages) {}
}
