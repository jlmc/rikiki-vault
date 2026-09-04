package io.github.jlmc.rikikivault.gui.viewer;

/** What a {@link FileViewer} produced (Plan.md §14) - rendering-agnostic, JavaFX-free. */
public sealed interface ViewerResult {

    record TextViewerResult(String text) implements ViewerResult {
    }

    record HtmlViewerResult(String html) implements ViewerResult {
    }

    record ImageViewerResult(byte[] imageBytes) implements ViewerResult {
    }

    record UnsupportedViewerResult(String reason) implements ViewerResult {
    }
}
