package com.laixia.maidintelligence.feature.behavior.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.api.MaidAbilityApi;
import com.laixia.maidintelligence.feature.behavior.api.MaidGazeRecallApi;
import com.laixia.maidintelligence.feature.behavior.api.MaidLearningApi;
import com.laixia.maidintelligence.feature.behavior.application.ability.DefaultMaidAbilityService;
import com.laixia.maidintelligence.feature.behavior.application.ability.MutableAbilityCatalog;
import com.laixia.maidintelligence.feature.behavior.application.learning.DefaultCompanionLearningService;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.handler.AffordancePerceptionHandler;
import com.laixia.maidintelligence.feature.behavior.handler.OwnerGazeRecallHandler;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.application.DefaultMaidIntentOrchestrator;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.application.ShadowingMaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmCompanionMemoryPort;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmCoordinationClaims;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentContext;
import com.laixia.maidintelligence.feature.orchestration.tlm.shadow.TlmMaidIntentShadowActions;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps original-behavior wiring out of the distribution composition root.
 */
@SuppressWarnings("null")
public record TlmBehaviorComposition(
        MaidGazeRecallApi<Player, EntityMaid> gazeRecall,
        MaidIntentApi<EntityMaid> intents,
        MaidAbilityApi<EntityMaid> abilities,
        MaidLearningApi<EntityMaid> learning,
        OwnerGazeRecallHandler gazeRecallHandler,
        AffordancePerceptionHandler perceptionHandler,
        TlmAffordancePerceptionService perception,
        TlmMaidIntentContext context,
        BehaviorTlmModule tlmModule
) {
    private static final int HUNGER_REQUEST_SIGNAL_TTL = 1200;

    public TlmBehaviorComposition {
        Objects.requireNonNull(gazeRecall, "gazeRecall");
        Objects.requireNonNull(intents, "intents");
        Objects.requireNonNull(abilities, "abilities");
        Objects.requireNonNull(learning, "learning");
        Objects.requireNonNull(gazeRecallHandler, "gazeRecallHandler");
        Objects.requireNonNull(perceptionHandler, "perceptionHandler");
        Objects.requireNonNull(perception, "perception");
        Objects.requireNonNull(tlmModule, "tlmModule");
    }

    public static TlmBehaviorComposition create(
            MaidStatusApi<EntityMaid> status,
            Consumer<EntityMaid> hungerRequestAction,
            Supplier<BehaviorTuning> tuning,
            MutableIntentCatalog catalog,
            MutableAbilityCatalog abilityCatalog
    ) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(
                hungerRequestAction,
                "hungerRequestAction"
        );
        Objects.requireNonNull(tuning, "tuning");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(abilityCatalog, "abilityCatalog");

        AtomicReference<MaidIntentApi<EntityMaid>> intentReference =
                new AtomicReference<>();
        MaidAbilityApi<EntityMaid> abilities =
                new DefaultMaidAbilityService<>(
                        abilityCatalog,
                        new TlmAbilityGrantPort(),
                        (maid, signal, gameTime, ttlTicks) -> {
                            MaidIntentApi<EntityMaid> current =
                                    intentReference.get();
                            return current != null && current.signal(
                                    maid,
                                    signal,
                                    gameTime,
                                    ttlTicks
                            );
                        }
                );
        DefaultCompanionLearningService<EntityMaid> learning =
                new DefaultCompanionLearningService<>(
                        new TlmLearningProfilePort(),
                        () -> current(tuning).learningMode()
                );
        TlmAffordancePerceptionService perception =
                new TlmAffordancePerceptionService();
        MaidCommandSeatBridge.bindPerception(perception);
        MaidSnackCabinetMealSource snackCabinetMeals =
                new MaidSnackCabinetMealSource(
                        new MaidMealAccess(),
                        perception
                );
        TlmMaidIntentContext context =
                new TlmMaidIntentContext(
                        status,
                        snackCabinetMeals,
                        perception
                );
        TlmMaidIntentActions actions =
                new TlmMaidIntentActions(
                        hungerRequestAction,
                        snackCabinetMeals,
                        abilities
                );
        MaidIntentApi<EntityMaid> live =
                new DefaultMaidIntentOrchestrator<>(
                        catalog,
                        context,
                        actions,
                        EntityMaid::getId,
                        () -> current(tuning).enabled(),
                        () -> current(tuning).evaluationIntervalTicks(),
                        () -> current(tuning).maxCandidateEvaluations(),
                        () -> current(tuning).diagnosticsEnabled(),
                        new TlmCompanionMemoryPort(),
                        learning,
                        learning
                );
        MaidIntentApi<EntityMaid> shadow =
                new DefaultMaidIntentOrchestrator<>(
                        catalog,
                        context,
                        new TlmMaidIntentShadowActions(snackCabinetMeals),
                        EntityMaid::getId,
                        () -> current(tuning).enabled(),
                        () -> current(tuning).evaluationIntervalTicks(),
                        () -> current(tuning).maxCandidateEvaluations(),
                        () -> current(tuning).diagnosticsEnabled()
                );
        MaidIntentApi<EntityMaid> intents =
                new ShadowingMaidIntentApi<>(
                        live,
                        shadow,
                        () -> current(tuning).rolloutMode()
                );
        intentReference.set(intents);
        TlmDeployBoatAutonomy boatAutonomy =
                new TlmDeployBoatAutonomy(abilities);
        MaidGazeRecallApi<Player, EntityMaid> gazeRecall =
                new TlmMaidGazeRecallService(intents);
        OwnerGazeRecallHandler gazeHandler =
                new OwnerGazeRecallHandler(gazeRecall, tuning);
        return new TlmBehaviorComposition(
                gazeRecall,
                intents,
                abilities,
                learning,
                gazeHandler,
                new AffordancePerceptionHandler(perception),
                perception,
                context,
                new BehaviorTlmModule(
                        intents,
                        boatAutonomy
                )
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

    public void reloadCoordination() {
        TlmCoordinationClaims.reload();
        TlmOwnerCoordinationGroups.reload();
    }

    private static BehaviorTuning current(
            Supplier<BehaviorTuning> tuning
    ) {
        BehaviorTuning current = tuning.get();
        return current == null ? BehaviorTuning.defaults() : current;
    }
}
