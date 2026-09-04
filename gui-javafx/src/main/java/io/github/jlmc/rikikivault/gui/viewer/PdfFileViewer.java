package io.github.jlmc.rikikivault.gui.viewer;

import io.github.jlmc.rikikivault.gui.support.Messages;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Renders just the first page as an image (as a preview, not a full reader),
 * but extracts the text of every page - extraction is cheap, unlike rasterization, so there's no
 * reason to limit it to page 1 the way the image is. Lets the preview offer a selectable/copyable
 * text view alongside the image.
 */
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
            byte[] imageBytes = out.toByteArray();

            String extractedText = extractText(document);
            if (extractedText == null || extractedText.isBlank()) {
                return new ViewerResult.ImageViewerResult(imageBytes);
            }
            return new ViewerResult.PdfViewerResult(imageBytes, extractedText);
        } catch (IOException e) {
            return new ViewerResult.UnsupportedViewerResult(Messages.get("fileViewer.pdfError"));
        }
    }

    private static String extractText(PDDocument document) {
        try {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            // A scanned/image-only PDF (or one PDFBox can't extract from) still gets the image
            // preview - just without the selectable-text toggle.
            return null;
        }
    }
}
