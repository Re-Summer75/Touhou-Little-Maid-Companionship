package com.laixia.maidintelligence.feature.orchestration.tlm.ambient;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionAlertness;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon
        .WeaponStowPolicy;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal
        .RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal
        .TlmWeaponScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception
        .TlmAlertness;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Map;
import java.util.function.Predicate;
import java.util.WeakHashMap;

/**
 * 安静久了就把武器收起来。
 *
 * <p>{@link WeaponStowPolicy} 早就写好了，玩家文档里也写着"周围安静满 200 tick 后
 * 收回背包"——而**生产代码里一个调用都没有**，所以她一旦拔刀就再也不放下。这是本仓库
 * 第三次出现"设计完整、验证齐全、没人调用"（前两次是处境四档和许可矩阵），
 * {@code docs/architecture/behavior-spec.md} 就是为此写的。
 *
 * <p>"安静"直接问处境轴：{@link CompanionAlertness#CALM} 才算安静。这样"她什么时候
 * 算没事了"只有一个答案，而不是这里再数一遍敌人。
 *
 * <p>而处境只数**敌对生物**——牛、村民、别的女仆都不算。所以"周围有东西"不会让她
 * 一直握着刀：站在牧场中央的女仆照样会把刀收起来。敌意判定不看物种名，见
 * {@code ThreatProfile}。
 *
 * <p>要求的是**安静持续了多久**，不是"此刻没有敌人"。怪是成波来的，两波之间收刀
 * 意味着下一波要边挨打边拔刀——这一条是策略自己写下的理由，这里只负责给它计时。
 */
public final class TlmWeaponStow {
    private final TlmWeaponScanner weapons =
            new TlmWeaponScanner(RangedWeaponRecognizer.NONE);

    /** 每只女仆最后一次**不**平静是什么时候。 */
    private final Map<EntityMaid, Long> lastAlarm = new WeakHashMap<>();

    private TlmWeaponStow() {
    }

    public static TlmWeaponStow create() {
        return new TlmWeaponStow();
    }

    /** 每 tick 一次，由 {@code MaidIntentBehavior} 的 preTick 驱动。 */
    public void tick(EntityMaid maid, long gameTime) {
        if (!maid.isAlive()) {
            return;
        }
        boolean calm = TlmAlertness.of(maid) == CompanionAlertness.CALM;
        if (!calm) {
            lastAlarm.put(maid, gameTime);
            return;
        }
        // 没见过她之前不能当成"已经安静很久"：那会让她一进世界就把刚拿的武器
        // 收起来。第一次见到就从此刻开始计时。
        Long since = lastAlarm.putIfAbsent(maid, gameTime);
        if (since == null) {
            return;
        }
        int quiet = (int) Math.min(Integer.MAX_VALUE, gameTime - since);
        stowIf(maid, InteractionHand.MAIN_HAND, quiet, this::isArm);
        // 副手单独收一次。盾**不是**武器种类的一种（`classifyFor` 认不出它），所以
        // 少了这一句的表现是刀收了、盾还挂着——比两样都拿着更怪。
        stowIf(maid, InteractionHand.OFF_HAND, quiet, TlmWeaponStow::isShield);
    }

    /**
     * 主手那一样值不值得收：武器算，盾也算。
     *
     * <p>盾通常在副手，但没有任何东西拦着她把盾拿在主手里，而"她握着盾站在牧场中央"
     * 和握着刀一样不像个陪伴。
     */
    private boolean isArm(ItemStack stack) {
        return weapons.classifyFor(stack) != null || isShield(stack);
    }

    /** 判据问物品自己，所以模组的盾自动算数。 */
    private static boolean isShield(ItemStack stack) {
        return !stack.isEmpty()
                && stack.canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    private static void stowIf(
            EntityMaid maid,
            InteractionHand hand,
            int quietTicks,
            Predicate<ItemStack> worthStowing
    ) {
        ItemStack held = maid.getItemInHand(hand);
        if (!WeaponStowPolicy.INSTANCE.shouldStow(
                quietTicks, false, worthStowing.test(held)
        )) {
            return;
        }
        stow(maid, hand, held);
    }

    /**
     * 收进背包，**收不下就继续拿着**。
     *
     * <p>宁可拿着也不丢地上：一件掉在地上的武器等于她自己解除了自己的武装，而这条
     * 规则本来只是想让她看起来像个陪伴而不是卫兵。背包满了只是这一次收不起来，不是
     * 一个需要靠丢东西解决的问题——她照样能在需要时换武器，因为换装走的是**交换**
     * （{@code TaskEquipUtil}），不需要背包有空位。
     *
     * <p>先模拟再落地：{@code insertItem} 可以只塞进去一部分，那样手里剩一半、包里
     * 多一半，而"收起来"这件事没有一半的形态。
     */
    private static void stow(
            EntityMaid maid, InteractionHand hand, ItemStack held
    ) {
        var pack = maid.getAvailableBackpackInv();
        if (!ItemHandlerHelper.insertItem(pack, held.copy(), true).isEmpty()) {
            return;
        }
        ItemHandlerHelper.insertItem(pack, held.copy(), false);
        maid.setItemInHand(hand, ItemStack.EMPTY);
    }
}
