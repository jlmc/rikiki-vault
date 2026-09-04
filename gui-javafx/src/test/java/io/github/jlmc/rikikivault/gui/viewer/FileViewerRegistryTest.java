package io.github.jlmc.rikikivault.gui.viewer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FileViewerRegistryTest {

    @Test
    void selectsJsonViewerForJsonFiles() {
        assertInstanceOf(JsonFileViewer.class, FileViewerRegistry.select("data.json"));
    }

    @Test
    void selectsXmlViewerForXmlFiles() {
        assertInstanceOf(XmlFileViewer.class, FileViewerRegistry.select("data.xml"));
    }

    @Test
    void selectsMarkdownViewerForMdFiles() {
        assertInstanceOf(MarkdownFileViewer.class, FileViewerRegistry.select("notes.md"));
    }

    @Test
    void selectsImageViewerForPngFiles() {
        assertInstanceOf(ImageFileViewer.class, FileViewerRegistry.select("photo.PNG"));
    }

    @Test
    void selectsPdfViewerForPdfFiles() {
        assertInstanceOf(PdfFileViewer.class, FileViewerRegistry.select("cv.pdf"));
    }

    @Test
    void fallsBackToTextViewerForUnknownExtensions() {
        assertInstanceOf(TextFileViewer.class, FileViewerRegistry.select("README"));
    }
}
