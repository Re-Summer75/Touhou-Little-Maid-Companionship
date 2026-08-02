package com.laixia.maidintelligence.gametest;

import com.laixia.maidintelligence.gametest.behavior.GazeCommandGameTests;
import net.minecraftforge.event.RegisterGameTestsEvent;

/**
 * Version-specific GameTest discovery kept outside the distribution root.
 */
public final class GameTestCatalog {
    private GameTestCatalog() {
    }

    public static void register(RegisterGameTestsEvent event) {
        event.register(AdaptiveAiGameTests.class);
        event.register(AdvancementGameTests.class);
        event.register(AiOptimizationGameTests.class);
        event.register(GazeCommandGameTests.class);
        event.register(GazeRecallGameTests.class);
        event.register(HungryOwnerRequestGameTests.class);
        event.register(IntentOrchestrationGameTests.class);
        event.register(LevelGameTests.class);
        event.register(MovementIntentGameTests.class);
        event.register(OwnerReturnGameTests.class);
        event.register(PassiveFollowGameTests.class);
        event.register(StatusFeedbackGameTests.class);
    }
}
