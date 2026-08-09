package com.laixia.maidintelligence.gametest.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * Going to read, play chess or use the computer, as a ranked decision.
 *
 * <p>The behaviour itself is TLM's and is unchanged. What these pin is that the
 * decision to take it up now belongs to the orchestrator: the native task
 * stands down under the freedom task, and the errand that replaces it reserves
 * the block so two maids cannot settle at one bookshelf.
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class PastimeErrandGameTests {
    private static final Map<String, String> PARAMETERS =
            Map.of("speed", "0.5", "close_distance", "2");

    private PastimeErrandGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aPastimeAcrossTheRoomIsWalkedToward(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 8, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        BlockPos shelf = placeBookshelf(scene, helper, 6, 2, 1);

        helper.assertTrue(
                run(scene, maid) == ActionResult.RUNNING,
                "A free bookshelf did not start the errand"
        );
        helper.assertTrue(
                headingFor(maid, shelf),
                "She was not sent toward the bookshelf"
        );
        helper.succeed();
    }

    /**
     * A joy block seats one maid, so the second to want it must be told no
     * rather than allowed to walk over and find it taken.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void oneBookshelfSeatsOneMaid(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 8, 2);
        EntityMaid first = scene.maid(1, 2, 1);
        EntityMaid second = scene.maid(2, 2, 1);
        placeBookshelf(scene, helper, 6, 2, 1);

        helper.assertTrue(
                run(scene, first) == ActionResult.RUNNING,
                "The first maid was not sent to the only bookshelf"
        );
        helper.assertTrue(
                run(scene, second) == ActionResult.FAILED,
                "A second maid was sent to a bookshelf already claimed"
        );
        helper.assertFalse(
                second.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "The turned-away maid was still given a walk target"
        );
        helper.succeed();
    }

    /** Nothing to do is not the same as somewhere to go. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void withoutAJoyBlockThereIsNoPastime(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 4, 2);
        EntityMaid maid = scene.maid(1, 2, 1);

        helper.assertTrue(
                run(scene, maid) == ActionResult.FAILED,
                "A maid with nothing to read was sent somewhere anyway"
        );
        helper.assertFalse(
                maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "A maid with nothing to read was given a walk target"
        );
        helper.succeed();
    }

    /**
     * The point of the takeover. Under the freedom task the native behaviour
     * has to decline, or it would take her before the orchestrator is asked and
     * the seat it produces would then block her from going to eat.
     *
     * <p>Checked against a maid on another task in the same room, because an
     * assertion that nothing happened proves nothing on its own — the same
     * result would follow from the bookshelf never having been found.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void theNativeBehaviourStandsDownUnderFreedom(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 8, 2);
        // 一个留在本体模式，一个自由模式：这条断言的全部意义在于两边不同。
        EntityMaid working = scene.hostModeMaid(1, 2, 1);
        EntityMaid free = scene.maid(2, 2, 1);
        placeBookshelf(scene, helper, 6, 2, 1);

        helper.assertTrue(
                FreedomMaidTask.UID.equals(free.getTask().getUid()),
                "The freedom task did not take effect"
        );

        // 判据问"这个行为在不在她的 brain 里"，不是"直接构造一个能不能跑起来"。
        //
        // 让位曾经靠拦截行为的启动条件，所以直接 tryStart 一个实例正好测到那段
        // 拦截。现在自由模式压根不注册本体的活动，拦截连同它一起删了——手工
        // new 出来的 MaidJoyTask 当然照跑，它只是个普通对象，与她的 brain 无关。
        // 那样的断言测的是测试自己造的东西。
        helper.assertFalse(
                hostPastimeAvailable(free),
                "自由模式的 brain 里还挂着本体的娱乐活动，"
                        + "它会在编排器被问到之前就把她带走"
        );
        helper.assertTrue(
                hostPastimeAvailable(working),
                "本体模式下娱乐活动也没了——我们把不该碰的模式改了"
        );
        helper.succeed();
    }

    /**
     * 本体的娱乐行为在不在她的 brain 里。
     *
     * <p>{@code MaidJoyTask} 注册在 IDLE 与 WORK 两个活动里，而没注册过的活动
     * 是激活不了的——{@code setActiveActivityIfPossible} 对空活动无效。只用
     * Brain 的公开 API，不必通到私有字段，也不会因为本体增删某条行为而失败。
     */
    private static boolean hostPastimeAvailable(EntityMaid maid) {
        maid.getBrain().setActiveActivityIfPossible(Activity.WORK);
        return maid.getBrain().isActive(Activity.WORK);
    }

    /**
     * Runs one attempt of TLM's own behaviour. It walks her closer on the first
     * passes and only reports started once she is beside the block, so the
     * whole check-rate window is stepped through rather than sampled once.
     */

    private static BlockPos placeBookshelf(
            CompanionScene scene,
            GameTestHelper helper,
            int x,
            int y,
            int z
    ) {
        BlockPos position = scene.at(x, y, z);
        helper.getLevel().setBlockAndUpdate(
                position,
                InitBlocks.BOOKSHELF.get().defaultBlockState()
        );
        return position;
    }

    private static ActionResult run(CompanionScene scene, EntityMaid maid) {
        return scene.actions().execute(
                maid,
                CompanionIntentIds.USE_JOY_BLOCK,
                PARAMETERS,
                scene.gameTime(),
                0
        );
    }

    private static boolean headingFor(EntityMaid maid, BlockPos position) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .filter(BlockPosTracker.class::isInstance)
                .map(BlockPosTracker.class::cast)
                .map(BlockPosTracker::currentBlockPosition)
                .filter(position::equals)
                .isPresent();
    }
}
