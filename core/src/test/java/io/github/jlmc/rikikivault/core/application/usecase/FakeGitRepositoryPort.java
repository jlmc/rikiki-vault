package io.github.jlmc.rikikivault.core.application.usecase;

import io.github.jlmc.rikikivault.core.domain.model.GitStatus;
import io.github.jlmc.rikikivault.core.ports.out.GitRepositoryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class FakeGitRepositoryPort implements GitRepositoryPort {

    int initCallCount = 0;
    String clonedRemoteUri;
    int pullCallCount = 0;
    int pushCallCount = 0;
    boolean pushReturnValue = true;
    final List<List<String>> addedPathBatches = new ArrayList<>();
    final List<String> commitMessages = new ArrayList<>();
    GitStatus statusToReturn = new GitStatus(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    String diffToReturn = "";

    @Override
    public void init() {
        initCallCount++;
    }

    @Override
    public void clone(String remoteUri) {
        clonedRemoteUri = remoteUri;
    }

    @Override
    public void pull() {
        pullCallCount++;
    }

    @Override
    public GitStatus status() {
        return statusToReturn;
    }

    @Override
    public void add(List<String> relativePaths) {
        addedPathBatches.add(List.copyOf(relativePaths));
    }

    @Override
    public void commit(String message) {
        commitMessages.add(message);
    }

    @Override
    public boolean push() {
        pushCallCount++;
        return pushReturnValue;
    }

    @Override
    public String diff() {
        return diffToReturn;
    }
}
