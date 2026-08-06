package com.laixia.maidintelligence;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.compat.tlm.TlmAdapterRegistry;
import com.laixia.maidintelligence.feature.ai.api.MaidAiOptimizationApi;
import com.laixia.maidintelligence.feature.ai.api.MaidMovementCoordinationApi;
import com.laixia.maidintelligence.feature.ai.api.MovementCoordinationMode;
import com.laixia.maidintelligence.feature.ai.application.DefaultMaidAiOptimizationService;
import com.laixia.maidintelligence.feature.ai.application.DefaultMaidMovementCoordinationService;
import com.laixia.maidintelligence.feature.ai.command.MaidAiCommands;
import com.laixia.maidintelligence.feature.ai.forge.AiForgeInstaller;
import com.laixia.maidintelligence.feature.ai.forge.AiServerConfig;
import com.laixia.maidintelligence.feature.ai.handler.MaidRideAutonomyHandler;
import com.laixia.maidintelligence.feature.advancement.api.MaidStatisticsApi;
import com.laixia.maidintelligence.feature.advancement.application.DefaultMaidStatisticsService;
import com.laixia.maidintelligence.feature.advancement.bridge.HoneySlideHandler;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidAdvancementAccess;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidCombatAdvancementTriggers;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidProgressAdvancementTriggers;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidWorldAdvancementTriggers;
import com.laixia.maidintelligence.feature.advancement.client.AdvancementClientSetup;
import com.laixia.maidintelligence.feature.advancement.client.TlmAdvancementClientRuntime;
import com.laixia.maidintelligence.feature.advancement.event.MaidAdvancementExperienceRewardedEvent;
import com.laixia.maidintelligence.feature.advancement.forge.AdvancementForgeInstaller;
import com.laixia.maidintelligence.feature.advancement.handler.MaidAdvancementBridgeHandlers;
import com.laixia.maidintelligence.feature.advancement.handler.MaidAdvancementLifecycleHandlers;
import com.laixia.maidintelligence.feature.advancement.menu.AdvancementMenuFactory;
import com.laixia.maidintelligence.feature.advancement.menu.AdvancementMenus;
import com.laixia.maidintelligence.feature.advancement.menu.MaidAdvancementContainer;
import com.laixia.maidintelligence.feature.advancement.network.AdvancementPacketRegistrar;
import com.laixia.maidintelligence.feature.advancement.network.ForgeAdvancementPacketHandler;
import com.laixia.maidintelligence.feature.advancement.network.MaidAdvancementServerPacketHandler;
import com.laixia.maidintelligence.feature.advancement.port.MaidCourtshipMemory;
import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementManager;
import com.laixia.maidintelligence.feature.advancement.server.MaidBridgeMemory;
import com.laixia.maidintelligence.feature.advancement.server.MaidProgressCriteria;
import com.laixia.maidintelligence.feature.advancement.server.MaidVanillaCombatCriteria;
import com.laixia.maidintelligence.feature.advancement.server.MaidVanillaWorldCriteria;
import com.laixia.maidintelligence.feature.advancement.tlm.AdvancementTlmModule;
import com.laixia.maidintelligence.feature.advancement.tlm.MaidStatisticsData;
import com.laixia.maidintelligence.feature.advancement.tlm.TlmAdvancementRequestResolver;
import com.laixia.maidintelligence.feature.advancement.tlm.TlmHoneySlideHandler;
import com.laixia.maidintelligence.feature.atmosphere.forge.AtmosphereForgeInstaller;
import com.laixia.maidintelligence.feature.behavior.api.MaidGazeRecallApi;
import com.laixia.maidintelligence.feature.behavior.application.ability.AbilityTemplateCompiler;
import com.laixia.maidintelligence.feature.behavior.application.ability.MutableAbilityCatalog;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.forge.BehaviorForgeInstaller;
import com.laixia.maidintelligence.feature.behavior.forge.BehaviorServerConfig;
import com.laixia.maidintelligence.feature.behavior.tlm.TlmBehaviorComposition;
import com.laixia.maidintelligence.feature.behavior.tlm.TlmEntityAbilityFacade;
import com.laixia.maidintelligence.feature.behavior.tlm.TlmEntityLearningFacade;
import com.laixia.maidintelligence.feature.interaction.bridge.EatingParticlePolicy;
import com.laixia.maidintelligence.feature.interaction.client.ClientInteractionSetup;
import com.laixia.maidintelligence.feature.interaction.client.runtime.TlmInteractionClientPacketHandler;
import com.laixia.maidintelligence.feature.interaction.client.runtime.TlmInteractionClientRuntime;
import com.laixia.maidintelligence.feature.interaction.event.MaidFedEvent;
import com.laixia.maidintelligence.feature.interaction.forge.InteractionConfig;
import com.laixia.maidintelligence.feature.interaction.forge.InteractionForgeInstaller;
import com.laixia.maidintelligence.feature.interaction.handler.MaidAutomaticEatingParticleHandler;
import com.laixia.maidintelligence.feature.interaction.handler.MaidDirectItemInteractionHandler;
import com.laixia.maidintelligence.feature.interaction.handler.MaidInteractionHandler;
import com.laixia.maidintelligence.feature.interaction.network.InteractionClientPacketHandler;
import com.laixia.maidintelligence.feature.interaction.network.InteractionPacketRegistrar;
import com.laixia.maidintelligence.feature.interaction.port.MaidFeedingStatusPort;
import com.laixia.maidintelligence.feature.interaction.port.MaidMouthFeedPort;
import com.laixia.maidintelligence.feature.interaction.service.MaidFeedingService;
import com.laixia.maidintelligence.feature.interaction.service.MaidMouthFeedRequestHandler;
import com.laixia.maidintelligence.feature.interaction.tlm.InteractionTlmModule;
import com.laixia.maidintelligence.feature.interaction.tlm.TlmEatingParticlePolicy;
import com.laixia.maidintelligence.feature.level.api.ExperienceSource;
import com.laixia.maidintelligence.feature.level.api.MaidLevelApi;
import com.laixia.maidintelligence.feature.level.application.DefaultMaidLevelService;
import com.laixia.maidintelligence.feature.level.client.LevelGuiHandler;
import com.laixia.maidintelligence.feature.level.command.LevelCommands;
import com.laixia.maidintelligence.feature.level.domain.DefaultLevelCurve;
import com.laixia.maidintelligence.feature.level.event.MaidLevelChangedEvent;
import com.laixia.maidintelligence.feature.level.forge.LevelForgeInstaller;
import com.laixia.maidintelligence.feature.level.handler.LevelExperienceHandler;
import com.laixia.maidintelligence.feature.level.network.LevelNetwork;
import com.laixia.maidintelligence.feature.level.network.LevelPacketRegistrar;
import com.laixia.maidintelligence.feature.orchestration.insight.InsightForgeInstaller;
import com.laixia.maidintelligence.feature.orchestration.insight.InsightPacketRegistrar;
import com.laixia.maidintelligence.feature.orchestration.tlm.insight.TlmMaidInsightSource;
import com.laixia.maidintelligence.feature.level.tlm.LevelTlmModule;
import com.laixia.maidintelligence.feature.level.tlm.TlmEntityLevelFacade;
import com.laixia.maidintelligence.feature.level.tlm.TlmMaidLevelStore;
import com.laixia.maidintelligence.feature.orchestration.api.MaidIntentApi;
import com.laixia.maidintelligence.feature.orchestration.application.MutableIntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.data.MaidIntentReloadListener;
import com.laixia.maidintelligence.feature.orchestration.forge.OrchestrationForgeInstaller;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmEntityIntentFacade;
import com.laixia.maidintelligence.feature.physics.PhysicsDebugCommands;
import com.laixia.maidintelligence.feature.physics.client.ClientPhysicsSetup;
import com.laixia.maidintelligence.feature.physics.forge.PhysicsClientConfig;
import com.laixia.maidintelligence.feature.physics.forge.PhysicsForgeInstaller;
import com.laixia.maidintelligence.feature.physics.tlm.PhysicsTlmModule;
import com.laixia.maidintelligence.feature.shading.client.ShadingCacheInvalidator;
import com.laixia.maidintelligence.feature.shading.client.ShadingClientSetup;
import com.laixia.maidintelligence.feature.shading.client.TlmShadingCacheInvalidator;
import com.laixia.maidintelligence.feature.shading.forge.ShadingForgeInstaller;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.api.MaidStatusFeedbackApi;
import com.laixia.maidintelligence.feature.status.client.MaidHungerGuiHandler;
import com.laixia.maidintelligence.feature.status.domain.DefaultHungerPolicy;
import com.laixia.maidintelligence.feature.status.domain.DefaultToolDurabilityPolicy;
import com.laixia.maidintelligence.feature.status.forge.StatusForgeInstaller;
import com.laixia.maidintelligence.feature.status.handler.MaidFoodStatusHandler;
import com.laixia.maidintelligence.feature.status.handler.MaidHungerRegenerationHandler;
import com.laixia.maidintelligence.feature.status.service.MaidActionService;
import com.laixia.maidintelligence.feature.status.service.MaidExpressionService;
import com.laixia.maidintelligence.feature.status.service.ToolReplacementService;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.StatusTlmModule;
import com.laixia.maidintelligence.feature.status.tlm.TlmMaidStatusService;
import com.laixia.maidintelligence.feature.status.tlm.TlmMaidStatusStore;
import com.laixia.maidintelligence.kernel.event.DomainEventBus;
import com.laixia.maidintelligence.kernel.service.MutableServiceRegistry;
import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.item.ModItems;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import com.laixia.maidintelligence.platform.network.ModNetwork;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.List;

