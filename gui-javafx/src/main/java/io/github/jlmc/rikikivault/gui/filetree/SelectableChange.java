package io.github.jlmc.rikikivault.gui.filetree;

import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/** One pending change in the review list, with a per-row checkbox (Plan.md §17). */
public final class SelectableChange {

    private final VaultChange change;
    private final BooleanProperty selected = new SimpleBooleanProperty(true);

    public SelectableChange(VaultChange change) {
        this.change = change;
    }

    public VaultChange change() {
        return change;
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }
}
