package io.github.jlmc.rikikivault.gui.viewer;

import io.github.jlmc.rikikivault.gui.support.Messages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;

import java.io.ByteArrayInputStream;

/** Turns a rendering-agnostic {@link ViewerResult} into actual JavaFX {@link Node}s. */
public final class ViewerResultRenderer {

    private ViewerResultRenderer() {
    }

    public static Node render(ViewerResult result) {
        return switch (result) {
            case ViewerResult.TextViewerResult text -> renderText(text.text());
            case ViewerResult.HtmlViewerResult html -> renderHtml(html.html());
            case ViewerResult.ImageViewerResult image -> renderImage(image.imageBytes());
            case ViewerResult.PdfViewerResult pdf -> renderPdf(pdf.imageBytes(), pdf.extractedText());
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

    /**
     * The rendered page image is the default view (visual fidelity); a toggle button swaps to a
     * plain, selectable {@link TextArea} with the PDF's extracted text - JavaFX has no ready
     * widget for a selectable text layer overlaid on an image, so this reuses the same
     * view/edit-style toggle interaction already established elsewhere in this app instead.
     */
    private static Node renderPdf(byte[] imageBytes, String extractedText) {
        Node imageView = renderImage(imageBytes);

        TextArea textArea = new TextArea(extractedText);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.getStyleClass().add("preview-text");
        textArea.setVisible(false);
        textArea.setManaged(false);

        StackPane body = new StackPane(imageView, textArea);

        Button toggle = new Button(Messages.get("fileViewer.pdf.showText"));
        toggle.setOnAction(_ -> {
            boolean showingText = textArea.isVisible();
            imageView.setVisible(showingText);
            imageView.setManaged(showingText);
            textArea.setVisible(!showingText);
            textArea.setManaged(!showingText);
            toggle.setText(showingText ? Messages.get("fileViewer.pdf.showText") : Messages.get("fileViewer.pdf.showImage"));
        });

        HBox toolbar = new HBox(toggle);
        toolbar.setPadding(new Insets(0, 0, 8, 0));
        BorderPane pane = new BorderPane();
        pane.setTop(toolbar);
        pane.setCenter(body);
        return pane;
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

    public static Node renderUnsupported(String reason) {
        Label label = new Label(reason);
        label.getStyleClass().add("preview-unsupported");
        StackPane pane = new StackPane(label);
        pane.setAlignment(Pos.CENTER);
        return pane;
    }
}
