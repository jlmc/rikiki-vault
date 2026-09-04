package io.github.jlmc.rikikivault.gui;

import javafx.fxml.FXMLLoader;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Loads an FXML resource with the current {@link Messages#bundle()} wired in, so every {@code
 * %key} placeholder in the file resolves in the active language - centralized here once (Milestone
 * 20) instead of every screen's {@code open()} repeating the same FXMLLoader/try-catch/resources
 * boilerplate and risking one being left out.
 */
final class Fxml {

    private Fxml() {
    }

    static FXMLLoader loader(String resource) {
        FXMLLoader loader = new FXMLLoader(Fxml.class.getResource(resource), Messages.bundle());
        try {
            loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + resource, e);
        }
        return loader;
    }
}
