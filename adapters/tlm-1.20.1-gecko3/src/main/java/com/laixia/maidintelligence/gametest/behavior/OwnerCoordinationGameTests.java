package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.domain.ability.CompanionAbilityIds;
import com.laixia.maidintelligence.feature.behavior.tlm.TlmOwnerCoordinationGroups;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class OwnerCoordinationGameTests {
    private OwnerCoordinationGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void twoMaidsElectOneBoatRequestResponder(
            GameTestHelper helper
    ) {
        Player owner = helper.makeMockPlayer();
        owner.setPos(2.5D, 2.0D, 1.5D);
        EntityMaid first = maid(helper, owner, new BlockPos(2, 2, 2));
        EntityMaid second = maid(helper, owner, new BlockPos(2, 2, 3));
        long gameTime = helper.getLevel().getGameTime();

        TlmOwnerCoordinationGroups.Decision firstDecision =
                TlmOwnerCoordinationGroups.decide(
                        first,
                        CompanionAbilityIds.DEPLOY_BOAT,
                        500,
                        1,
                        100,
                        gameTime,
                        candidate -> true,
                        candidate -> 120.0D
                );
        TlmOwnerCoordinationGroups.Decision secondDecision =
                TlmOwnerCoordinationGroups.decide(
                        second,
                        CompanionAbilityIds.DEPLOY_BOAT,
                        500,
                        1,
                        100,
                        gameTime,
                        candidate -> true,
                        candidate -> 120.0D
                );
        helper.assertTrue(
                firstDecision.requestId().equals(
                        secondDecision.requestId()
                ),
                "Owner group did not share one boat request identity"
        );
        helper.assertTrue(
                firstDecision.assigned() != secondDecision.assigned(),
                "Boat request did not elect exactly one responder"
        );
        helper.assertTrue(
                firstDecision.responders().size() == 1
                        && firstDecision.responders().equals(
                        secondDecision.responders()
                ),
                "Sequential maid ticks observed different assignments"
        );
        TlmOwnerCoordinationGroups.unload(helper.getLevel());
        helper.succeed();
    }

    private static EntityMaid maid(
            GameTestHelper helper,
            Player owner,
            BlockPos position
    ) {
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setTame(true);
        maid.setTask(
                TaskManager.findTask(FreedomMaidTask.UID).orElseThrow()
        );
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        maid.setPos(
                position.getX() + 0.5D,
                position.getY(),
                position.getZ() + 0.5D
        );
        helper.getLevel().addFreshEntity(maid);
        return maid;
    }
}
