package io.github.jlmc.rikikivault.gui.filetree;

import javafx.scene.control.TableCell;

import java.util.List;
import java.util.function.Function;

/** Renders a coloured status badge for a table row, given how to extract its {@link FileStatus}. */
public final class StatusBadgeCell<S> extends TableCell<S, S> {

    private static final List<String> STYLE_CLASSES = List.of(
            FileStatus.SYNCED.styleClass(), FileStatus.ADDED.styleClass(),
            FileStatus.MODIFIED.styleClass(), FileStatus.DELETED.styleClass());

    private final Function<S, FileStatus> statusOf;

    public StatusBadgeCell(Function<S, FileStatus> statusOf) {
        this.statusOf = statusOf;
    }

    @Override
    protected void updateItem(S item, boolean empty) {
        super.updateItem(item, empty);
        getStyleClass().removeAll(STYLE_CLASSES);
        getStyleClass().add("status-badge");
        if (empty || item == null) {
            setText(null);
        } else {
            FileStatus status = statusOf.apply(item);
            setText(status.symbol());
            getStyleClass().add(status.styleClass());
        }
    }
}
