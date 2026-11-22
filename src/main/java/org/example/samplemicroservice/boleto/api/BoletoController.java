package org.example.samplemicroservice.boleto.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.example.samplemicroservice.boleto.application.BoletoDocuments;
import org.example.samplemicroservice.boleto.application.BoletoFailure;
import org.example.samplemicroservice.boleto.application.BoletoLifecycle;
import org.example.samplemicroservice.boleto.application.BoletoQueries;
import org.example.samplemicroservice.boleto.application.BoletoService;
import org.example.samplemicroservice.boleto.application.BoletoRevisions;
import org.example.samplemicroservice.boleto.application.BoletoTelemetry;
import org.example.samplemicroservice.boleto.application.Snapshot;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.example.samplemicroservice.boleto.events.BoletoJournal.AuditEntry;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/boletos")
public class BoletoController {
    private final BoletoService service;
    private final BoletoDocuments documents;
    private final BoletoTelemetry telemetry;
    private final BoletoLifecycle lifecycle;
    private final BoletoQueries queries;
    private final BoletoRevisions revisions;

    public BoletoController(BoletoService service, BoletoDocuments documents, BoletoTelemetry telemetry,
            BoletoLifecycle lifecycle, BoletoQueries queries, BoletoRevisions revisions) {
        this.service = service;
        this.documents = documents;
        this.telemetry = telemetry;
        this.lifecycle = lifecycle;
        this.queries = queries;
        this.revisions = revisions;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Snapshot> issue(@RequestHeader HttpHeaders headers, @RequestBody JsonNode json) {
        List<String> keys = headers.get("Idempotency-Key");
        if (keys == null || keys.size() != 1 || keys.getFirst().contains(",")) {
            throw BoletoFailure.INVALID_REQUEST.exception();
        }
        var result = telemetry.observe("issuance", () ->
                service.issue(keys.getFirst(), IssueBoletoRequest.from(json).toInput()));
        if (result.created()) {
            telemetry.count("boletos.issued");
            return ResponseEntity.created(URI.create("/api/v1/boletos/" + result.boleto().id()))
                    .body(result.boleto());
        }
        return ResponseEntity.ok(result.boleto());
    }

    @GetMapping("/{id}")
    Snapshot get(@PathVariable String id) {
        return lifecycle.detail(parseId(id));
    }

    @GetMapping
    BoletoQueries.PageResult list(@RequestParam(required = false) String customerId,
            @RequestParam(required = false) BoletoStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "issuedAt,desc") String sort) {
        return queries.list(customerId, status, from, to, page, size, sort);
    }

    @PostMapping("/{id}/payments")
    Snapshot pay(@PathVariable String id) {
        return lifecycle.change(parseId(id), BoletoStatus.PAGO);
    }

    @PostMapping("/{id}/cancellations")
    Snapshot cancel(@PathVariable String id) {
        return lifecycle.change(parseId(id), BoletoStatus.CANCELADO);
    }

    @GetMapping("/{id}/history")
    List<AuditEntry> history(@PathVariable String id) {
        return lifecycle.history(parseId(id));
    }

    @GetMapping("/{id}/revisions")
    List<BoletoRevisions.Metadata> revisions(@PathVariable String id,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "20") int size) {
        return revisions.list(parseId(id), before, size);
    }

    @GetMapping("/{id}/revisions/{revision}")
    BoletoRevisions.Detail revision(@PathVariable String id, @PathVariable long revision) {
        return revisions.detail(parseId(id), revision);
    }

    @GetMapping("/{id}/revisions/compare")
    BoletoRevisions.Comparison compareRevisions(@PathVariable String id,
            @RequestParam long from, @RequestParam long to) {
        return revisions.compare(parseId(id), from, to);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> pdf(@PathVariable String id) {
        UUID boletoId = parseId(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("boleto-" + boletoId + ".pdf").toString())
                .body(documents.render(boletoId));
    }

    private UUID parseId(String id) {
        if (!id.matches("[0-9A-Fa-f]{8}(-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}")) {
            throw BoletoFailure.INVALID_REQUEST.exception();
        }
        return UUID.fromString(id);
    }
}
