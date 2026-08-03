package com.laixia.maidintelligence.feature.behavior.port;

import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordanceQuery;

import java.util.List;

public interface AffordanceQueryPort {
    List<AffordanceCandidate> query(AffordanceQuery query);
}
