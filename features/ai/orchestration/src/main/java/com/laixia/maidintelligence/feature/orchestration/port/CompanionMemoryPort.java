package com.laixia.maidintelligence.feature.orchestration.port;

import com.laixia.maidintelligence.feature.orchestration.domain.observation.CompanionPersistentMemory;

public interface CompanionMemoryPort<M> {
    CompanionPersistentMemory load(M subject);

    void save(M subject, CompanionPersistentMemory memory);

    static <M> CompanionMemoryPort<M> noop() {
        return new CompanionMemoryPort<>() {
            @Override
            public CompanionPersistentMemory load(M subject) {
                return CompanionPersistentMemory.initial();
            }

            @Override
            public void save(
                    M subject,
                    CompanionPersistentMemory memory
            ) {
            }
        };
    }
}
