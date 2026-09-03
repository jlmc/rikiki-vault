package io.github.jlmc.rikikivault.core.ports.in;

public interface RevokeMachineUseCase {

    boolean revoke(RevokeMachineCommand command);
}
