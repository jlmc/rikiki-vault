package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.adapters.configuration.LocalAppPreferencesAdapter;
import io.github.jlmc.rikikivault.core.configuration.VaultPaths;
import io.github.jlmc.rikikivault.core.ports.out.AppPreferencesPort;
import io.github.jlmc.rikikivault.gui.controllers.InitOrCloneController;
import io.github.jlmc.rikikivault.gui.controllers.MainWindowController;
import io.github.jlmc.rikikivault.gui.controllers.PassphrasePromptController;
import io.github.jlmc.rikikivault.gui.controllers.WelcomeController;
import io.github.jlmc.rikikivault.gui.support.BackgroundTasks;
import io.github.jlmc.rikikivault.gui.support.Fxml;
import io.github.jlmc.rikikivault.gui.support.Notifications;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private StackPane contentSlot;

    @Override
    public void start(Stage stage) {
        log.info("Starting Rikiki Vault GUI");
        stage.setTitle("Rikiki Vault");
        stage.getIcons().add(new Image(App.class.getResourceAsStream("/branding/icon.png")));

        contentSlot = new StackPane();

        // A persistent overlay above whatever screen is currently in contentSlot, so toasts
        // survive every setContent(...)-style screen swap below instead of being torn down with
        // the screen that triggered them - the panel's own internal structure (VBox/StackPane/
        // alignment/pickOnBounds) is Notifications' own implementation detail, not App's concern.
        AppPreferencesPort preferencesPort = new LocalAppPreferencesAdapter(VaultPaths.defaultPreferencesDirectory());
        Region notificationsHost = Notifications.createHost(preferencesPort.load().notifications());

        setContent(loadWelcome(stage));
        Scene scene = new Scene(new StackPane(contentSlot, notificationsHost), 900, 600);
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);

        stage.show();
    }

    private void setContent(Parent screen) {
        contentSlot.getChildren().setAll(screen);
    }

    @Override
    public void stop() {
        log.info("Stopping Rikiki Vault GUI");
        BackgroundTasks.shutdown();
    }

    private Parent loadWelcome(Stage stage) {
        FXMLLoader loader = load("/fxml/welcome-view.fxml");
        WelcomeController controller = loader.getController();
        // Reloading this exact screen from scratch is what makes a language change (or any other
        // Settings change) take effect immediately, instead of only after the app is restarted -
        // Messages.bundle() already re-reads the current preference on every FXML load, so a
        // fresh load is all that's needed.
        controller.init(stage, ctx -> openVault(stage, ctx), () -> setContent(loadWelcome(stage)));
        return loader.getRoot();
    }

    private void openVault(Stage stage, VaultContext ctx) {
        log.info("Opening vault at {}", ctx.vaultRoot());
        if (ctx.keyStorePort().isPassphraseProtected()) {
            setContent(loadPassphrasePrompt(stage, ctx));
        } else {
            proceedToVault(stage, ctx);
        }
    }

    private void proceedToVault(Stage stage, VaultContext ctx) {
        Parent next = ctx.isInitialized() ? loadMainWindow(stage, ctx) : loadInitOrClone(stage, ctx);
        setContent(next);
    }

    private Parent loadPassphrasePrompt(Stage stage, VaultContext ctx) {
        FXMLLoader loader = load("/fxml/passphrase-prompt-view.fxml");
        PassphrasePromptController controller = loader.getController();
        controller.init(ctx,
                unlocked -> proceedToVault(stage, unlocked),
                () -> setContent(loadWelcome(stage)));
        return loader.getRoot();
    }

    private Parent loadInitOrClone(Stage stage, VaultContext ctx) {
        FXMLLoader loader = load("/fxml/init-or-clone-view.fxml");
        InitOrCloneController controller = loader.getController();
        controller.init(ctx, opened -> setContent(loadMainWindow(stage, opened)));
        return loader.getRoot();
    }

    private Parent loadMainWindow(Stage stage, VaultContext ctx) {
        FXMLLoader loader = load("/fxml/main-window-view.fxml");
        MainWindowController controller = loader.getController();
        controller.init(ctx, () -> setContent(loadMainWindow(stage, ctx)));
        return loader.getRoot();
    }

    private static FXMLLoader load(String resource) {
        return Fxml.loader(resource);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
