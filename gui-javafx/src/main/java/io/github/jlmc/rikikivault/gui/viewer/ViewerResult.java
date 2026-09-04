package io.github.jlmc.rikikivault.gui.viewer;

/** What a {@link FileViewer} produced - rendering-agnostic, JavaFX-free. */
public sealed interface ViewerResult {

    record TextViewerResult(String text) implements ViewerResult {
    }

    record HtmlViewerResult(String html) implements ViewerResult {
    }

    record ImageViewerResult(byte[] imageBytes) implements ViewerResult {
    }

    /** A PDF's first page rendered as an image, plus the extracted text of every page (cheap,
     * unlike rasterization) so the user can switch to a selectable/copyable view. */
    record PdfViewerResult(byte[] imageBytes, String extractedText) implements ViewerResult {
    }

    record UnsupportedViewerResult(String reason) implements ViewerResult {
    }
}
