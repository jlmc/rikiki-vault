package io.github.jlmc.rikikivault.gui.support;

import io.github.jlmc.rikikivault.core.configuration.NotificationSettings;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Builds one toast Node, adds it to {@code container}, and owns its entrance/exit animation and
 * optional auto-dismiss timer. Package-private - only {@link Notifications} calls this.
 */
final class NotificationToast {

    private static final Duration ANIMATION_DURATION = Duration.millis(200);
    private static final double SLIDE_DISTANCE = 40;
    private static final double CLOSE_BUTTON_RESERVED_WIDTH = 28;
    private static final double MESSAGE_MAX_WIDTH = 300;

    private NotificationToast() {
    }

    static void show(Pane container, NotificationType type, String message, NotificationSettings settings) {
        FontIcon icon = new FontIcon(type.iconLiteral());
        icon.getStyleClass().add("notification-icon");

        Label label = new Label(message);
        label.setWrapText(true);
        // wrapText alone isn't reliable without a concrete bound on the Label itself - relying on
        // the parent's max-width to "squeeze" it via HBox layout doesn't always engage the wrap.
        label.setMaxWidth(MESSAGE_MAX_WIDTH);
        label.getStyleClass().add("notification-message");
        HBox.setHgrow(label, Priority.ALWAYS);

        HBox content = new HBox(10, icon, label);
        content.setAlignment(Pos.TOP_LEFT);
        // Leaves room so wrapped text never runs under the close button overlaid on top of it.
        StackPane.setMargin(content, new Insets(0, CLOSE_BUTTON_RESERVED_WIDTH, 0, 0));

        Button closeButton = new Button();
        closeButton.setGraphic(new FontIcon("fth-x"));
        closeButton.getStyleClass().add("notification-close-button");

        // A StackPane overlay - not part of the HBox row - is what keeps the close button pinned
        // to the top-right corner regardless of how tall the wrapped message grows.
        StackPane toast = new StackPane(content, closeButton);
        StackPane.setAlignment(closeButton, Pos.TOP_RIGHT);
        toast.getStyleClass().addAll("notification-toast", type.styleClass());
        toast.setOpacity(0);
        toast.setTranslateX(SLIDE_DISTANCE);

        container.getChildren().add(toast);
        animateIn(toast);

        PauseTransition autoDismiss = settings.autoDismiss()
                ? new PauseTransition(Duration.seconds(settings.autoDismissSeconds()))
                : null;
        Runnable dismiss = () -> animateOut(toast, () -> container.getChildren().remove(toast));

        closeButton.setOnAction(_ -> dismiss.run());
        if (autoDismiss != null) {
            autoDismiss.setOnFinished(_ -> dismiss.run());
            // Never let a notification the user is reading disappear from under the cursor.
            toast.setOnMouseEntered(_ -> autoDismiss.pause());
            toast.setOnMouseExited(_ -> autoDismiss.play());
            autoDismiss.play();
        }
    }

    private static void animateIn(StackPane toast) {
        TranslateTransition slide = new TranslateTransition(ANIMATION_DURATION, toast);
        slide.setToX(0);
        FadeTransition fade = new FadeTransition(ANIMATION_DURATION, toast);
        fade.setToValue(1);
        ParallelTransition transition = new ParallelTransition(slide, fade);
        transition.setInterpolator(Interpolator.EASE_OUT);
        transition.play();
    }

    private static void animateOut(StackPane toast, Runnable onFinished) {
        TranslateTransition slide = new TranslateTransition(ANIMATION_DURATION, toast);
        slide.setToX(SLIDE_DISTANCE);
        FadeTransition fade = new FadeTransition(ANIMATION_DURATION, toast);
        fade.setToValue(0);
        ParallelTransition transition = new ParallelTransition(slide, fade);
        transition.setInterpolator(Interpolator.EASE_IN);
        transition.setOnFinished(_ -> onFinished.run());
        transition.play();
    }
}
