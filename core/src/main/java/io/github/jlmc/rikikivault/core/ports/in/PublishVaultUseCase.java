package io.github.jlmc.rikikivault.core.ports.in;

public interface PublishVaultUseCase {

    boolean publish(PublishVaultCommand command);
}
