package io.github.jlmc.rikikivault.core.ports.in;

public interface RevokeMachineUseCase {

    void revoke(RevokeMachineCommand command);
}
