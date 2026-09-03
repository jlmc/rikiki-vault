package io.github.jlmc.rikikivault.core.ports.out;

/** Plan.md §16 - the diff calculation itself must be behind a port, not inline in the UI. */
public interface DiffPort {

    String diff(byte[] previous, byte[] current);
}
