package org.example.samplemicroservice.boleto.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.example.samplemicroservice.boleto.application.Snapshot;
import org.springframework.stereotype.Component;

@Component
public class PdfBoxRenderer implements PdfRenderer {
    @Override
    public byte[] render(Snapshot boleto) throws IOException {
        try (var document = new PDDocument();
                var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(FontName.HELVETICA_BOLD), 16);
                content.setLeading(24);
                content.newLineAtOffset(48, 730);
                content.showText("DOCUMENTO EDUCACIONAL");
                content.newLine();
                content.showText("SEM VALIDADE PARA PAGAMENTO");
                content.newLine();
                content.setFont(new PDType1Font(FontName.HELVETICA), 11);
                content.showText("Boleto: " + boleto.id());
                content.newLine();
                content.showText("Amount: BRL " + boleto.amount().toPlainString());
                content.newLine();
                content.showText("Due date: " + boleto.dueDate());
                content.newLine();
                content.showText("Synthetic customer:");
                for (int start = 0; start < boleto.customerId().length(); start += 60) {
                    content.newLine();
                    content.showText(
                            boleto.customerId().substring(start, Math.min(start + 60, boleto.customerId().length())));
                }
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
