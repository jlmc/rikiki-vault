package io.github.jlmc.rikikivault.gui.support;

import io.github.jlmc.rikikivault.core.ports.out.GitRepositoryPort;

/**
 * Shared "push, and never block on failure" step used both right after a fresh local publish
 * ({@code ChangeReviewController}) and when there's nothing new to encrypt but local is already
 * ahead of the remote ({@code MainWindowController}). By the time this runs, whatever needed
 * saving locally already has - so a push failure is always a non-blocking warning, never
 * {@link Dialogs#showError(Throwable)}.
 */
public final class RemotePush {

    private RemotePush() {
    }

    public static void pushInBackground(GitRepositoryPort gitRepositoryPort, Runnable onDone) {
        BackgroundTasks.run(
                gitRepositoryPort::push,
                pushed -> onDone.run(),
                error -> {
                    onDone.run();
                    Dialogs.showWarning(Messages.get("remotePush.failed.title"),
                            Messages.get("remotePush.failed.body", Dialogs.fullMessage(error)));
                });
    }
}
