package io.github.jlmc.rikikivault.gui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;

import java.io.ByteArrayInputStream;

/** Turns a rendering-agnostic {@link ViewerResult} into actual JavaFX {@link Node}s. */
final class ViewerResultRenderer {

    private ViewerResultRenderer() {
    }

    static Node render(ViewerResult result) {
        return switch (result) {
            case ViewerResult.TextViewerResult text -> renderText(text.text());
            case ViewerResult.HtmlViewerResult html -> renderHtml(html.html());
            case ViewerResult.ImageViewerResult image -> renderImage(image.imageBytes());
            case ViewerResult.UnsupportedViewerResult unsupported -> renderUnsupported(unsupported.reason());
        };
    }

    private static Node renderText(String text) {
        TextArea textArea = new TextArea(text);
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.getStyleClass().add("preview-text");
        return textArea;
    }

    private static Node renderHtml(String html) {
        WebView webView = new WebView();
        webView.getEngine().loadContent(html);
        return webView;
    }

    private static Node renderImage(byte[] imageBytes) {
        ImageView imageView = new ImageView(new Image(new ByteArrayInputStream(imageBytes)));
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        ScrollPane scrollPane = new ScrollPane(imageView);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("preview-image-scroll");
        imageView.fitWidthProperty().bind(scrollPane.widthProperty().subtract(24));
        return scrollPane;
    }

    static Node renderUnsupported(String reason) {
        Label label = new Label(reason);
        label.getStyleClass().add("preview-unsupported");
        StackPane pane = new StackPane(label);
        pane.setAlignment(Pos.CENTER);
        return pane;
    }
}
