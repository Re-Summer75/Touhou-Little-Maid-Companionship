package com.laixia.maidintelligence.gametest;

import com.laixia.maidintelligence.gametest.behavior.AbilityGameTests;
import com.laixia.maidintelligence.gametest.behavior.GazeCommandGameTests;
import com.laixia.maidintelligence.gametest.behavior.NativeBehaviorArbitrationGameTests;
import com.laixia.maidintelligence.gametest.behavior.OwnerCoordinationGameTests;
import com.laixia.maidintelligence.gametest.behavior.VehicleAutonomyGameTests;
import net.minecraftforge.event.RegisterGameTestsEvent;

/**
 * Version-specific GameTest discovery kept outside the distribution root.
 */
public final class GameTestCatalog {
    private GameTestCatalog() {
    }

    public static void register(RegisterGameTestsEvent event) {
        event.register(AdaptiveAiGameTests.class);
        event.register(AbilityGameTests.class);
        event.register(AdvancementGameTests.class);
        event.register(AiOptimizationGameTests.class);
        event.register(GazeCommandGameTests.class);
        event.register(NativeBehaviorArbitrationGameTests.class);
        event.register(OwnerCoordinationGameTests.class);
        event.register(VehicleAutonomyGameTests.class);
        event.register(GazeRecallGameTests.class);
        event.register(HungryOwnerRequestGameTests.class);
        event.register(IntentOrchestrationGameTests.class);
        event.register(LevelGameTests.class);
        event.register(MovementIntentGameTests.class);
        event.register(FreedomTaskGameTests.class);
        event.register(LooseFoodGameTests.class);
        event.register(OwnerAwarenessGameTests.class);
        event.register(OwnerReturnGameTests.class);
        event.register(PassiveFollowGameTests.class);
        event.register(StatusFeedbackGameTests.class);
    }
}
