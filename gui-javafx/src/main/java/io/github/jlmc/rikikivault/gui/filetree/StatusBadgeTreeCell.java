package io.github.jlmc.rikikivault.gui.filetree;

import javafx.scene.control.TreeTableCell;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Function;

/** {@link TreeTableCell} counterpart of {@link StatusBadgeCell} - same rendering, different cell base class. */
public final class StatusBadgeTreeCell<S> extends TreeTableCell<S, S> {

    private final Function<S, FileStatus> statusOf;

    public StatusBadgeTreeCell(Function<S, FileStatus> statusOf) {
        this.statusOf = statusOf;
    }

    @Override
    protected void updateItem(S item, boolean empty) {
        super.updateItem(item, empty);
        getStyleClass().add("status-badge");
        setText(null);
        FileStatus status = empty || item == null ? null : statusOf.apply(item);
        if (status == null) {
            setGraphic(null);
        } else {
            FontIcon icon = new FontIcon(status.iconLiteral());
            icon.setIconSize(13);
            icon.getStyleClass().add(status.styleClass());
            setGraphic(icon);
        }
    }
}