/**
 * Distribution composition root. Feature applications and both version
 * adapter families are assembled explicitly here.
 */
@Mod(ModResources.MOD_ID)
public final class MaidIntelligence {
    public static final String MOD_ID = ModResources.MOD_ID;

    public MaidIntelligence() {
        IEventBus modEventBus =
                FMLJavaModLoadingContext.get().getModEventBus();
        ForgeLifecycle forge = new ForgeLifecycle(
                modEventBus,
                MinecraftForge.EVENT_BUS
        );
        DomainEventBus events = new DomainEventBus();
        MaidAiOptimizationApi aiOptimization =
                new DefaultMaidAiOptimizationService(
                        AiServerConfig::tuning
                );
        MaidMovementCoordinationApi movementCoordination =
                new DefaultMaidMovementCoordinationService(
                        () -> AiServerConfig.isEnabled()
                                ? AiServerConfig.movementCoordinationMode()
                                : MovementCoordinationMode.OFF,
                        AiServerConfig::movementLeaseTicks,
                        AiServerConfig::pickupCommitmentTicks,
                        AiServerConfig::movementFailOpenTicks
                );

        TlmMaidStatusService statusService = createStatusService();
        MaidStatusApi<EntityMaid> statusApi = statusService;
        MaidLevelApi<EntityMaid> levelApi = new DefaultMaidLevelService<>(
                new TlmMaidLevelStore(),
                DefaultLevelCurve.INSTANCE,
                (maid, oldLevel, newLevel) -> events.publish(
                        new MaidLevelChangedEvent<>(
                                maid,
                                oldLevel,
                                newLevel
                        )
                )
        );

        MaidStatisticsData statisticsData = new MaidStatisticsData();
        MaidStatisticsApi<EntityMaid> statistics =
                new DefaultMaidStatisticsService<>(statisticsData);
        MaidBridgeMemory bridgeMemory = new MaidBridgeMemory();
        MaidAdvancementManager advancementManager =
                new MaidAdvancementManager(
                        (maid, points) -> events.publish(
                                new MaidAdvancementExperienceRewardedEvent<>(
                                        maid,
                                        points
                                )
                        ),
                        statistics
                );
        MaidWorldAdvancementTriggers worldAdvancementTriggers =
                new MaidVanillaWorldCriteria(advancementManager);
        MaidCombatAdvancementTriggers combatAdvancementTriggers =
                new MaidVanillaCombatCriteria(advancementManager);
        MaidProgressAdvancementTriggers progressAdvancementTriggers =
                new MaidProgressCriteria(advancementManager, statistics);
        TlmAdvancementRequestResolver advancementRequests =
                new TlmAdvancementRequestResolver(advancementManager);
        MaidFeedingService feeding = new MaidFeedingService(
                feedingStatusPort(statusService),
                events
        );
        MaidMouthFeedPort<ServerPlayer> mouthFeed =
                new MaidMouthFeedRequestHandler(feeding);
        MutableIntentCatalog intentCatalog = new MutableIntentCatalog();
        MutableAbilityCatalog abilityCatalog = new MutableAbilityCatalog();
        TlmBehaviorComposition behaviors = TlmBehaviorComposition.create(
                statusApi,
                statusService::requestHungerAttention,
                BehaviorServerConfig::tuning,
                intentCatalog,
                abilityCatalog
        );
        statusService.bindHungerRequestSignal(behaviors::signalHungerRequest);
        MaidIntentApi<Entity> commandIntents =
                new TlmEntityIntentFacade(behaviors.intents());
        var commandAbilities = new TlmEntityAbilityFacade(behaviors.abilities());
        DomainEventWiring.wire(events, progressAdvancementTriggers, levelApi);
        MutableServiceRegistry services = new MutableServiceRegistry()
                .register(MaidAiOptimizationApi.class, aiOptimization)
                .register(
                        MaidMovementCoordinationApi.class,
                        movementCoordination
                )
                .register(
                        MaidGazeRecallApi.class,
                        behaviors.gazeRecall()
                )
                .register(MaidIntentApi.class, behaviors.intents())
                .register(MaidLevelApi.class, levelApi)
                .register(MaidStatusApi.class, statusApi)
                .register(MaidStatusFeedbackApi.class, statusService)
                .register(
                        MaidWorldAdvancementTriggers.class,
                        worldAdvancementTriggers
                )
                .register(
                        MaidCombatAdvancementTriggers.class,
                        combatAdvancementTriggers
                )
                .register(
                        MaidProgressAdvancementTriggers.class,
                        progressAdvancementTriggers
                )
                .register(
                        MaidAdvancementAccess.class,
                        advancementManager
                )
                .register(MaidCourtshipMemory.class, bridgeMemory)
                .register(MaidMouthFeedPort.class, mouthFeed)
                .register(
                        AdvancementMenuFactory.class,
                        (windowId, inventory, data) ->
                                new MaidAdvancementContainer(
                                        windowId,
                                        inventory,
                                        data.readInt()
                                )
                )
                .register(
                        MaidAdvancementServerPacketHandler.class,
                        new ForgeAdvancementPacketHandler(
                                advancementRequests::resolveOpenPage,
                                advancementRequests::resolveSnapshot,
                                MaidAdvancementContainer::create
                        )
                )
                .register(
                        HoneySlideHandler.class,
                        new TlmHoneySlideHandler(worldAdvancementTriggers)
                )
                .register(
                        EatingParticlePolicy.class,
                        new TlmEatingParticlePolicy()
                );
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> services
                        .register(
                                InteractionClientPacketHandler.class,
                                new TlmInteractionClientPacketHandler()
                        )
                        .register(
                                ShadingCacheInvalidator.class,
                                new TlmShadingCacheInvalidator()
                        )
        );
        AdapterRuntime.install(services.freeze());

