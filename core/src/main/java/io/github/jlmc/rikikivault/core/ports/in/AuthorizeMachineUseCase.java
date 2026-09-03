package io.github.jlmc.rikikivault.core.ports.in;

public interface AuthorizeMachineUseCase {

    boolean authorize(AuthorizeMachineCommand command);
}
