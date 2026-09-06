package io.github.jlmc.rikikivault.gui.filetree;

import javafx.scene.control.TableCell;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Function;

/** Renders a coloured status badge for a table row, given how to extract its {@link FileStatus}. */
public final class StatusBadgeCell<S> extends TableCell<S, S> {

    private final Function<S, FileStatus> statusOf;

    public StatusBadgeCell(Function<S, FileStatus> statusOf) {
        this.statusOf = statusOf;
    }

    @Override
    protected void updateItem(S item, boolean empty) {
        super.updateItem(item, empty);
        getStyleClass().add("status-badge");
        setText(null);
        if (empty || item == null) {
            setGraphic(null);
        } else {
            FileStatus status = statusOf.apply(item);
            FontIcon icon = new FontIcon(status.iconLiteral());
            icon.setIconSize(13);
            icon.getStyleClass().add(status.styleClass());
            setGraphic(icon);
        }
    }
}
