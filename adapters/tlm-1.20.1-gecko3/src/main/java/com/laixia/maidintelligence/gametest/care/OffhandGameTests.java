package com.laixia.maidintelligence.gametest.care;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon.WeaponCandidate;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.WeaponSwap;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.guard.ShieldGuard;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ThreatProfile;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatSample;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidOffhand;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * 副手这一格归谁管。
 *
 * <p>本体那边它是个**只能写一次的槽**：三条把东西放进去的路，零条把东西拿出来
 * 的路，而唯一的清空动作藏在"用完一件物品"的回调里——战斗打断进食就永远走不到
 * 那一步。可见的后果有三个，看上去互不相干：副手插着武器时她整局举不起盾、那把
 * 武器也永远回不到主手、而下一顿饭开始的瞬间它会掉在地上。
 *
 * <p>这里量的就是这三条，外加它们共同的那条底线：**本模组不产生掉落物。**腾不
 * 出手时正确的行为是放弃这一次意图，不是把东西扔出去——所以"背包塞满"那一例和
 * 成功那几例一样重要。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class OffhandGameTests {
    private OffhandGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void vacatingTheOffHandMovesItIntoThePack(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnMaid(helper);
        maid.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_SWORD));

        helper.assertTrue(
                MaidOffhand.vacate(maid),
                "An empty pack and a free hand should have been enough to vacate"
        );
        helper.assertTrue(
                maid.getOffhandItem().isEmpty(),
                "Off hand still holds " + maid.getOffhandItem()
        );
        helper.assertTrue(
                packHolds(maid, Items.IRON_SWORD),
                "The sword left the hand but never reached the pack"
        );
        assertNothingDropped(helper, maid);
        helper.succeed();
    }

    /**
     * 塞不下的时候她什么都不做——这一条比成功那几条更重要。
     *
     * <p>"腾不出手"是本模组唯一允许的失败方式。另一种写法是"塞不下就扔"，那正是
     * 本体的两个方法各自在做的事，也正是这一整组测试存在的理由。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aFullPackMeansSheKeepsHoldingIt(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        fillPack(maid);
        maid.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_SWORD));

        helper.assertFalse(
                MaidOffhand.vacate(maid),
                "A full pack should have refused the vacate"
        );
        helper.assertTrue(
                maid.getOffhandItem().is(Items.IRON_SWORD),
                "The sword should still be in her hand, not " + maid.getOffhandItem()
        );
        assertNothingDropped(helper, maid);
        helper.succeed();
    }

    /**
     * 上一顿饭被打断，扣在隐藏槽里的东西要还回来。
     *
     * <p>本体把副手物品存进 {@code hideInv} 第 0 格，只有 {@code completeUsingItem}
     * 才归还。战斗每次换武器都会打断进食，于是那一格里的盾再也没人取——而下一次
     * "存"会把它扔在地上。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void whatAnInterruptedMealTookHostageComesBack(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnMaid(helper);
        maid.getHideInv().setStackInSlot(0, new ItemStack(Items.SHIELD));

        MaidOffhand.recoverStranded(maid);

        helper.assertTrue(
                maid.getOffhandItem().is(Items.SHIELD),
                "The stranded shield did not come back: " + maid.getOffhandItem()
        );
        helper.assertTrue(
                maid.getHideInv().getStackInSlot(0).isEmpty(),
                "The hidden slot still holds it"
        );
        assertNothingDropped(helper, maid);
        helper.succeed();
    }

    /**
     * 开一顿饭不会把副手那件东西扔出去。
     *
     * <p>这是玩家报的那一条。原先的写法把副手物品交给 {@code memoryHandItemStack}
     * 代管，而那个方法**在隐藏槽已经有东西时直接生成掉落物**——隐藏槽有东西恰恰
     * 是被打断过的常态。所以这一例故意把两边都摆成最坏：隐藏槽扣着一把剑，副手
     * 拿着盾，然后开饭。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void startingAMealNeverDropsWhatSheWasHolding(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnMaid(helper);
        maid.getHideInv().setStackInSlot(0, new ItemStack(Items.IRON_SWORD));
        maid.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        maid.getAvailableBackpackInv()
                .insertItem(0, new ItemStack(Items.BREAD, 4), false);

        new MaidMealAccess().tryStartHungerMeal(maid);

        assertNothingDropped(helper, maid);
        helper.assertTrue(
                maid.getHideInv().getStackInSlot(0).isEmpty(),
                "The sword is still hostage in the hidden slot"
        );
        helper.succeed();
    }

    /**
     * 背包塞满时，她照样吃得下背包里的那口饭。
     *
     * <p>顺序决定的：腾手要往背包里放东西，而这口饭占着的那一格常常就是唯一的
     * 空位——先取饭再腾手，位置就有了；反过来写她会守着满背包的食物饿死。格子
     * 多的背包（小 12、中 24、大 36）更容易长期处在塞满状态，所以这一条随背包
     * 变大而变重要，不是变次要。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aFullPackStillLetsHerEatWhatIsInIt(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnMaid(helper);
        maid.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        maid.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        fillPackAround(maid, 3);
        maid.getAvailableBackpackInv()
                .insertItem(3, new ItemStack(Items.BREAD), false);

        helper.assertTrue(
                new MaidMealAccess().tryStartHungerMeal(maid),
                "A pack whose only free space is the meal itself still has room"
        );
        helper.assertTrue(
                maid.getOffhandItem().is(Items.BREAD),
                "She is not holding the meal: " + maid.getOffhandItem()
        );
        helper.assertTrue(
                packHolds(maid, Items.SHIELD),
                "The displaced shield is not in the pack"
        );
        assertNothingDropped(helper, maid);
        helper.succeed();
    }

    /**
     * 同一条顺序，盾这一侧。
     *
     * <p>盾不可堆叠，取一件必定空出一格，所以"先取盾再腾手"总是成立——而
     * "先腾手再取盾"在满背包下永远拿不到盾。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aFullPackStillLetsHerReachTheShield(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnMaid(helper);
        maid.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BREAD));
        fillPackAround(maid, 2);
        maid.getAvailableBackpackInv()
                .insertItem(2, new ItemStack(Items.SHIELD), false);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 1));

        ScannedThreat threat = measured(maid, zombie);
        ShieldGuard.consider(
                maid,
                threat,
                ThreatField.of(List.of(threat.sample()), 3.0D),
                false
        );

        helper.assertTrue(
                maid.getOffhandItem().canPerformAction(ToolActions.SHIELD_BLOCK),
                "She never got the shield up: " + maid.getOffhandItem()
        );
        helper.assertTrue(
                packHolds(maid, Items.BREAD),
                "The interrupted meal is not in the pack"
        );
        assertNothingDropped(helper, maid);
        zombie.discard();
        helper.succeed();
    }

    /**
     * 插在副手的武器，最终要能到主手。
     *
     * <p>军械表只看主手和背包，所以"能不能拿到主手"等价于"它有没有出现在军械表
     * 里"。收回背包这一步就是让它出现。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aWeaponParkedInTheOffHandBecomesOneSheCanDraw(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnMaid(helper);
        maid.setItemSlot(
                EquipmentSlot.OFFHAND, new ItemStack(Items.DIAMOND_SWORD)
        );
        TlmWeaponScanner scanner = scanner();

        helper.assertTrue(
                scanner.scan(maid).isEmpty(),
                "The arsenal is not supposed to be able to see the off hand"
        );
        new WeaponSwap(scanner).unpark(maid);

        helper.assertTrue(
                packHolds(maid, Items.DIAMOND_SWORD),
                "The parked sword did not reach the pack"
        );
        List<WeaponCandidate> arsenal = scanner.scan(maid);
        helper.assertTrue(
                arsenal.size() == 1 && !arsenal.get(0).inHand(),
                "It never became something she could draw: " + arsenal
        );
        assertNothingDropped(helper, maid);
        helper.succeed();
    }

    /**
     * 不是武器的东西不动。
     *
     * <p>副手那个图腾是她少死一次的全部原因，而图腾在背包里对她毫无作用——原版
     * 只看两只手。把"副手归本模组管"读成"副手归本模组清空"就会净亏一条命。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aTotemInTheOffHandIsLeftWhereSheNeedsIt(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnMaid(helper);
        maid.setItemSlot(
                EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING)
        );

        new WeaponSwap(scanner()).unpark(maid);

        helper.assertTrue(
                maid.getOffhandItem().is(Items.TOTEM_OF_UNDYING),
                "The totem was taken out of the hand that uses it"
        );
        helper.succeed();
    }

    /**
     * 副手有东西不再等于这一局没有盾。
     *
     * <p>走的是生产里的那个顺序：先 {@code unpark} 把武器收回背包，再让盾去要
     * 那一格。原先第二步见副手非空就直接放弃，于是一把插着的剑让她整局赤手承伤。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void anOccupiedOffHandNoLongerCostsHerTheShield(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnMaid(helper);
        maid.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_SWORD));
        maid.getAvailableBackpackInv()
                .insertItem(0, new ItemStack(Items.SHIELD), false);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 1));

        new WeaponSwap(scanner()).unpark(maid);
        ScannedThreat threat = measured(maid, zombie);
        ShieldGuard.consider(
                maid,
                threat,
                ThreatField.of(List.of(threat.sample()), 3.0D),
                false
        );

        helper.assertTrue(
                maid.getOffhandItem().canPerformAction(ToolActions.SHIELD_BLOCK),
                "She never got the shield up: " + maid.getOffhandItem()
        );
        helper.assertTrue(
                packHolds(maid, Items.IRON_SWORD),
                "The displaced sword is not in the pack"
        );
        assertNothingDropped(helper, maid);
        // 自己收走。收场阶段是**杀**掉结构里的实体，而僵尸一死就掉腐肉——腐肉
        // 是食物，会挂在广告板上被隔壁那格结构的感知测试读到。
        zombie.discard();
        helper.succeed();
    }

    private static EntityMaid spawnMaid(GameTestHelper helper) {
        return helper.spawn(InitEntities.MAID.get(), new BlockPos(1, 2, 1));
    }

    private static TlmWeaponScanner scanner() {
        return new TlmWeaponScanner(RangedWeaponRecognizer.NONE);
    }

    private static boolean packHolds(EntityMaid maid, Item item) {
        IItemHandler pack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < pack.getSlots(); slot++) {
            if (pack.getStackInSlot(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    /** 每一格都塞满，且塞的是不可堆叠的东西——留一格就等于没塞满。 */
    private static void fillPack(EntityMaid maid) {
        fillPackAround(maid, -1);
    }

    /** 除了留出的那一格，其余塞满。留出的那一格由调用方决定摆什么。 */
    private static void fillPackAround(EntityMaid maid, int free) {
        IItemHandler pack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < pack.getSlots(); slot++) {
            if (slot == free) {
                continue;
            }
            pack.insertItem(slot, new ItemStack(Items.NETHERITE_HELMET), false);
        }
    }

    /**
     * 地上一件都不能有。
     *
     * <p>这一句才是这组测试真正在守的东西：上面每一例的"成功"都可以用"扔出去"
     * 达成，而扔出去在尸潮里等于永久损失。
     */
    private static void assertNothingDropped(
            GameTestHelper helper, EntityMaid maid
    ) {
        List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, maid.getBoundingBox().inflate(12.0D)
        );
        helper.assertTrue(
                dropped.isEmpty(),
                "Something hit the ground: " + dropped.stream()
                        .map(entity -> entity.getItem().toString())
                        .toList()
        );
    }

    private static ScannedThreat measured(EntityMaid maid, Zombie target) {
        return new ScannedThreat(target, new ThreatSample(
                maid.distanceTo(target),
                ThreatProfile.strikeDamage(target),
                ThreatProfile.reach(target, maid),
                ThreatProfile.attackPeriod(target),
                target.getHealth(),
                false,
                ThreatProfile.closingSpeed(maid, target),
                ThreatRelation.ATTACKING_MAID
        ));
    }
}
