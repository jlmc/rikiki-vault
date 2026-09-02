package io.github.jlmc.rikikivault.gui;

import io.github.jlmc.rikikivault.core.application.usecase.LoadMachineIdentityService;
import io.github.jlmc.rikikivault.core.domain.model.MachineIdentity;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class MainWindowController {

    @FXML private Label titleLabel;
    @FXML private Label fingerprintLabel;

    void init(VaultContext ctx) {
        MachineIdentity identity = new LoadMachineIdentityService(ctx.keyStorePort()).load();
        titleLabel.setText("Vault aberto: " + ctx.vaultRoot());
        fingerprintLabel.setText("Identidade desta máquina: " + identity.id());
    }
}
