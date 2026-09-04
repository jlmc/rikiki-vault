package io.github.jlmc.rikikivault.gui;

/**
 * Entry point for the packaged jar/native app. `java -jar`/jpackage refuse to launch a
 * Main-Class that directly extends {@code javafx.application.Application} on the classpath
 * (a launcher check unrelated to whether the JavaFX jars are actually present) - going through
 * this indirection class dodges that check. {@code mvn javafx:run} is unaffected and keeps
 * pointing at {@link App} directly.
 */
public final class Launcher {

    public static void main(String[] args) {
        App.main(args);
    }
}
