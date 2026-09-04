package io.github.jlmc.rikikivault.gui.viewer;

import io.github.jlmc.rikikivault.gui.support.Messages;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Renders just the first page (Plan.md §14 asks for a preview, not a full reader). */
final class PdfFileViewer implements FileViewer {

    @Override
    public boolean supports(String fileName) {
        return fileName.toLowerCase().endsWith(".pdf");
    }

    @Override
    public ViewerResult view(byte[] content, String fileName) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.getNumberOfPages() == 0) {
                return new ViewerResult.UnsupportedViewerResult(Messages.get("fileViewer.pdfNoPages"));
            }
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 96);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return new ViewerResult.ImageViewerResult(out.toByteArray());
        } catch (IOException e) {
            return new ViewerResult.UnsupportedViewerResult(Messages.get("fileViewer.pdfError"));
        }
    }
}
