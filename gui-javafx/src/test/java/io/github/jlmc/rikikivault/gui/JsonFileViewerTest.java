package io.github.jlmc.rikikivault.gui;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFileViewerTest {

    private final JsonFileViewer viewer = new JsonFileViewer();

    @Test
    void supportsOnlyJsonFiles() {
        assertTrue(viewer.supports("data.json"));
        assertTrue(!viewer.supports("data.txt"));
    }

    @Test
    void prettyPrintsValidJson() {
        byte[] compact = "{\"a\":1,\"b\":[2,3]}".getBytes(StandardCharsets.UTF_8);

        ViewerResult result = viewer.view(compact, "data.json");

        ViewerResult.TextViewerResult text = assertInstanceOf(ViewerResult.TextViewerResult.class, result);
        assertTrue(text.text().contains("\n"));
        assertTrue(text.text().contains("\"a\" : 1"));
    }

    @Test
    void fallsBackToRawTextForMalformedJson() {
        byte[] malformed = "{not json".getBytes(StandardCharsets.UTF_8);

        ViewerResult result = viewer.view(malformed, "data.json");

        ViewerResult.TextViewerResult text = assertInstanceOf(ViewerResult.TextViewerResult.class, result);
        assertTrue(text.text().equals("{not json"));
    }
}
