package io.github.jlmc.rikikivault.gui.controllers;

import io.github.jlmc.rikikivault.core.adapters.configuration.LocalAppPreferencesAdapter;
import io.github.jlmc.rikikivault.core.configuration.AppLanguage;
import io.github.jlmc.rikikivault.core.configuration.AppPreferences;
import io.github.jlmc.rikikivault.core.configuration.NotificationSettings;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.ports.out.AppPreferencesPort;
import io.github.jlmc.rikikivault.gui.App;
import io.github.jlmc.rikikivault.gui.support.Fxml;
import io.github.jlmc.rikikivault.gui.support.Messages;
import io.github.jlmc.rikikivault.gui.support.Notifications;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.function.Consumer;

/**
 * Single "Configurações" screen (Milestone 19) replacing the standalone "Definições de Git"
 * dialog - one clearly-labelled, navigable tab per concern (Git today, Idioma; more later)
 * instead of a separate modal window per setting. Global to the machine, not tied to any open
 * vault, so it's reachable both before one is opened (Welcome screen, still as a modal dialog via
 * {@link #openModal}) and from the main window's toolbar (Milestone 31, embedded in the sidebar
 * panel via {@link #embed}). Both sections persist together through {@link AppPreferencesPort}
 * ({@link LocalAppPreferencesAdapter}), written once on Save.
 */
public final class SettingsController {

    @FXML private TabPane tabPane;
    @FXML private RadioButton ptRadio;
    @FXML private RadioButton enRadio;
    @FXML private Label statusLabel;

    private final AppPreferencesPort preferencesPort = new LocalAppPreferencesAdapter(VaultPaths.defaultPreferencesDirectory());
    private GitAuthSettingsPanel gitAuthPanel;
    private AppSettingsPanel appSettingsPanel;
    private AppLanguage lastSavedLanguage;
    private Runnable onLanguageChanged;
    private Runnable onClose;

    /**
     * Builds the Settings content as a plain, embeddable {@link Parent} - used both by
     * {@link #openModal} (wrapped in its own {@link Stage}) and directly by the main window's
     * sidebar panel, so the two hosting styles never duplicate any of this screen's logic.
     *
     * @param onPassphraseChanged notified with the new passphrase (or {@code null} if removed)
     *                            right after a change is applied in the Segurança tab - lets a
     *                            host with an open vault session refresh its own cached unlocked
     *                            {@code KeyStorePort} instead of asking the user to re-enter it.
     *                            Pass {@code null} when there's no session to keep in sync (e.g.
     *                            {@link #openModal}, reached before any vault is open).
     */
    static Parent embed(Runnable onLanguageChanged, Runnable onClose, Consumer<char[]> onPassphraseChanged) {
        FXMLLoader loader = Fxml.loader("/fxml/settings-view.fxml");
        Parent root = loader.getRoot();
        SettingsController controller = loader.getController();
        controller.init(onLanguageChanged, onClose, onPassphraseChanged);
        return root;
    }

    static void openModal(Stage owner, Runnable onLanguageChanged) {
        Stage stage = new Stage();
        Parent root = embed(onLanguageChanged, stage::close, null);
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Messages.get("settings.windowTitle"));
        Scene scene = new Scene(root, 640, 640);
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
    }

    private void init(Runnable onLanguageChanged, Runnable onClose, Consumer<char[]> onPassphraseChanged) {
        this.onLanguageChanged = onLanguageChanged;
        this.onClose = onClose;

        AppPreferences preferences = preferencesPort.load();
        this.lastSavedLanguage = preferences.language();

        FXMLLoader gitLoader = Fxml.loader("/fxml/git-auth-settings-panel.fxml");
        Parent gitRoot = gitLoader.getRoot();
        gitAuthPanel = gitLoader.getController();
        gitAuthPanel.init(preferences.gitAuth());
        Tab gitTab = new Tab(Messages.get("settings.tab.git"), gitRoot);
        tabPane.getTabs().add(0, gitTab);

        FXMLLoader securityLoader = Fxml.loader("/fxml/security-settings-panel.fxml");
        Parent securityRoot = securityLoader.getRoot();
        SecuritySettingsPanel securityPanel = securityLoader.getController();
        securityPanel.init(onPassphraseChanged);
        Tab securityTab = new Tab(Messages.get("settings.tab.security"), securityRoot);
        tabPane.getTabs().add(1, securityTab);

        FXMLLoader appLoader = Fxml.loader("/fxml/app-settings-panel.fxml");
        Parent appRoot = appLoader.getRoot();
        appSettingsPanel = appLoader.getController();
        appSettingsPanel.init(preferences.notifications());
        Tab appTab = new Tab(Messages.get("settings.tab.app"), appRoot);
        tabPane.getTabs().add(2, appTab);

        tabPane.getSelectionModel().select(gitTab);

        (preferences.language() == AppLanguage.EN ? enRadio : ptRadio).setSelected(true);
    }

    @FXML
    private void onSave() {
        AppLanguage language = enRadio.isSelected() ? AppLanguage.EN : AppLanguage.PT;
        NotificationSettings notificationSettings = appSettingsPanel.buildSettings();
        preferencesPort.save(new AppPreferences(gitAuthPanel.buildSettings(), language, notificationSettings));
        Notifications.updateSettings(notificationSettings);
        statusLabel.setText(Messages.get("settings.saved"));

        // Real re-rendering in the new language arrives with Milestone 20's message bundles -
        // for now this just notifies the caller that a language change was saved.
        if (language != lastSavedLanguage) {
            lastSavedLanguage = language;
            if (onLanguageChanged != null) {
                onLanguageChanged.run();
            }
        }
    }

    @FXML
    private void onClose() {
        if (onClose != null) {
            onClose.run();
        }
    }
}
