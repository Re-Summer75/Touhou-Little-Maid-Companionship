package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.api.MaidGazeRecallApi;
import com.laixia.maidintelligence.feature.behavior.api.MaidOwnerReturnApi;
import com.laixia.maidintelligence.feature.behavior.domain.GazeRecallPolicy;
import com.laixia.maidintelligence.feature.behavior.handler.OwnerGazeRecallHandler;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Keeps original-behavior wiring out of the distribution composition root.
 */
public record TlmBehaviorComposition(
        MaidGazeRecallApi<Player, EntityMaid> gazeRecall,
        MaidOwnerReturnApi<EntityMaid> ownerReturn,
        OwnerGazeRecallHandler gazeRecallHandler,
        BehaviorTlmModule tlmModule
) {
    public TlmBehaviorComposition {
        Objects.requireNonNull(gazeRecall, "gazeRecall");
        Objects.requireNonNull(ownerReturn, "ownerReturn");
        Objects.requireNonNull(gazeRecallHandler, "gazeRecallHandler");
        Objects.requireNonNull(tlmModule, "tlmModule");
    }

    public static TlmBehaviorComposition create(
            MaidStatusApi<EntityMaid> status,
            Consumer<EntityMaid> hungerRequestAction,
            Supplier<BehaviorTuning> tuning
    ) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(
                hungerRequestAction,
                "hungerRequestAction"
        );
        Objects.requireNonNull(tuning, "tuning");

        MaidGazeRecallApi<Player, EntityMaid> gazeRecall =
                new TlmMaidGazeRecallService(GazeRecallPolicy.defaults());
        OwnerGazeRecallHandler gazeHandler =
                new OwnerGazeRecallHandler(gazeRecall, tuning);
        TlmMaidHungryOwnerRequestService hungryRequest =
                new TlmMaidHungryOwnerRequestService(
                        status,
                        tuning,
                        maid -> maid.getRandom().nextDouble(),
                        hungerRequestAction
                );
        TlmMaidOwnerReturnService ownerReturn =
                new TlmMaidOwnerReturnService(
                        tuning,
                        maid -> maid.getRandom().nextDouble()
                );
        return new TlmBehaviorComposition(
                gazeRecall,
                ownerReturn,
                gazeHandler,
                new BehaviorTlmModule(hungryRequest, ownerReturn)
        );
    }
}
