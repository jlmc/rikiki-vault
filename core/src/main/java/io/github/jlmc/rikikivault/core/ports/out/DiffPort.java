package io.github.jlmc.rikikivault.core.ports.out;

/** Port for calculating line-based diffs between previous and current byte content. */
public interface DiffPort {

    String diff(byte[] previous, byte[] current);
}
