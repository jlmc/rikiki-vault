package io.github.jlmc.rikikivault.gui;

import javafx.scene.control.TreeTableCell;

import java.util.List;
import java.util.function.Function;

/** {@link TreeTableCell} counterpart of {@link StatusBadgeCell} - same rendering, different cell base class. */
final class StatusBadgeTreeCell<S> extends TreeTableCell<S, S> {

    private static final List<String> STYLE_CLASSES = List.of(
            FileStatus.SYNCED.styleClass(), FileStatus.ADDED.styleClass(),
            FileStatus.MODIFIED.styleClass(), FileStatus.DELETED.styleClass());

    private final Function<S, FileStatus> statusOf;

    StatusBadgeTreeCell(Function<S, FileStatus> statusOf) {
        this.statusOf = statusOf;
    }

    @Override
    protected void updateItem(S item, boolean empty) {
        super.updateItem(item, empty);
        getStyleClass().removeAll(STYLE_CLASSES);
        getStyleClass().add("status-badge");
        FileStatus status = empty || item == null ? null : statusOf.apply(item);
        if (status == null) {
            setText(null);
        } else {
            setText(status.symbol());
            getStyleClass().add(status.styleClass());
        }
    }
}
