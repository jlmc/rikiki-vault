package io.github.jlmc.rikikivault.gui.controls;

import io.github.jlmc.rikikivault.gui.support.Messages;
import javafx.geometry.Pos;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SkinBase;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Visual tree for {@link RevealablePasswordField}: a masked/plain-text field pair (only one ever
 * visible) plus a reveal toggle - the same {@code StackPane + PasswordField + TextField +
 * ToggleButton + FontIcon} shape every hand-wired instance of this used to build, now built once.
 */
final class RevealablePasswordFieldSkin extends SkinBase<RevealablePasswordField> {

    RevealablePasswordFieldSkin(RevealablePasswordField control) {
        super(control);

        PasswordField masked = new PasswordField();
        TextField revealed = new TextField();
        masked.textProperty().bindBidirectional(control.textProperty());
        masked.promptTextProperty().bind(control.promptTextProperty());
        revealed.textProperty().bindBidirectional(masked.textProperty());
        revealed.setManaged(false);
        revealed.setVisible(false);
        masked.setMaxWidth(Double.MAX_VALUE);
        revealed.setMaxWidth(Double.MAX_VALUE);

        StackPane fieldStack = new StackPane(masked, revealed);
        HBox.setHgrow(fieldStack, Priority.ALWAYS);

        FontIcon eyeIcon = new FontIcon("fth-eye");
        ToggleButton eyeToggle = new ToggleButton();
        eyeToggle.setGraphic(eyeIcon);
        eyeToggle.setTooltip(new Tooltip(Messages.get("gitAuthPanel.eyeToggle.tooltip")));
        eyeToggle.selectedProperty().addListener((_, _, isSelected) -> {
            revealed.setVisible(isSelected);
            revealed.setManaged(isSelected);
            masked.setVisible(!isSelected);
            masked.setManaged(!isSelected);
            eyeIcon.setIconLiteral(isSelected ? "fth-eye-off" : "fth-eye");
        });

        HBox root = new HBox(8, fieldStack, eyeToggle);
        root.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(root);
    }
}
