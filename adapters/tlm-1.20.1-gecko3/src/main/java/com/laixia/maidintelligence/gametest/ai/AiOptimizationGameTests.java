package com.laixia.maidintelligence.gametest.ai;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.sensor.MaidPickupEntitiesSensor;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.ai.api.AiOptimizationSnapshot;
import com.laixia.maidintelligence.feature.ai.api.MaidAiOptimizationApi;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AiOptimizationGameTests {
    private AiOptimizationGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void reachablePathIsReusedUntilMaidMoves(
            GameTestHelper helper
    ) {
        MaidAiOptimizationApi optimization = optimization();
        helper.assertTrue(
                optimization.enabled()
                        && optimization.reachablePathCacheTicks() > 0,
                "AI path caching must be enabled for this verification"
        );
        optimization.resetMetrics();

        EntityMaid maid = spawnMaid(helper);
        helper.runAfterDelay(1, () -> {
            maid.setOnGround(true);
            BlockPos target = maid.blockPosition().east();
            helper.assertTrue(
                    maid.canPathReach(target),
                    "Open test target was unexpectedly unreachable"
            );
            AiOptimizationSnapshot afterFirstCheck = optimization.snapshot();

            helper.assertTrue(
                    maid.canPathReach(target),
                    "Cached reachable target changed result"
            );
            AiOptimizationSnapshot afterCachedCheck = optimization.snapshot();
            helper.assertTrue(
                    afterCachedCheck.pathCacheHits()
                            > afterFirstCheck.pathCacheHits(),
                    "Repeated reachable check did not hit the cache"
            );
            helper.assertTrue(
                    afterCachedCheck.pathComputations()
                            == afterFirstCheck.pathComputations(),
                    "Repeated reachable check still computed a path"
            );

            maid.setPos(maid.getX(), maid.getY(), maid.getZ() + 1.0D);
            maid.setOnGround(true);
            helper.assertTrue(
                    maid.canPathReach(target),
                    "Target became unreachable after moving one block"
            );
            helper.assertTrue(
                    optimization.snapshot().pathComputations()
                            > afterCachedCheck.pathComputations(),
                    "Moving the maid did not invalidate the path cache"
            );
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void pickupSensorPreservesDistanceOrder(
            GameTestHelper helper
    ) {
        MaidAndItems fixture = spawnPickupFixture(helper);
        helper.assertTrue(
                fixture.maid().canPickup(fixture.near(), true)
                        && fixture.maid().canPickup(fixture.far(), true),
                "Pickup fixture contains an ineligible item"
        );
        new ExposedPickupSensor().scan(helper.getLevel(), fixture.maid());

        List<Entity> visible = fixture.maid().getBrain()
                .getMemory(InitEntities.VISIBLE_PICKUP_ENTITIES.get())
                .orElse(List.of());
        int nearIndex = visible.indexOf(fixture.near());
        int farIndex = visible.indexOf(fixture.far());
        helper.assertTrue(
                nearIndex >= 0 && farIndex >= 0,
                "Pickup sensor omitted an eligible item"
        );
        helper.assertTrue(
                nearIndex < farIndex,
                "Pickup sensor changed nearest-first ordering"
        );
        helper.succeed();
    }

    private static MaidAndItems spawnPickupFixture(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnMaid(helper);
        maid.setPickup(true);
        maid.setInSittingPose(true);
        ItemEntity near = new ItemEntity(
                helper.getLevel(),
                maid.getX() + 2.0D,
                maid.getY(),
                maid.getZ(),
                new ItemStack(Items.DIAMOND)
        );
        ItemEntity far = new ItemEntity(
                helper.getLevel(),
                maid.getX() + 3.0D,
                maid.getY(),
                maid.getZ(),
                new ItemStack(Items.EMERALD)
        );
        helper.getLevel().addFreshEntity(near);
        helper.getLevel().addFreshEntity(far);
        return new MaidAndItems(maid, near, far);
    }

    private static EntityMaid spawnMaid(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        EntityMaid maid = helper.spawn(
                InitEntities.MAID.get(),
                new BlockPos(1, 2, 1)
        );
        maid.setTame(true);
        return maid;
    }

    @SuppressWarnings("unchecked")
    private static MaidAiOptimizationApi optimization() {
        return AdapterRuntime.require(MaidAiOptimizationApi.class);
    }

    private record MaidAndItems(
            EntityMaid maid,
            ItemEntity near,
            ItemEntity far
    ) {
    }

    private static final class ExposedPickupSensor
            extends MaidPickupEntitiesSensor {
        private void scan(ServerLevel level, EntityMaid maid) {
            doTick(level, maid);
        }
    }
}