        TlmAdapterRegistry.install(List.of(
                new LevelTlmModule(),
                new AdvancementTlmModule(statisticsData),
                new InteractionTlmModule(feeding),
                new PhysicsTlmModule(),
                new StatusTlmModule(statusService),
                behaviors.tlmModule()
        ));

        ModNetwork.initialize(List.of(
                new LevelPacketRegistrar(),
                new InteractionPacketRegistrar(),
                new AdvancementPacketRegistrar(),
                new InsightPacketRegistrar()
        ));

        List<ForgeFeatureInstaller> forgeFeatures = List.of(
                new ModItems(),
                new InsightForgeInstaller(new TlmMaidInsightSource(
                        behaviors.intents(),
                        behaviors.context()
                )),
                new AiForgeInstaller(
                        new MaidAiCommands(
                                aiOptimization,
                                movementCoordination,
                                commandIntents,
                                commandAbilities,
                                new TlmEntityLearningFacade(behaviors.learning()),
                                EntityMaid.class::isInstance,
                                () -> BehaviorServerConfig.tuning()
                                        .diagnosticsEnabled()
                        )::onRegisterCommands,
                        new MaidRideAutonomyHandler()
                ),
                new BehaviorForgeInstaller(
                        behaviors.gazeRecallHandler()::onPlayerTick,
                        behaviors.gazeRecallHandler()::onPlayerLogout,
                        behaviors.perceptionHandler()::onChunkLoad,
                        behaviors.perceptionHandler()::onChunkUnload,
                        behaviors.perceptionHandler()::onBlockPlace,
                        behaviors.perceptionHandler()::onBlockBreak,
                        behaviors.perceptionHandler()::onEntityJoin,
                        behaviors.perceptionHandler()::onEntityLeave,
                        behaviors.perceptionHandler()::onLevelUnload
                ),
                new OrchestrationForgeInstaller(
                        new MaidIntentReloadListener(
                                intentCatalog,
                                CompanionIntentIds.vocabulary(),
                                abilityCatalog,
                                new AbilityTemplateCompiler(),
                                behaviors::reloadCoordination
                        ),
                        commandIntents::forget
                ),
                new LevelForgeInstaller(
                        new LevelExperienceHandler(levelApi),
                        new LevelCommands(
                                new TlmEntityLevelFacade(levelApi),
                                EntityMaid.class::isInstance
                        )::onRegisterCommands,
                        () -> new LevelGuiHandler(levelApi)
                ),
                new AdvancementForgeInstaller(
                        AdvancementMenus::register,
                        new MaidAdvancementLifecycleHandlers(
                                advancementManager,
                                bridgeMemory
                        ),
                        new MaidAdvancementBridgeHandlers(
                                bridgeMemory,
                                worldAdvancementTriggers,
                                combatAdvancementTriggers,
                                progressAdvancementTriggers,
                                maid -> levelApi.getProgress(maid).level()
                        ),
                        () -> new AdvancementClientSetup(
                                TlmAdvancementClientRuntime.createGuiHandler(),
                                TlmAdvancementClientRuntime::registerScreen
                        )
                ),
                new InteractionForgeInstaller(
                        List.of(
                                new MaidInteractionHandler(
                                        InteractionConfig::usesCustomKeys
                                ),
                                new MaidDirectItemInteractionHandler(
                                        feeding
                                ),
                                new MaidAutomaticEatingParticleHandler()
                        ),
                        () -> new ClientInteractionSetup(
                                TlmInteractionClientRuntime::reloadResources,
                                TlmInteractionClientRuntime::endClientTick
                        )
                ),
                new ShadingForgeInstaller(
                        () -> ShadingClientSetup::initialize
                ),
                new PhysicsForgeInstaller(
                        PhysicsDebugCommands::onRegisterCommands,
                        () -> lifecycle ->
                                ClientPhysicsSetup.initialize(
                                        lifecycle.modEventBus(),
                                        lifecycle.gameEventBus(),
                                        PhysicsClientConfig::isEnabled
                                )
                ),
                new AtmosphereForgeInstaller(),
                new StatusForgeInstaller(
                        new MaidFoodStatusHandler(statusService),
                        new MaidHungerRegenerationHandler(
                                statusService
                        ),
                        () -> new MaidHungerGuiHandler(statusService)
                )
        );
        forgeFeatures.forEach(feature -> feature.install(forge));
    }

    private static TlmMaidStatusService createStatusService() {
        return new TlmMaidStatusService(
                new TlmMaidStatusStore(),
                DefaultHungerPolicy.INSTANCE,
                new ToolReplacementService(
                        DefaultToolDurabilityPolicy.INSTANCE
                ),
                new MaidMealAccess(),
                new MaidExpressionService(),
                new MaidActionService()
        );
    }


    private static MaidFeedingStatusPort<EntityMaid, ItemStack>
    feedingStatusPort(TlmMaidStatusService status) {
        return new MaidFeedingStatusPort<>() {
            @Override
            public boolean isSaturationFull(EntityMaid maid) {
                return status.isSaturationFull(maid);
            }

            @Override
            public void captureFoodNutrition(
                    EntityMaid maid,
                    ItemStack food
            ) {
                status.captureFoodNutrition(maid, food);
            }

            @Override
            public void restoreFromFood(
                    EntityMaid maid,
                    int nutrition,
                    float saturationModifier
            ) {
                status.restoreFromFood(
                        maid,
                        nutrition,
                        saturationModifier
                );
            }
        };
    }

}
