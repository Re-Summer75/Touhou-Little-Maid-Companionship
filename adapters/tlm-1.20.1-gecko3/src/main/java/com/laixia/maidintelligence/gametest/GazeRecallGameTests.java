package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.behavior.domain.GazeRecallPolicy;
import com.laixia.maidintelligence.feature.behavior.handler.OwnerGazeRecallHandler;
import com.laixia.maidintelligence.feature.behavior.tlm.TlmMaidGazeRecallService;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class GazeRecallGameTests {
    private GazeRecallGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void levelOneGazeRecallApproachesOwner(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnOwnedMaid(helper);
        Player owner = owner(helper, maid);
        maid.setFavorability(64);

        boolean recalled = service().tryRecall(owner, maid);

        helper.assertTrue(recalled, "Level-one gaze recall was rejected");
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .map(target -> target.getTarget())
                        .filter(EntityTracker.class::isInstance)
                        .map(EntityTracker.class::cast)
                        .map(EntityTracker::getEntity)
                        .filter(owner::equals)
                        .isPresent(),
                "Gaze recall did not target the owner"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void gazeRecallRespectsFavorabilityAndCommandedSit(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnOwnedMaid(helper);
        Player owner = owner(helper, maid);
        maid.setFavorability(63);
        helper.assertFalse(
                service().tryRecall(owner, maid),
                "Favorability level zero unlocked gaze recall"
        );

        maid.setFavorability(64);
        maid.setInSittingPose(true);
        helper.assertFalse(
                service().tryRecall(owner, maid),
                "Gaze recall overrode the owner's sitting command"
        );
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .isEmpty(),
                "Rejected gaze recall wrote a movement target"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void gazeTargetingIgnoresBlockOcclusion(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnOwnedMaid(helper);
        Player owner = helper.makeMockPlayer();
        owner.setPos(
                maid.getX() - 4.0D,
                maid.getY(),
                maid.getZ()
        );
        maid.setOwnerUUID(owner.getUUID());
        owner.lookAt(
                EntityAnchorArgument.Anchor.EYES,
                maid.getEyePosition()
        );
        Vec3 obstruction = owner.getEyePosition().lerp(
                maid.getEyePosition(),
                0.5D
        );
        helper.getLevel().setBlockAndUpdate(
                BlockPos.containing(obstruction),
                Blocks.STONE.defaultBlockState()
        );

        helper.assertFalse(
                owner.hasLineOfSight(maid),
                "Gaze occlusion fixture did not block normal line of sight"
        );
        helper.assertTrue(
                OwnerGazeRecallHandler.findLookedAtMaid(owner, 8.0D) == maid,
                "Block occlusion prevented gaze targeting"
        );
        helper.succeed();
    }

    private static EntityMaid spawnOwnedMaid(GameTestHelper helper) {
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        EntityMaid maid = helper.spawn(
                InitEntities.MAID.get(),
                new BlockPos(1, 2, 1)
        );
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        return maid;
    }

    private static Player owner(
            GameTestHelper helper,
            EntityMaid maid
    ) {
        Player owner = helper.makeMockPlayer();
        owner.setPos(4.5D, 2.0D, 1.5D);
        maid.setOwnerUUID(owner.getUUID());
        return owner;
    }

    private static TlmMaidGazeRecallService service() {
        return new TlmMaidGazeRecallService(GazeRecallPolicy.defaults());
    }
}
