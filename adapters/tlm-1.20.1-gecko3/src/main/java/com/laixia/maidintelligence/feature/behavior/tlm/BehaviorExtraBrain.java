package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.entity.ai.IExtraMaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.tlm.MaidIntentBehavior;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentObserver;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.List;
import java.util.Objects;

public final class BehaviorExtraBrain implements IExtraMaidBrain {
    private static final int INTENT_PRIORITY = 4;

    private final MaidIntentApi<EntityMaid> intents;
    private final TlmMaidIntentObserver observer;
    private final TlmDeployBoatAutonomy boatAutonomy;

    public BehaviorExtraBrain(
            MaidIntentApi<EntityMaid> intents,
            TlmMaidIntentObserver observer,
            TlmDeployBoatAutonomy boatAutonomy
    ) {
        this.intents = Objects.requireNonNull(
                intents,
                "intents"
        );
        this.observer = Objects.requireNonNull(
                observer,
                "observer"
        );
        this.boatAutonomy = Objects.requireNonNull(
                boatAutonomy,
                "boatAutonomy"
        );
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>>
    getCoreBehaviors() {
        return List.of(
                Pair.of(
                        INTENT_PRIORITY,
                        new MaidIntentBehavior(
                                intents,
                                observer,
                                boatAutonomy::tick
                        )
                )
        );
    }
}
