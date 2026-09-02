package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.domain.model.VaultChange;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

/** One pending change in the review list, with a per-row checkbox (Plan.md §17). */
final class SelectableChange {

    private final VaultChange change;
    private final BooleanProperty selected = new SimpleBooleanProperty(true);

    SelectableChange(VaultChange change) {
        this.change = change;
    }

    VaultChange change() {
        return change;
    }

    BooleanProperty selectedProperty() {
        return selected;
    }

    boolean isSelected() {
        return selected.get();
    }
}
