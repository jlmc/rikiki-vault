package io.github.jlmc.rikikivault.gui.viewer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfFileViewerTest {

    private final PdfFileViewer viewer = new PdfFileViewer();

    @Test
    void supportsOnlyPdfFiles() {
        assertTrue(viewer.supports("cv.pdf"));
        assertTrue(!viewer.supports("cv.docx"));
    }

    @Test
    void aPdfWithTextRendersTheFirstPageAsAnImageAndExtractsTheText() throws Exception {
        byte[] pdfBytes = pdfWithText("Hello from a test PDF");

        ViewerResult result = viewer.view(pdfBytes, "cv.pdf");

        ViewerResult.PdfViewerResult pdf = assertInstanceOf(ViewerResult.PdfViewerResult.class, result);
        assertTrue(pdf.imageBytes().length > 0);
        assertTrue(pdf.extractedText().contains("Hello from a test PDF"));
    }

    @Test
    void aPdfWithNoTextFallsBackToJustTheImage() throws Exception {
        byte[] pdfBytes = someMinimalPdf();

        ViewerResult result = viewer.view(pdfBytes, "cv.pdf");

        ViewerResult.ImageViewerResult image = assertInstanceOf(ViewerResult.ImageViewerResult.class, result);
        assertTrue(image.imageBytes().length > 0);
    }

    private static byte[] pdfWithText(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] someMinimalPdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
