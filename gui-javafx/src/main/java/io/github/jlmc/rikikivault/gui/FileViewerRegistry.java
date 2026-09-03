package io.github.jlmc.rikikivault.gui;

import java.util.List;

/** Picks the first {@link FileViewer} that supports a given file name. {@link TextFileViewer} is last - it always supports. */
final class FileViewerRegistry {

    private static final List<FileViewer> VIEWERS = List.of(
            new JsonFileViewer(),
            new XmlFileViewer(),
            new MarkdownFileViewer(),
            new ImageFileViewer(),
            new PdfFileViewer(),
            new TextFileViewer());

    private FileViewerRegistry() {
    }

    static FileViewer select(String fileName) {
        return VIEWERS.stream()
                .filter(viewer -> viewer.supports(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No viewer matched (TextFileViewer should always match): " + fileName));
    }
}
