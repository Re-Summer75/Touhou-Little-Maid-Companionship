package com.laixia.maidintelligence.gametest.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * 感知一把尺：捡东西看得和打架一样远，而且以**她**为中心。
 *
 * <p>钉的是一次实机报告：捡东西的范围明显比战斗小、还只围着玩家转。来源是承诺
 * 模型落地前的一道临时绳子（跟随时只在主人八格内挑清扫目标）——它把同一双眼睛
 * 做成了两种视力。绳子已删，这条测试守住它不回来：跟随模式下、离主人十四格的
 * 东西照样成为目标，与战斗共用 {@code PerceptionRange.BLOCKS} 这一个数。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class PerceptionParityGameTests {
    private static final Map<String, String> PARAMETERS =
            Map.of("speed", "0.7", "close_distance", "1");

    private PerceptionParityGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void sheSeesADropAsFarAsSheSeesAFight(
            GameTestHelper helper
    ) {
        // 有主人、跟随模式（home 关即跟随）——旧绳子正是在这个组合下把她的视野
        // 缩成主人周围八格的。围墙：这只女仆开着拾物，而拾取感知有十六格，敞着
        // 会认领到邻居结构里的东西、用租约把人家堵住。
        CompanionScene scene = CompanionScene.walledRoom(helper, 18, 3);
        EntityMaid maid = scene.maid(2, 2, 1);
        maid.setPickup(true);
        ItemEntity far = scene.drop(Items.COBBLESTONE, 16, 2, 1);

        ActionResult result = new TlmMaidIntentActions(
                ignored -> {
                },
                new MaidSnackCabinetMealSource(
                        new MaidMealAccess(),
                        new TlmAffordancePerceptionService()
                )
        ).execute(
                maid,
                CompanionIntentIds.PICK_UP_LOOSE_DROP,
                PARAMETERS,
                helper.getLevel().getGameTime(),
                0
        );

        helper.assertTrue(
                result == ActionResult.RUNNING,
                "A drop fourteen blocks out (well past the old owner-leash) "
                        + "was not worth setting out for"
        );
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .map(walk -> walk.getTarget()
                                .currentPosition()
                                .closerThan(far.position(), 2.0D))
                        .orElse(false),
                "She was not sent anywhere near the far drop"
        );
        // 断言完就收权：这只女仆在结构拆除前还会活一小会儿，开着拾物的她会去
        // 认领十六格内任何结构的掉落物，把邻居的清扫用租约堵住。
        maid.setPickup(false);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        helper.succeed();
    }
}
