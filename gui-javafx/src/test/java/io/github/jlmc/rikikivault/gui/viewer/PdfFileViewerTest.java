package io.github.jlmc.rikikivault.gui.viewer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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
    void rendersTheFirstPageAsAnImage() throws Exception {
        byte[] pdfBytes = someMinimalPdf();

        ViewerResult result = viewer.view(pdfBytes, "cv.pdf");

        ViewerResult.ImageViewerResult image = assertInstanceOf(ViewerResult.ImageViewerResult.class, result);
        assertTrue(image.imageBytes().length > 0);
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
