package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.ports.out.DiffPort;

final class FakeDiffPort implements DiffPort {

    byte[] receivedPrevious;
    byte[] receivedCurrent;
    String resultToReturn = "fake diff result";

    @Override
    public String diff(byte[] previous, byte[] current) {
        receivedPrevious = previous;
        receivedCurrent = current;
        return resultToReturn;
    }
}
