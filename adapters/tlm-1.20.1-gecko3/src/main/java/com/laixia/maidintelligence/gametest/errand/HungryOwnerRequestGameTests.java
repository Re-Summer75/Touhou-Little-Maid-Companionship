package com.laixia.maidintelligence.gametest.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.gametest.support.IntentGameTestRuntime;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class HungryOwnerRequestGameTests {
    private HungryOwnerRequestGameTests() {
    }

    @GameTest(batch = "hungryownerrequest", templateNamespace = "minecraft", template = "empty")
    public static void hungryMaidApproachesAndRequestsOnArrival(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper);
        status().setHunger(fixture.maid(), 40);
        AtomicInteger requests = new AtomicInteger();
        IntentGameTestRuntime.Runtime runtime =
                IntentGameTestRuntime.create(
                        status(),
                        maid -> requests.incrementAndGet(),
                        IntentGameTestRuntime.hungerIntent()
                );
        long gameTime = helper.getLevel().getGameTime();

        helper.assertTrue(
                runtime.tick(fixture.maid(), gameTime),
                "Hungry intent did not start"
        );
        assertWalkTargetOwner(helper, fixture);
        helper.assertTrue(
                requests.get() == 0,
                "Food request action ran before arrival"
        );

        fixture.maid().setPos(
                fixture.owner().getX() - 1.0D,
                fixture.owner().getY(),
                fixture.owner().getZ()
        );
        runtime.tick(fixture.maid(), gameTime + 1L);
        runtime.tick(fixture.maid(), gameTime + 2L);
        helper.assertTrue(
                requests.get() == 1,
                "Food request action did not run exactly once on arrival"
        );
        helper.succeed();
    }

    @GameTest(batch = "hungryownerrequest", templateNamespace = "minecraft", template = "empty")
    public static void hungerThresholdAndCommandedSitAreDataGuards(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper);
        IntentGameTestRuntime.Runtime runtime =
                IntentGameTestRuntime.create(
                        status(),
                        maid -> {
                        },
                        IntentGameTestRuntime.hungerIntent()
                );
        long gameTime = helper.getLevel().getGameTime();

        status().setHunger(fixture.maid(), 41);
        helper.assertFalse(
                runtime.tick(fixture.maid(), gameTime),
                "Hunger above forty passed the data guard"
        );
        status().setHunger(fixture.maid(), 40);
        fixture.maid().setInSittingPose(true);
        helper.assertFalse(
                runtime.tick(fixture.maid(), gameTime + 1L),
                "Commanded sitting was overridden"
        );
        helper.assertTrue(
                fixture.maid().getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .isEmpty(),
                "Rejected hungry intent wrote movement"
        );
        helper.succeed();
    }

    private static void assertWalkTargetOwner(
            GameTestHelper helper,
            Fixture fixture
    ) {
        helper.assertTrue(
                fixture.maid().getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .map(target -> target.getTarget())
                        .filter(EntityTracker.class::isInstance)
                        .map(EntityTracker.class::cast)
                        .map(EntityTracker::getEntity)
                        .filter(fixture.owner()::equals)
                        .isPresent(),
                "Hungry maid did not seek her owner"
        );
    }

    private static Fixture fixture(GameTestHelper helper) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Player owner = helper.makeMockPlayer();
        owner.setPos(GameTestPositions.center(helper, 5, 2, 1));
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(GameTestPositions.center(helper, 1, 2, 1));
        maid.setTame(true);
        maid.setTask(
                TaskManager.findTask(FreedomMaidTask.UID).orElseThrow()
        );
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        return new Fixture(owner, maid);
    }

    @SuppressWarnings("unchecked")
    private static MaidStatusApi<EntityMaid> status() {
        return (MaidStatusApi<EntityMaid>) AdapterRuntime.require(
                MaidStatusApi.class
        );
    }

    private record Fixture(Player owner, EntityMaid maid) {
    }
}
