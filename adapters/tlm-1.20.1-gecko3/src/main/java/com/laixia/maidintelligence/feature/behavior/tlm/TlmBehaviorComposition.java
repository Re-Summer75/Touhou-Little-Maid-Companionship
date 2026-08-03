package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.api.MaidGazeRecallApi;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.handler.OwnerGazeRecallHandler;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.application.DefaultMaidIntentOrchestrator;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentContext;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentObserver;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Keeps original-behavior wiring out of the distribution composition root.
 */
@SuppressWarnings("null")
public record TlmBehaviorComposition(
        MaidGazeRecallApi<Player, EntityMaid> gazeRecall,
        MaidIntentApi<EntityMaid> intents,
        OwnerGazeRecallHandler gazeRecallHandler,
        BehaviorTlmModule tlmModule
) {
    private static final int HUNGER_REQUEST_SIGNAL_TTL = 1200;

    public TlmBehaviorComposition {
        Objects.requireNonNull(gazeRecall, "gazeRecall");
        Objects.requireNonNull(intents, "intents");
        Objects.requireNonNull(gazeRecallHandler, "gazeRecallHandler");
        Objects.requireNonNull(tlmModule, "tlmModule");
    }

    public static TlmBehaviorComposition create(
            MaidStatusApi<EntityMaid> status,
            Consumer<EntityMaid> hungerRequestAction,
            Supplier<BehaviorTuning> tuning,
            MutableIntentCatalog catalog
    ) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(
                hungerRequestAction,
                "hungerRequestAction"
        );
        Objects.requireNonNull(tuning, "tuning");
        Objects.requireNonNull(catalog, "catalog");

        TlmMaidIntentObserver observer = new TlmMaidIntentObserver();
        MaidSnackCabinetMealSource snackCabinetMeals =
                new MaidSnackCabinetMealSource(new MaidMealAccess());
        TlmMaidIntentContext context =
                new TlmMaidIntentContext(
                        status,
                        observer,
                        snackCabinetMeals
                );
        TlmMaidIntentActions actions =
                new TlmMaidIntentActions(
                        hungerRequestAction,
                        snackCabinetMeals
                );
        MaidIntentApi<EntityMaid> intents =
                new DefaultMaidIntentOrchestrator<>(
                        catalog,
                        context,
                        actions,
                        EntityMaid::getId,
                        () -> current(tuning).enabled(),
                        () -> current(tuning).evaluationIntervalTicks(),
                        () -> current(tuning).maxCandidateEvaluations(),
                        () -> current(tuning).diagnosticsEnabled()
                );
        observer.bind(intents);
        MaidGazeRecallApi<Player, EntityMaid> gazeRecall =
                new TlmMaidGazeRecallService(intents);
        OwnerGazeRecallHandler gazeHandler =
                new OwnerGazeRecallHandler(gazeRecall, tuning);
        return new TlmBehaviorComposition(
                gazeRecall,
                intents,
                gazeHandler,
                new BehaviorTlmModule(intents, observer)
        );
    }

    public boolean signalHungerRequest(EntityMaid maid) {
        Objects.requireNonNull(maid, "maid");
        return intents.signal(
                maid,
                CompanionIntentIds.HUNGER_REQUEST,
                maid.level().getGameTime(),
                HUNGER_REQUEST_SIGNAL_TTL
        );
    }

    private static BehaviorTuning current(
            Supplier<BehaviorTuning> tuning
    ) {
        BehaviorTuning current = tuning.get();
        return current == null ? BehaviorTuning.defaults() : current;
    }
}
