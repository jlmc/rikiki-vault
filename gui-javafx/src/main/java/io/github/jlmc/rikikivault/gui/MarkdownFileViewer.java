package io.github.jlmc.rikikivault.gui;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

final class MarkdownFileViewer implements FileViewer {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    @Override
    public boolean supports(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    @Override
    public ViewerResult view(byte[] content, String fileName) {
        String raw = TextFileViewer.tryDecodeUtf8(content);
        if (raw == null) {
            return new ViewerResult.UnsupportedViewerResult("Pré-visualização não disponível para este tipo de ficheiro.");
        }
        Node document = parser.parse(raw);
        String body = renderer.render(document);
        String html = "<html><body style=\"font-family: -apple-system, Helvetica, Arial, sans-serif; padding: 12px;\">"
                + body + "</body></html>";
        return new ViewerResult.HtmlViewerResult(html);
    }
}
