package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.FreedomMaidTask;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.CombatMovement;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 走到打得到的地方，以及打空之后换手。
 *
 * <p>两条都不是战术问题，是"她停在够不着的地方"和"她举着空武器"。既有测试都
 * 漏掉了，因为它们要么只问她选了哪个意图，要么只问伤害有没有落上。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class CombatApproachGameTests {
    /** 与 TlmCombatAction 一致的战斗移动倍率。 */
    private static final float COMBAT_SPEED = 0.6F;

    /** 弓的期望站位，也是远程追击写下的停止距离。 */
    private static final int BOW_STANDOFF = 8;

    /** 剑的停止距离，约等于她的挥击半径。 */
    private static final int SWORD_STANDOFF = 1;

    private CombatApproachGameTests() {
    }

    /**
     * 换成近战时，停止距离必须跟着换。
     *
     * <p>去重曾只比较位置，而 EntityTracker 报告的就是目标自己的位置，于是两边
     * 恒为零、恒判定相同。远程追击因此活过了换武器：她仍在走向同一个目标，所以
     * 什么都没重写，停止距离留在八格，而剑只够到一格半。她走到八格、停下，然后
     * 在整场战斗里对着空气挥刀。
     *
     * <p>这条断言只看写下的停止距离，不看她走到哪——走多久是导航的事，而"她被
     * 告知在八格外就算到了"是这里的命题。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void changingStanceChangesWhereSheStops(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 4, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        Zombie zombie = target(helper, maid, 3.0D);

        CombatMovement.chase(maid, zombie, BOW_STANDOFF, COMBAT_SPEED);
        helper.assertTrue(
                closeEnough(maid) == BOW_STANDOFF,
                "远程追击没有写下期望的停止距离，实际为 " + closeEnough(maid)
        );

        CombatMovement.chase(maid, zombie, SWORD_STANDOFF, COMBAT_SPEED);
        helper.assertTrue(
                closeEnough(maid) == SWORD_STANDOFF,
                "换成近战后停止距离仍是 " + closeEnough(maid)
                        + "；她会停在够不着的地方对空气挥刀"
        );
        helper.succeed();
    }

    /**
     * 同一个目标、同一个停止距离才算重复。
     *
     * <p>去重本身是必要的：移动协调把同一意图的再次写入判为续租并在强制模式下
     * 回滚，所以每 tick 重写一个追击等于每 tick 取消自己。这条确认修正没有把
     * 去重一起弄丢。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void anUnchangedChaseIsNotRewritten(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 4, 2);
        EntityMaid maid = scene.maid(1, 2, 1);
        Zombie zombie = target(helper, maid, 3.0D);

        CombatMovement.chase(maid, zombie, SWORD_STANDOFF, COMBAT_SPEED);
        WalkTarget first = walkTarget(maid);
        CombatMovement.chase(maid, zombie, SWORD_STANDOFF, COMBAT_SPEED);

        helper.assertTrue(
                walkTarget(maid) == first,
                "同一个追击被重写了；移动协调会把它当成续租并回滚"
        );
        helper.succeed();
    }

    /**
     * 最后一支弹药打出去之后，她该拔剑。
     *
     * <p>弹药是"这把武器现在能不能用"的一部分，不是背包的静态属性。给她一支箭，
     * 打完之后远程候选整体失效，选择应当落到近战。
     *
     * <p>第一条断言防止空跑：她压根没打出去的话，这条测试就没有验证到换手，
     * 而"没换手"和"没开火"在最终状态上长得一模一样。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 260
    )
    public static void anEmptyBowGivesWayToTheSword(GameTestHelper helper) {
        runsDryThenDrawsSteel(helper, Items.BOW);
    }

    /** 弩同理：装填与击发是两步，但没有弹药时两步都不该开始。 */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 260
    )
    public static void anEmptyCrossbowGivesWayToTheSword(
            GameTestHelper helper
    ) {
        runsDryThenDrawsSteel(helper, Items.CROSSBOW);
    }

    private static void runsDryThenDrawsSteel(
            GameTestHelper helper,
            Item weapon
    ) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(1, 2, 2);
        // 射击经 maid.performRangedAttack 转发给当前任务，任务不对整条路由就
        // 落空。这里要验证的是弹药耗尽后的换手，前提是她真的打得出去。
        maid.setTask(new FreedomMaidTask());
        maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(weapon));
        // 一支，正好够打一次。
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.ARROW, 1)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                1, new ItemStack(Items.IRON_SWORD)
        );
        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX(), maid.getY(), maid.getZ() + 6.0D);
        // 只当靶子：会动的僵尸会自己走位，断言就不再只关于换手了。
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);

        // 只观察，不驱动：编排器在生产里每 tick 自己跑一次，测试再手动推一次
        // 就成了每 tick 两次，拉弦节奏与真实环境对不上。
        helper.startSequence()
                .thenExecuteFor(200, () -> {
                })
                .thenExecute(() -> {
                    helper.assertFalse(
                            hasArrow(maid),
                            "两百 tick 里那支箭一直在，她根本没打出去，"
                                    + "这条测试没有验证到换手"
                    );
                    helper.assertTrue(
                            maid.getMainHandItem().is(Items.IRON_SWORD),
                            "弹药耗尽后她手里仍是 "
                                    + maid.getMainHandItem().getItem()
                                    + "；她会举着空的远程武器反复拉弦"
                    );
                })
                .thenSucceed();
    }

    private static Zombie target(
            GameTestHelper helper,
            EntityMaid maid,
            double offset
    ) {
        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX() + offset, maid.getY(), maid.getZ());
        zombie.setNoAi(true);
        helper.getLevel().addFreshEntity(zombie);
        return zombie;
    }

    private static boolean hasArrow(EntityMaid maid) {
        var backpack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            if (backpack.getStackInSlot(slot).is(Items.ARROW)) {
                return true;
            }
        }
        return maid.getOffhandItem().is(Items.ARROW);
    }

    private static int closeEnough(EntityMaid maid) {
        WalkTarget target = walkTarget(maid);
        return target == null ? -1 : target.getCloseEnoughDist();
    }

    private static WalkTarget walkTarget(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
    }
}
