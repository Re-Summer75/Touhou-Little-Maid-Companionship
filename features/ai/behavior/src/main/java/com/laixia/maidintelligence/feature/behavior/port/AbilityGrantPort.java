package com.laixia.maidintelligence.feature.behavior.port;

import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityGrantSet;

public interface AbilityGrantPort<M> {
    AbilityGrantSet load(M subject);

    void save(M subject, AbilityGrantSet grants);
}
