package io.github.jlmc.rikikivault.gui.viewer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownFileViewerTest {

    private final MarkdownFileViewer viewer = new MarkdownFileViewer();

    @Test
    void supportsMdAndMarkdownExtensions() {
        assertTrue(viewer.supports("notes.md"));
        assertTrue(viewer.supports("notes.markdown"));
        assertTrue(!viewer.supports("notes.txt"));
    }

    @Test
    void rendersHeadingsAndEmphasisAsHtml() {
        byte[] markdown = "# Title\n\nSome **bold** text.".getBytes(StandardCharsets.UTF_8);

        ViewerResult result = viewer.view(markdown, "notes.md");

        ViewerResult.HtmlViewerResult html = assertInstanceOf(ViewerResult.HtmlViewerResult.class, result);
        assertTrue(html.html().contains("<h1>Title</h1>"));
        assertTrue(html.html().contains("<strong>bold</strong>"));
    }
}
