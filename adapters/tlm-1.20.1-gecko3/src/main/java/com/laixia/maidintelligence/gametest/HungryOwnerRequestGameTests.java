package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.BehaviorTuning;
import com.laixia.maidintelligence.feature.behavior.tlm.TlmMaidHungryOwnerRequestService;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
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
import java.util.function.Consumer;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class HungryOwnerRequestGameTests {
    private HungryOwnerRequestGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void hungerTwentyHighTrustSeeksOwner(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper);
        status().setHunger(fixture.maid(), 20);
        fixture.maid().setFavorability(384);
        helper.assertTrue(
                fixture.maid()
                        .getFavorabilityManager()
                        .getLevel() > 2,
                "Food-request fixture did not reach favorability level three"
        );

        boolean requested = service(0.20D).tryRequest(fixture.maid());

        helper.assertTrue(requested, "Eligible hungry maid did not request food");
        assertWalkTargetOwner(helper, fixture);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void needsFoodDialogueBranchSeeksOwner(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper);
        status().setHunger(fixture.maid(), 40);
        fixture.maid().setFavorability(0);

        boolean requested = service(0.05D).tryRequest(fixture.maid());

        helper.assertTrue(
                requested,
                "Needs-food dialogue branch did not request the owner"
        );
        assertWalkTargetOwner(helper, fixture);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void hungryMaidRequestsOnArrival(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper);
        status().setHunger(fixture.maid(), 40);
        AtomicInteger requestActions = new AtomicInteger();
        TlmMaidHungryOwnerRequestService service = service(
                0.0D,
                maid -> requestActions.incrementAndGet()
        );

        helper.assertTrue(
                service.tryRequest(fixture.maid()),
                "Hungry maid did not begin approaching her owner"
        );
        helper.assertTrue(
                requestActions.get() == 0,
                "Hungry maid requested before reaching her owner"
        );
        fixture.maid().setPos(
                fixture.owner().getX() - 1.0D,
                fixture.owner().getY(),
                fixture.owner().getZ()
        );
        helper.assertTrue(
                service.shouldEvaluate(
                        fixture.maid(),
                        helper.getLevel().getGameTime() + 1L
                ) && service.tryRequest(fixture.maid()),
                "Arrival did not complete the pending food request"
        );
        helper.assertTrue(
                requestActions.get() == 1,
                "Food request action was not played exactly once"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void hungryRequestRespectsThresholdAndCommandedSit(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper);
        fixture.maid().setFavorability(384);
        status().setHunger(fixture.maid(), 41);
        helper.assertFalse(
                service(0.0D).tryRequest(fixture.maid()),
                "Maid requested food above hunger forty"
        );

        status().setHunger(fixture.maid(), 40);
        fixture.maid().setInSittingPose(true);
        helper.assertFalse(
                service(0.0D).tryRequest(fixture.maid()),
                "Food request overrode the owner's sitting command"
        );
        fixture.maid().setInSittingPose(false);
        helper.assertFalse(
                service(1.0D).tryRequest(fixture.maid()),
                "Failed probability roll still requested food"
        );
        helper.assertTrue(
                fixture.maid()
                        .getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .isEmpty(),
                "Rejected food request wrote a movement target"
        );
        helper.succeed();
    }

    private static void assertWalkTargetOwner(
            GameTestHelper helper,
            Fixture fixture
    ) {
        helper.assertTrue(
                fixture.maid()
                        .getBrain()
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
        owner.setPos(5.5D, 2.0D, 1.5D);
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(1.5D, 2.0D, 1.5D);
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        return new Fixture(owner, maid);
    }

    private static TlmMaidHungryOwnerRequestService service(
            double randomSample
    ) {
        return service(randomSample, maid -> {
        });
    }

    private static TlmMaidHungryOwnerRequestService service(
            double randomSample,
            Consumer<EntityMaid> requestAction
    ) {
        return new TlmMaidHungryOwnerRequestService(
                status(),
                BehaviorTuning::defaults,
                maid -> randomSample,
                requestAction
        );
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
