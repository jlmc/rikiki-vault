package io.github.jlmc.rikikivault.gui.viewer;

import java.util.List;

final class ImageFileViewer implements FileViewer {

    private static final List<String> EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".gif", ".bmp");

    @Override
    public boolean supports(String fileName) {
        String lower = fileName.toLowerCase();
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    @Override
    public ViewerResult view(byte[] content, String fileName) {
        return new ViewerResult.ImageViewerResult(content);
    }
}
