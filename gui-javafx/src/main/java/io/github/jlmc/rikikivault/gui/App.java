package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.gui.controllers.InitOrCloneController;
import io.github.jlmc.rikikivault.gui.controllers.MainWindowController;
import io.github.jlmc.rikikivault.gui.controllers.WelcomeController;
import io.github.jlmc.rikikivault.gui.support.Fxml;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    @Override
    public void start(Stage stage) {
        log.info("Starting Rikiki Vault GUI");
        stage.setTitle("Rikiki Vault");
        stage.getIcons().add(new Image(App.class.getResourceAsStream("/branding/icon.png")));
        Scene scene = new Scene(loadWelcome(stage), 900, 600);
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private Parent loadWelcome(Stage stage) {
        FXMLLoader loader = load("/fxml/welcome-view.fxml");
        WelcomeController controller = loader.getController();
        // Reloading this exact screen from scratch is what makes a language change (or any other
        // Settings change) take effect immediately, instead of only after the app is restarted -
        // Messages.bundle() already re-reads the current preference on every FXML load, so a
        // fresh load is all that's needed.
        controller.init(stage, ctx -> openVault(stage, ctx), () -> stage.getScene().setRoot(loadWelcome(stage)));
        return loader.getRoot();
    }

    private void openVault(Stage stage, VaultContext ctx) {
        log.info("Opening vault at {}", ctx.vaultRoot());
        Parent next = ctx.isInitialized() ? loadMainWindow(stage, ctx) : loadInitOrClone(stage, ctx);
        stage.getScene().setRoot(next);
    }

    private Parent loadInitOrClone(Stage stage, VaultContext ctx) {
        FXMLLoader loader = load("/fxml/init-or-clone-view.fxml");
        InitOrCloneController controller = loader.getController();
        controller.init(ctx, opened -> stage.getScene().setRoot(loadMainWindow(stage, opened)));
        return loader.getRoot();
    }

    private Parent loadMainWindow(Stage stage, VaultContext ctx) {
        FXMLLoader loader = load("/fxml/main-window-view.fxml");
        MainWindowController controller = loader.getController();
        controller.init(ctx, () -> stage.getScene().setRoot(loadMainWindow(stage, ctx)));
        return loader.getRoot();
    }

    private static FXMLLoader load(String resource) {
        return Fxml.loader(resource);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
