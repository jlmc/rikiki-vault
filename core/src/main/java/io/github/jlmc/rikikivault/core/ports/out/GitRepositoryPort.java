package io.github.jlmc.rikikivault.core.ports.out;

import io.github.jlmc.rikikivault.core.domain.model.GitStatus;

import java.util.List;

public interface GitRepositoryPort {

    void init();

    void clone(String remoteUri);

    void pull();

    GitStatus status();

    void add(List<String> relativePaths);

    void commit(String message);

    void push();

    String diff();
}
