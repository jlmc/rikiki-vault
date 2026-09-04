package io.github.jlmc.rikikivault.gui.support;

import javafx.concurrent.Task;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs work off the JavaFX Application Thread (Git/crypto operations must not block
 * the UI). {@code onSuccess}/{@code onFailure} run back on the FX thread automatically, since
 * {@link Task}'s succeeded/failed handlers are always dispatched there.
 */
public final class BackgroundTask {

    private BackgroundTask() {
    }

    public static <T> void run(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return work.get();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));
        Thread thread = new Thread(task, "rikiki-vault-bg");
        thread.setDaemon(true);
        thread.start();
    }

    public static void runVoid(Runnable work, Runnable onSuccess, Consumer<Throwable> onFailure) {
        run(() -> {
            work.run();
            return null;
        }, ignored -> onSuccess.run(), onFailure);
    }
}
