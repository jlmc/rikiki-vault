package io.github.jlmc.rikikivault.gui.support;

import javafx.concurrent.Task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs work off the JavaFX Application Thread (Git/crypto operations must not block
 * the UI), on a shared virtual-thread executor. {@code onSuccess}/{@code onFailure} run back on
 * the FX thread automatically, since {@link Task}'s succeeded/failed handlers are always
 * dispatched there.
 */
public final class BackgroundTasks {

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private BackgroundTasks() {
    }

    public static <T> void run(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() {
                return work.get();
            }
        };
        task.setOnSucceeded(_ -> onSuccess.accept(task.getValue()));
        task.setOnFailed(_ -> onFailure.accept(task.getException()));
        EXECUTOR.submit(task);
    }

    public static void runVoid(Runnable work, Runnable onSuccess, Consumer<Throwable> onFailure) {
        run(() -> {
            work.run();
            return null;
        }, _ -> onSuccess.run(), onFailure);
    }

    public static void shutdown() {
        EXECUTOR.close();
    }
}
