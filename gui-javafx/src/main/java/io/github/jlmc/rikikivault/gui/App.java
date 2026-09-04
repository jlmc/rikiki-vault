package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.gui.controllers.InitOrCloneController;
import io.github.jlmc.rikikivault.gui.controllers.MainWindowController;
import io.github.jlmc.rikikivault.gui.controllers.WelcomeController;
import io.github.jlmc.rikikivault.gui.support.Fxml;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class App extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("Rikiki Vault");
        Scene scene = new Scene(loadWelcome(stage), 900, 600);
        scene.getStylesheets().add(App.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private Parent loadWelcome(Stage stage) {
        FXMLLoader loader = load("/fxml/welcome-view.fxml");
        WelcomeController controller = loader.getController();
        controller.init(stage, ctx -> openVault(stage, ctx));
        return loader.getRoot();
    }

    private void openVault(Stage stage, VaultContext ctx) {
        Parent next = ctx.isInitialized() ? loadMainWindow(ctx) : loadInitOrClone(stage, ctx);
        stage.getScene().setRoot(next);
    }

    private Parent loadInitOrClone(Stage stage, VaultContext ctx) {
        FXMLLoader loader = load("/fxml/init-or-clone-view.fxml");
        InitOrCloneController controller = loader.getController();
        controller.init(ctx, opened -> stage.getScene().setRoot(loadMainWindow(opened)));
        return loader.getRoot();
    }

    private Parent loadMainWindow(VaultContext ctx) {
        FXMLLoader loader = load("/fxml/main-window-view.fxml");
        MainWindowController controller = loader.getController();
        controller.init(ctx);
        return loader.getRoot();
    }

    private static FXMLLoader load(String resource) {
        return Fxml.loader(resource);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
