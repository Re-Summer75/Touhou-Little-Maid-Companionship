package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.wind.DeterministicWindFieldScenarios;
import com.laixia.maidintelligence.feature.physics.client.wind.SkirtWindIntegrationScenarios;
import com.laixia.maidintelligence.feature.physics.client.wind.SolverWindIntegrationScenarios;
import com.laixia.maidintelligence.feature.physics.client.wind.WeatherWindTimingScenarios;

final class EnvironmentalWindVerification {
    private EnvironmentalWindVerification() {
    }

    static void run() {
        DeterministicWindFieldScenarios.run();
        WeatherWindTimingScenarios.run();
        SolverWindIntegrationScenarios.run();
        SkirtWindIntegrationScenarios.run();
    }
}
