package io.github.jlmc.rikikivault.gui.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;

/**
 * A {@link javafx.scene.control.PasswordField} with a "reveal" eye toggle beside it, swapping
 * between a masked and a plain-text field the same way every one of this app's secret-entry
 * fields does (Git token, HTTP password, passphrase current/new/confirm, vault-unlock passphrase).
 * Used to be a hand-wired {@code StackPane + PasswordField + TextField + ToggleButton + FontIcon}
 * block duplicated in 4 FXML files, plus a matching {@code wireReveal(...)} method copy-pasted
 * into 3 controllers - both collapse into this one control.
 *
 * <p>A proper {@link Control}/{@link Skin} pair rather than a plain composite {@code Node}: the
 * API/behavior ({@code text}/{@code promptText}) lives here, the visual tree lives in
 * {@link RevealablePasswordFieldSkin} - the standard JavaFX separation, and what lets this stay
 * skinnable/CSS-stylable like any other control instead of a fixed hand-built layout.
 */
public final class RevealablePasswordField extends Control {

    private final StringProperty text = new SimpleStringProperty(this, "text", "");
    private final StringProperty promptText = new SimpleStringProperty(this, "promptText", "");

    public RevealablePasswordField() {
        getStyleClass().add("revealable-password-field");
        // Control's default max-width is unbounded, unlike a layout Pane's - without this, this
        // stretches to fill any fillWidth VBox/parent instead of respecting its own prefWidth.
        setMaxWidth(Region.USE_PREF_SIZE);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RevealablePasswordFieldSkin(this);
    }

    public String getText() {
        return text.get();
    }

    public void setText(String value) {
        text.set(value);
    }

    public StringProperty textProperty() {
        return text;
    }

    /** Matches {@link javafx.scene.control.TextInputControl#clear()}'s contract - empties the text. */
    public void clear() {
        setText("");
    }

    public String getPromptText() {
        return promptText.get();
    }

    public void setPromptText(String value) {
        promptText.set(value);
    }

    public StringProperty promptTextProperty() {
        return promptText;
    }
}
