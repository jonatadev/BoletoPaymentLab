package org.example.samplemicroservice.boleto.document;

import java.io.IOException;
import org.example.samplemicroservice.boleto.application.Snapshot;

public interface PdfRenderer {
    byte[] render(Snapshot boleto) throws IOException;
}
