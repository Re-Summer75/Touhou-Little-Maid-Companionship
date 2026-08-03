package com.laixia.maidintelligence.feature.behavior.port;

import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceAdvertisement;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceTargetId;

public interface AffordanceIndexPort extends AffordanceQueryPort {
    boolean upsert(AffordanceAdvertisement advertisement);

    boolean remove(AffordanceTargetId target, long revision);

    int size();
}
