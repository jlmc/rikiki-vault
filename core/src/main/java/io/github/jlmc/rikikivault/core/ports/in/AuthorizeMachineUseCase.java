package io.github.jlmc.rikikivault.core.ports.in;

public interface AuthorizeMachineUseCase {

    void authorize(AuthorizeMachineCommand command);
}
