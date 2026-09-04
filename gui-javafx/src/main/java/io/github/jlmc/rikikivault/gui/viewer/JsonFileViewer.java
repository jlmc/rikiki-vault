package io.github.jlmc.rikikivault.gui.viewer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jlmc.rikikivault.gui.support.Messages;

final class JsonFileViewer implements FileViewer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(String fileName) {
        return fileName.toLowerCase().endsWith(".json");
    }

    @Override
    public ViewerResult view(byte[] content, String fileName) {
        String raw = TextFileViewer.tryDecodeUtf8(content);
        if (raw == null) {
            return new ViewerResult.UnsupportedViewerResult(Messages.get("fileViewer.unsupported"));
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            String pretty = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            return new ViewerResult.TextViewerResult(pretty);
        } catch (Exception e) {
            return new ViewerResult.TextViewerResult(raw);
        }
    }
}
