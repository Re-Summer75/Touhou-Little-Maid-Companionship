package com.laixia.maidintelligence.feature.behavior.api;

import com.laixia.maidintelligence.feature.behavior.domain.learning.CompanionLearningProfile;
import com.laixia.maidintelligence.feature.behavior.domain.learning.LearningMode;

public interface MaidLearningApi<M> {
    LearningMode mode();

    CompanionLearningProfile profile(M subject);

    boolean freeze(M subject, boolean frozen);

    void reset(M subject);

    String export(M subject);
}
