package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMode;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.CombatMovement;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ThreatProfile;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmAlertness;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmCombatAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.gametest.support.CombatProbe;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 走到打得到的地方，以及手里那把究竟是不是该拿的那把。
 *
 * <p>这些都不是战术问题，是"她停在够不着的地方"和"她举着不该举的东西"。既有
 * 测试都漏掉了，因为它们要么只问她选了哪个意图，要么只问伤害有没有落上。
 *
 * <p>换手这一环单独钉住是有理由的：决定和执行是两段代码，决定对了而手是错的，
 * 从外面看和决定错了一模一样。
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

        // 每一次换手记一行。她最后手里拿着什么是结果，而"她是在几格上改的主意"
        // 才是原因——两者差着整条决定链，只看结果的话，"从没选过远程"和"选了
        // 又反悔"长得完全一样。
        List<String> swaps = new java.util.ArrayList<>();
        String[] previous = {""};
        String[] priced = {"-"};
        long start = helper.getLevel().getGameTime();

        // 只观察，不驱动：编排器在生产里每 tick 自己跑一次，测试再手动推一次
        // 就成了每 tick 两次，拉弦节奏与真实环境对不上。
        helper.startSequence()
                .thenExecuteFor(200, () -> {
                    String held = maid.getMainHandItem().getItem().toString();
                    if (!held.equals(previous[0])) {
                        // Priced before the swap is recorded, so the line
                        // describes the situation the choice was made in rather
                        // than the one it produced.
                        if (previous[0].isEmpty()) {
                            priced[0] = CombatProbe.pricingNow(maid);
                        }
                        previous[0] = held;
                        swaps.add(String.format("%d:%s@%.1f",
                                helper.getLevel().getGameTime() - start,
                                held, maid.distanceTo(zombie)));
                    }
                })
                .thenExecute(() -> {
                    helper.assertFalse(
                            hasArrow(maid),
                            "两百 tick 里那支箭一直在，她根本没打出去，"
                                    + "这条测试没有验证到换手。"
                                    + CombatProbe.holdingAndSeeing(maid)
                                    + " 换手=" + swaps
                                    + "\n定价: " + priced[0]
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

    /**
     * 决定拿最好的那把，就得真的拿到最好的那把。
     *
     * <p>换手曾经只按"种类"去背包里找，而宿主的查找取第一个命中的槽位——于是
     * 一个"用下界合金剑"的决定可以换来一把石剑。更糟的是同一个判据先测主手：
     * 手上已经拿着任意一件近战武器，查找就直接短路了，她永远换不掉手里那把。
     *
     * <p>断言看的是最终握着什么，而不是策略选了什么：选择本来就没错。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void sheDrawsTheBetterBladeNotTheOneAlreadyHeld(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setTask(new FreedomMaidTask());
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_SWORD)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.DIAMOND_SWORD)
        );
        seeHostile(maid, helper, 1.5D);

        combat().execute(maid);

        helper.assertTrue(
                maid.getMainHandItem().is(Items.DIAMOND_SWORD),
                "她仍握着 " + maid.getMainHandItem().getItem()
                        + "；换手按种类匹配，手上随便一把近战就让查找短路了"
        );
        helper.succeed();
    }

    /**
     * 她不该对一件用不了的武器做出使用它的动作。
     *
     * <p>玩家报告的是"没有子弹还在那儿举着弓"。这条不是问她选得对不对，而是
     * 问执行路径有没有可能与选择脱节——姿态一旦按"策略选了远程"分派，而手里
     * 那把恰好射不出去，她就会站在射程上永远拉一张空弓。现在分派看的是手里
     * 这一把本身能不能用，于是这种脱节在结构上不成立。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void sheNeverWorksAWeaponSheCannotFire(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setTask(new FreedomMaidTask());
        // 空弓在手，箭一支也没有；背包里有把剑。
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.BOW)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.IRON_SWORD)
        );
        seeHostile(maid, helper, 6.0D);

        combat().execute(maid);

        helper.assertTrue(
                maid.getMainHandItem().is(Items.IRON_SWORD),
                "空弓没有被换掉，她手里仍是 "
                        + maid.getMainHandItem().getItem()
        );
        helper.assertFalse(
                maid.isUsingItem() && !new TlmWeaponScanner(
                        RangedWeaponRecognizer.NONE
                ).canFire(maid, maid.getMainHandItem()),
                "她正在使用一件打不出去的武器"
        );

        // 连剑也没有：此时正确的答案是不打，而不是继续举着空弓。
        maid.getAvailableBackpackInv().setStackInSlot(0, ItemStack.EMPTY);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.BOW)
        );
        seeHostile(maid, helper, 6.0D);
        combat().execute(maid);

        helper.assertFalse(
                maid.isUsingItem(),
                "无弹药、无近战，她还是把空弓拉了起来"
        );
        helper.succeed();
    }

    /**
     * 本体攻击任务在跑的时候，本模组的战斗动作必须让位。
     *
     * <p>交战意图刻意不检查工作模式——战斗是最高中断 band，本该能抢占。但那也
     * 意味着主人把她设成本体攻击任务时，两套系统会同时驱动同一场战斗：各自选
     * 目标，各自调用 `doHurtTarget`，于是她在那些任务上伤害翻倍。
     *
     * <p>断言看的是"她有没有把对方当成攻击目标"：让位的形态是完全不接手，而不
     * 是接手之后打得温柔一点。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aBuiltInAttackTaskIsLeftAlone(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.IRON_SWORD)
        );
        seeHostile(maid, helper, 1.5D);

        // 先确认自由模式下她确实会接手，否则这条测试可能因为别的原因而通过。
        maid.setTask(new FreedomMaidTask());
        combat().execute(maid);
        helper.assertTrue(
                maid.getBrain()
                        .getMemory(MemoryModuleType.ATTACK_TARGET).isPresent(),
                "自由模式下她都没有接手战斗，这条测试没有验证到让位"
        );

        // 换成本体的近战攻击任务：让位现在发生在更早也更彻底的地方。
        //
        // 战斗动作里曾有一句"本体攻击任务在跑就收手"，因为那时本模组的意图层
        // 在所有工作模式下都跑。现在编排器只在自由模式启动，本体模式下这套
        // 逻辑根本不会被调用——断言那句已删的分支等于断言一段不存在的代码，
        // 所以改问真正决定这件事的开关。
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.setTask(TaskManager.findTask(TaskAttack.UID).orElseThrow());
        helper.assertFalse(
                FreedomMode.isActive(maid),
                "本体攻击任务下 FreedomMode 仍然认为该由我们接管，"
                        + "两套一起打会让伤害翻倍"
        );
        helper.succeed();
    }

    /**
     * 战斗中不该被捡东西拖走。
     *
     * <p>玩家报告"战斗下被捡东西影响"。拾取是本体行为，带真实的移动承诺，一旦
     * 开始就每 tick 和战斗抢她的脚——赢的次数足以让人看到她为了一根掉在地上的
     * 羽毛走进怪堆。所以从源头拒绝，而不是在仲裁里压过它：压过去也意味着她已经
     * 起步、转身、再被拉回来。
     *
     * <p>三段都要断言：安静时允许（否则可能是把拾取整个关掉了）、逼近时拒绝、
     * 远处看得见但过不来时仍然允许（否则山谷对面一只骷髅就能停掉全部家务）。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void pickupStandsDownWhenThreatened(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.setTask(new FreedomMaidTask());

        helper.assertTrue(
                TlmAlertness.allowsErrands(maid),
                "空场时她就被禁止做差事了，那这条测的不是威胁"
        );

        Zombie onTop = seeHostileEntity(maid, helper, 1.5D);
        helper.assertFalse(
                TlmAlertness.allowsErrands(maid),
                "东西已经贴到脸上，她还被允许走开去捡东西。状态="
                        + TlmAlertness.of(maid)
                        + " 敌意判定="
                        + ThreatProfile.isHostileTo(maid, onTop)
                        + " 到达时间="
                        + ThreatProfile.secondsToContact(maid, onTop)
        );

        // 十六格外、静止不动：看得见，但永远到不了。
        Zombie idle = new Zombie(helper.getLevel());
        idle.setPos(maid.getX() + 15.0D, maid.getY(), maid.getZ());
        idle.setNoAi(true);
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, List.of(idle))
        );
        helper.assertTrue(
                TlmAlertness.allowsErrands(maid),
                "一个过不来的远处敌人就让她停掉了全部差事"
        );
        helper.succeed();
    }

    private static TlmCombatAction combat() {
        return new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        );
    }

    /**
     * 敌人只进记忆，不进世界。
     *
     * <p>真实僵尸的索敌达十六格，放进共享测试世界会波及邻座；这里要验证的是
     * 感知到敌人之后握着什么，敌人怎么进入感知不是本测试的命题。
     */
    private static Zombie seeHostileEntity(
            EntityMaid maid,
            GameTestHelper helper,
            double offset
    ) {
        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX() + offset, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, zombie);
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, List.of(zombie))
        );
        return zombie;
    }

    private static void seeHostile(
            EntityMaid maid,
            GameTestHelper helper,
            double offset
    ) {
        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX() + offset, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, zombie);
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, List.of(zombie))
        );
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
