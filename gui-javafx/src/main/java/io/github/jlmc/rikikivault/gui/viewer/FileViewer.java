package io.github.jlmc.rikikivault.gui.viewer;

/** Deliberately independent of JavaFX/Swing/any UI toolkit; the caller decides how to render the result. */
public interface FileViewer {

    boolean supports(String fileName);

    ViewerResult view(byte[] content, String fileName);
}
