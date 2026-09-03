package io.github.jlmc.rikikivault.gui;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlFileViewerTest {

    private final XmlFileViewer viewer = new XmlFileViewer();

    @Test
    void supportsOnlyXmlFiles() {
        assertTrue(viewer.supports("data.xml"));
        assertTrue(!viewer.supports("data.json"));
    }

    @Test
    void prettyPrintsValidXml() {
        byte[] compact = "<root><child>value</child></root>".getBytes(StandardCharsets.UTF_8);

        ViewerResult result = viewer.view(compact, "data.xml");

        ViewerResult.TextViewerResult text = assertInstanceOf(ViewerResult.TextViewerResult.class, result);
        assertTrue(text.text().contains("\n"));
        assertTrue(text.text().contains("<child>value</child>"));
    }

    @Test
    void fallsBackToRawTextForMalformedXml() {
        byte[] malformed = "<not xml".getBytes(StandardCharsets.UTF_8);

        ViewerResult result = viewer.view(malformed, "data.xml");

        ViewerResult.TextViewerResult text = assertInstanceOf(ViewerResult.TextViewerResult.class, result);
        assertTrue(text.text().equals("<not xml"));
    }
}
