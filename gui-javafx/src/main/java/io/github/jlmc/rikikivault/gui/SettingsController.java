package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.adapters.configuration.LocalAppPreferencesAdapter;
import io.github.jlmc.rikikivault.core.configuration.AppLanguage;
import io.github.jlmc.rikikivault.core.configuration.AppPreferences;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.ports.out.AppPreferencesPort;
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

/**
 * Single "Configurações" screen (Milestone 19) replacing the standalone "Definições de Git"
 * dialog - one clearly-labelled, navigable tab per concern (Git today, Idioma; more later)
 * instead of a separate modal window per setting. Global to the machine, not tied to any open
 * vault, so it's reachable both before one is opened (Welcome screen) and from the main window's
 * toolbar. Both sections persist together through {@link AppPreferencesPort}
 * ({@link LocalAppPreferencesAdapter}), written once on Save.
 */
public final class SettingsController {

    @FXML private TabPane tabPane;
    @FXML private RadioButton ptRadio;
    @FXML private RadioButton enRadio;
    @FXML private Label statusLabel;

    private Stage stage;
    private final AppPreferencesPort preferencesPort = new LocalAppPreferencesAdapter(VaultPaths.defaultPreferencesDirectory());
    private GitAuthSettingsPanel gitAuthPanel;
    private AppLanguage lastSavedLanguage;
    private Runnable onLanguageChanged;

    static void open(Stage owner, Runnable onLanguageChanged) {
        FXMLLoader loader = Fxml.loader("/fxml/settings-view.fxml");
        Parent root = loader.getRoot();
        SettingsController controller = loader.getController();
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(Messages.get("settings.windowTitle"));
        Scene scene = new Scene(root, 640, 640);
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        controller.init(stage, onLanguageChanged);
        stage.showAndWait();
    }

    private void init(Stage stage, Runnable onLanguageChanged) {
        this.stage = stage;
        this.onLanguageChanged = onLanguageChanged;

        AppPreferences preferences = preferencesPort.load();
        this.lastSavedLanguage = preferences.language();

        FXMLLoader gitLoader = Fxml.loader("/fxml/git-auth-settings-panel.fxml");
        Parent gitRoot = gitLoader.getRoot();
        gitAuthPanel = gitLoader.getController();
        gitAuthPanel.init(stage, preferences.gitAuth());
        Tab gitTab = new Tab(Messages.get("settings.tab.git"), gitRoot);
        tabPane.getTabs().add(0, gitTab);
        tabPane.getSelectionModel().select(gitTab);

        (preferences.language() == AppLanguage.EN ? enRadio : ptRadio).setSelected(true);
    }

    @FXML
    private void onSave() {
        AppLanguage language = enRadio.isSelected() ? AppLanguage.EN : AppLanguage.PT;
        preferencesPort.save(new AppPreferences(gitAuthPanel.buildSettings(), language));
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
        stage.close();
    }
}
