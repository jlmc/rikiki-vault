package io.github.jlmc.rikikivault.gui;

/** Plan.md §14 - deliberately independent of JavaFX/Swing/any UI toolkit; the caller decides how to render the result. */
interface FileViewer {

    boolean supports(String fileName);

    ViewerResult view(byte[] content, String fileName);
}
