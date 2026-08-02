package com.laixia.maidintelligence.feature.orchestration.port;

import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;

@FunctionalInterface
public interface IntentCatalogPort {
    IntentCatalog current();
}
