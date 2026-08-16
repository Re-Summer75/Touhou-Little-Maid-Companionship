package com.laixia.maidintelligence.gametest.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmEnRouteScoop;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 顺路捡拾：手是空闲资源，脚不是她的。
 *
 * <p>这三条钉的是资源仲裁里最小的那条契约——移动目标只有一个持有者，想在别人
 * 走路时做事的行为只能借空闲的手。所以第一条断"borrow 成立"（走路时臂展内的
 * 东西被收走、移动目标一动不动），后两条断"borrow 的边界"（不在路上不借，
 * 打着架不借）。
 *
 * <p>直接调 {@code TlmEnRouteScoop.tick}，不经编排器：经过的话清扫意图也会奔
 * 同一件东西去，两条路径都能把它收掉，测试就分不清是谁干的了。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class EnRouteScoopGameTests {
    private EnRouteScoopGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void walkingPastADropSheScoopsItWithoutStopping(
            GameTestHelper helper
    ) {
        EntityMaid maid = maid(helper);
        WalkTarget heading = walkFarAway(helper, maid);
        ItemEntity beside = drop(helper, 2.8D, 1.5D);

        TlmEnRouteScoop.create().tick(maid, helper.getLevel().getGameTime());

        helper.assertFalse(
                beside.isAlive(),
                "A drop within arm's reach of her path was left lying"
        );
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .orElse(null) == heading,
                "The scoop touched her walk target — hands only, never feet"
        );
        maid.discard();
        helper.succeed();
    }

    /** 不在路上就不借手：站着的时候这是清扫意图的事，不是顺手的事。 */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void standingStillSheLeavesItForTheSweep(
            GameTestHelper helper
    ) {
        EntityMaid maid = maid(helper);
        ItemEntity beside = drop(helper, 2.8D, 1.5D);

        TlmEnRouteScoop.create().tick(maid, helper.getLevel().getGameTime());

        helper.assertTrue(
                beside.isAlive(),
                "The scoop fired while she was not walking anywhere"
        );
        maid.discard();
        helper.succeed();
    }

    /** 打着架不借：那双手另有安排。 */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void midFightHerHandsAreNotForBorrowing(
            GameTestHelper helper
    ) {
        EntityMaid maid = maid(helper);
        walkFarAway(helper, maid);
        Zombie foe = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 3));
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, foe);
        ItemEntity beside = drop(helper, 2.8D, 1.5D);

        TlmEnRouteScoop.create().tick(maid, helper.getLevel().getGameTime());

        helper.assertTrue(
                beside.isAlive(),
                "The scoop fired mid-fight"
        );
        foe.discard();
        maid.discard();
        helper.succeed();
    }

    private static WalkTarget walkFarAway(
            GameTestHelper helper,
            EntityMaid maid
    ) {
        WalkTarget heading = new WalkTarget(
                new BlockPosTracker(helper.absolutePos(new BlockPos(8, 2, 1))),
                0.6F,
                1
        );
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, heading);
        return heading;
    }

    private static EntityMaid maid(GameTestHelper helper) {
        for (int x = 0; x <= 9; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, 2, 2, 1));
        maid.setTame(true);
        maid.setPickup(true);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        return maid;
    }

    private static ItemEntity drop(
            GameTestHelper helper,
            double x,
            double z
    ) {
        Vec3 origin = GameTestPositions.center(helper, 0, 2, 0);
        ItemEntity entity = new ItemEntity(
                helper.getLevel(),
                origin.x + x,
                origin.y,
                origin.z + z,
                new ItemStack(Items.COBBLESTONE)
        );
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
}
