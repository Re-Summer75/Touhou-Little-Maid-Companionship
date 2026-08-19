package com.laixia.maidintelligence.gametest.interaction;

import com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.interaction.handler.MaidInteractionHandler;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 交互按键开关的两种状态。
 *
 * <p>三段断言共用一个场景，而不是拆成三个测试：每个场景都会往共享的测试世界里
 * 放一名主人和一只女仆，而主人和可坐实体都进 Affordance 索引，索引又有 top-K
 * 上限——多出来的两套人马足以把隔壁「五格外的空椅子」挤出候选，让那个测试找不到
 * 椅子。测试之间的隔离靠的是少留东西，不是多开房间。
 *
 * <p>关闭时的断言看的是「事件没有被取消」，而不只是「女仆没坐下」：交还本体的
 * 前提是这次交互原封不动地传下去，一个吞掉事件却什么也不做的实现同样会让右键失灵。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class MaidInteractionSwitchGameTests {
    private MaidInteractionSwitchGameTests() {
    }

    @GameTest(batch = "maidinteractionswitch", templateNamespace = "minecraft", template = "empty")
    public static void theSwitchGatesBothDefaultGestures(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 3, 2);
        EntityMaid maid = scene.maid(1, 2, 1);

        InteractMaidEvent ignoredSit = emptyHand(scene.owner(), maid);
        new MaidInteractionHandler(() -> false).onNormalInteract(ignoredSit);
        helper.assertFalse(
                maid.isMaidInSittingPose(),
                "Disabled keys still toggled the sitting pose"
        );
        helper.assertFalse(
                ignoredSit.isCanceled(),
                "Disabled keys swallowed the interaction instead of "
                        + "handing it back"
        );

        scene.owner().setShiftKeyDown(true);
        InteractMaidEvent ignoredShift = emptyHand(scene.owner(), maid);
        new MaidInteractionHandler(() -> false).onShiftInteract(ignoredShift);
        helper.assertFalse(
                ignoredShift.isCanceled(),
                "Disabled keys still claimed the shift interaction"
        );
        scene.owner().setShiftKeyDown(false);

        InteractMaidEvent handledSit = emptyHand(scene.owner(), maid);
        new MaidInteractionHandler(() -> true).onNormalInteract(handledSit);
        helper.assertTrue(
                maid.isMaidInSittingPose(),
                "Enabled keys did not seat her on an empty-hand right-click"
        );
        helper.assertTrue(
                handledSit.isCanceled(),
                "Enabled keys left the interaction for the host to repeat"
        );

        helper.succeed();
    }

    private static InteractMaidEvent emptyHand(Player owner, EntityMaid maid) {
        return new InteractMaidEvent(owner, maid, ItemStack.EMPTY);
    }
}
