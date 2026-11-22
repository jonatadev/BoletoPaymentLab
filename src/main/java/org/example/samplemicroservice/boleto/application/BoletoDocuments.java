package org.example.samplemicroservice.boleto.application;

import java.io.IOException;
import java.util.UUID;
import org.example.samplemicroservice.boleto.document.PdfRenderer;
import org.springframework.stereotype.Service;

@Service
public class BoletoDocuments {
    private final BoletoQueries queries;
    private final PdfRenderer renderer;
    private final BoletoTelemetry telemetry;

    BoletoDocuments(BoletoQueries queries, PdfRenderer renderer, BoletoTelemetry telemetry) {
        this.queries = queries;
        this.renderer = renderer;
        this.telemetry = telemetry;
    }

    public byte[] render(UUID id) {
        Snapshot boleto = queries.find(id);
        try {
            return telemetry.observe("pdf.render", () -> renderer.render(boleto));
        } catch (IOException | IllegalArgumentException exception) {
            throw BoletoFailure.PDF_RENDER_FAILED.exception();
        }
    }
}
