package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.ports.out.GitRepositoryPort;

/**
 * Shared "push, and never block on failure" step used both right after a fresh local publish
 * ({@link ChangeReviewController}) and when there's nothing new to encrypt but local is already
 * ahead of the remote ({@link MainWindowController}). By the time this runs, whatever needed
 * saving locally already has - so a push failure is always a non-blocking warning, never
 * {@link Dialogs#showError(Throwable)}.
 */
final class RemotePush {

    private RemotePush() {
    }

    static void pushInBackground(GitRepositoryPort gitRepositoryPort, Runnable onDone) {
        BackgroundTask.run(
                gitRepositoryPort::push,
                pushed -> onDone.run(),
                error -> {
                    onDone.run();
                    Dialogs.showWarning("Não foi possível publicar",
                            "Não foi possível publicar para o remoto: " + Dialogs.fullMessage(error));
                });
    }
}
