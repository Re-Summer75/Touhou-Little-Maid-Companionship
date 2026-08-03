package com.laixia.maidintelligence.feature.behavior.port;

import com.laixia.maidintelligence.feature.behavior.domain.learning.CompanionLearningProfile;

public interface LearningProfilePort<M> {
    CompanionLearningProfile load(M subject);

    void save(M subject, CompanionLearningProfile profile);
}
