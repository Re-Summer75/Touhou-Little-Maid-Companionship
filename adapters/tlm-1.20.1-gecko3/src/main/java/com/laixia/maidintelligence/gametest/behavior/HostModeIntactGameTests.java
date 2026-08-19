package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidBegTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 本体模式必须与没装本模组时一模一样。
 *
 * <p>整套隔离只有一条承诺，而它有两半，各自都能被错误的实现单独满足：自由模式
 * 里没有本体行为（"我们把所有模式都清空了"也能做到），本体模式里本体行为完好
 * （"我们其实什么都没接管"也能做到）。{@code FreedomTaskGameTests} 从 brain
 * 注册层同时钉住两半。
 *
 * <p>这里钉的是另一件事：一条**具体的、玩家看得见的**本体行为，在本体模式下确实
 * 还会启动。结构断言证明"活动注册过"，不证明"注册的东西还能跑"——玩家报的从来
 * 是后者。选祈求（手持蛋糕吸引女仆）是因为它跨越了本模组改过的每一层：需要传感器
 * 填的可见实体记忆、需要限制区判定、需要本体行为自己被注册。任何一层泄漏，它都
 * 会先失效。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class HostModeIntactGameTests {
    /** 本体祈求的触发距离是 6 格，站得比它近。 */
    private static final double BEG_RANGE = 3.0D;

    private HostModeIntactGameTests() {
    }

    /**
     * 本体模式下，手持蛋糕仍然吸引得到她。
     *
     * <p>不喂记忆就测不到东西：可见实体记忆由传感器每 tick 重建，而夹具里没有
     * 走过完整的 tick 循环。喂进去之后，剩下的每一步都是本体自己的判断。
     */
    @GameTest(batch = "hostmodeintact", templateNamespace = "minecraft", template = "empty")
    public static void cakeStillTemptsAHostModeMaid(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.hostModeMaid(2, 2, 2);
        Player owner = scene.owner();
        owner.setPos(
                maid.getX() + BEG_RANGE, maid.getY(), maid.getZ()
        );
        owner.setItemInHand(
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Items.CAKE)
        );
        helper.assertTrue(
                maid.getTemptationItem().test(owner.getMainHandItem()),
                "蛋糕不在本体的诱惑物品里，这条测试的前提就不成立"
        );
        see(maid, owner);

        helper.assertTrue(
                new MaidBegTask().tryStart(
                        helper.getLevel(),
                        maid,
                        helper.getLevel().getGameTime()
                ),
                "本体模式下手持蛋糕吸引不动她了——本模组泄漏到了不该碰的模式"
        );
        helper.succeed();
    }

    /**
     * 同样的场景，自由模式下这条行为不该在她的 brain 里。
     *
     * <p>与上一条成对：只证明本体完好，等于允许"我们什么都没接管"。判据用活动
     * 注册而不是直接构造行为——手工 new 出来的对象与她的 brain 无关，跑不跑都
     * 说明不了问题。
     */
    @GameTest(batch = "hostmodeintact", templateNamespace = "minecraft", template = "empty")
    public static void theSameCakeDoesNotDriveAFreeMaid(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        Player owner = scene.owner();
        owner.setPos(
                maid.getX() + BEG_RANGE, maid.getY(), maid.getZ()
        );
        owner.setItemInHand(
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Items.CAKE)
        );
        see(maid, owner);

        maid.getBrain().setActiveActivityIfPossible(
                net.minecraft.world.entity.schedule.Activity.WORK
        );
        helper.assertFalse(
                maid.getBrain().isActive(
                        net.minecraft.world.entity.schedule.Activity.WORK
                ),
                "自由模式里本体的 WORK 活动还能激活，祈求等行为仍在替她做决定"
        );
        helper.succeed();
    }

    /**
     * 不喂记忆，让传感器自己跑——玩家遇到的就是这条路径。
     *
     * <p>上一条断言手工写入了可见实体记忆，于是它证明的是"记忆有了之后本体行为
     * 还会启动"，而不是"记忆会被填上"。两者之间隔着整个传感器，也隔着感知范围
     * 的来源——本模组恰好改过那一段。玩家报"贴脸都没反应"时，第一条测试是绿的。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 120
    )
    public static void aHostModeMaidActuallySeesHerOwner(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.hostModeMaid(2, 2, 2);
        Player owner = scene.owner();
        // 贴脸：一格。范围完全不是这条测试的命题。
        owner.setPos(maid.getX() + 1.0D, maid.getY(), maid.getZ());
        owner.setItemInHand(
                net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Items.CAKE)
        );
        // 一只真的在世界里的生物，用来区分传感器和夹具。
        // 用绝对坐标：helper.spawn 收的是相对于测试结构的坐标，把女仆的绝对
        // 方块坐标传进去会把它扔到三百多格外，然后"传感器看不见"就成了必然。
        net.minecraft.world.entity.animal.Cow witness =
                new net.minecraft.world.entity.animal.Cow(
                        net.minecraft.world.entity.EntityType.COW,
                        helper.getLevel()
                );
        witness.setPos(maid.getX() + 1.0D, maid.getY(), maid.getZ());
        helper.getLevel().addFreshEntity(witness);

        helper.startSequence()
                .thenExecuteFor(60, () -> {
                })
                .thenExecute(() -> {
                    helper.assertTrue(
                            maid.getBrain().hasMemoryValue(
                                    MemoryModuleType
                                            .NEAREST_VISIBLE_LIVING_ENTITIES
                            ),
                            "六十 tick 之后她的可见实体记忆仍是空的：传感器没有"
                                    + "填上主人，本体的一切依赖它的行为都会静默失效"
                    );
                    var seen = maid.getBrain().getMemory(
                            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES
                    ).orElseThrow();
                    // 先分清"传感器坏了"和"夹具的假玩家不在世界里"。
                    //
                    // GameTest 的 makeMockPlayer 不把玩家加入实体列表，所以任何
                    // 扫描世界的传感器都看不见它——那是夹具的性质，不是产品缺陷。
                    // 放一只真的在世界里的生物，就能把两者分开：它被看见即证明
                    // 传感器本身完好。
                    helper.assertTrue(
                            seen.contains(witness),
                            "贴在她身上的一只牛都没进可见实体记忆：传感器真的坏了。"
                                    + " 搜索范围=" + maid.getTask().searchRadius(maid)
                                    + " 距离=" + maid.distanceTo(witness)
                                    + " 模式=" + maid.getTask().getUid()
                    );
                    helper.assertFalse(
                            seen.find(maid::isOwnedBy).findAny().isPresent(),
                            "假玩家竟然被传感器看见了，这条测试的分辨力假设不成立"
                    );
                })
                .thenSucceed();
    }

    /** 把主人写进可见实体记忆，传感器在夹具里跑不满一轮。 */
    private static void see(EntityMaid maid, LivingEntity seen) {
        List<LivingEntity> visible = List.of(seen);
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_LIVING_ENTITIES, visible
        );
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, visible)
        );
    }
}
