package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.port.IntentContextPort;
import com.laixia.maidintelligence.feature.orchestration.tlm.context.TlmMaidFactReader;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;

import java.util.List;

public final class TlmMaidIntentContext
        implements IntentContextPort<EntityMaid> {
    private final TlmMaidFactReader facts;

    public TlmMaidIntentContext(
            MaidStatusApi<EntityMaid> status,
            TlmMaidIntentObserver observer
    ) {
        this(
                status,
                observer,
                new MaidSnackCabinetMealSource(new MaidMealAccess()),
                new TlmAffordancePerceptionService()
        );
    }

    public TlmMaidIntentContext(
            MaidStatusApi<EntityMaid> status,
            TlmMaidIntentObserver observer,
            MaidSnackCabinetMealSource snackCabinetMeals
    ) {
        this(
                status,
                observer,
                snackCabinetMeals,
                new TlmAffordancePerceptionService()
        );
    }

    public TlmMaidIntentContext(
            MaidStatusApi<EntityMaid> status,
            TlmMaidIntentObserver observer,
            MaidSnackCabinetMealSource snackCabinetMeals,
            TlmAffordancePerceptionService perception
    ) {
        facts = new TlmMaidFactReader(
                status,
                observer,
                snackCabinetMeals,
                perception
        );
    }

    @Override
    public void readFacts(
            EntityMaid maid,
            long gameTime,
            List<OrchestrationId> requestedFacts,
            double[] output
    ) {
        facts.readFacts(maid, gameTime, requestedFacts, output);
    }
}
