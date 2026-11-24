package org.example.samplemicroservice.boleto.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.example.samplemicroservice.boleto.application.BoletoFailure.FailureException;
import org.example.samplemicroservice.boleto.document.PdfRenderer;
import org.example.samplemicroservice.boleto.domain.BoletoStatus;
import org.junit.jupiter.api.Test;

class BoletoDocumentsTests {
    private final BoletoQueries queries = mock(BoletoQueries.class);
    private final PdfRenderer renderer = mock(PdfRenderer.class);
    private final BoletoDocuments documents =
            new BoletoDocuments(
                    queries, renderer, new BoletoTelemetry(ObservationRegistry.NOOP, new SimpleMeterRegistry()));

    @Test
    void shouldTranslateDocumentRenderingFailure() throws IOException {
        UUID id = UUID.randomUUID();
        var boleto =
                new Snapshot(
                        id,
                        "customer-test",
                        new BigDecimal("10.00"),
                        "BRL",
                        LocalDate.of(2026, 1, 3),
                        BoletoStatus.EMITIDO,
                        Instant.parse("2026-01-01T12:00:00Z"));
        when(queries.find(id)).thenReturn(boleto);
        when(renderer.render(boleto)).thenThrow(new IOException("Test rendering failure"));

        assertThatThrownBy(() -> documents.render(id))
                .isInstanceOfSatisfying(
                        FailureException.class,
                        exception ->
                                Assertions.assertThat(exception.failure()).isEqualTo(BoletoFailure.PDF_RENDER_FAILED));
    }

    @Test
    void shouldSkipRenderingWhenTheBoletoIsMissing() {
        UUID id = UUID.randomUUID();
        when(queries.find(id)).thenThrow(BoletoFailure.BOLETO_NOT_FOUND.exception());

        assertThatThrownBy(() -> documents.render(id))
                .isInstanceOfSatisfying(
                        FailureException.class,
                        exception ->
                                Assertions.assertThat(exception.failure()).isEqualTo(BoletoFailure.BOLETO_NOT_FOUND));
        verifyNoInteractions(renderer);
    }
}
