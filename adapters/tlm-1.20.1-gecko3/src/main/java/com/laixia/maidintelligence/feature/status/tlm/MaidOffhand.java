package com.laixia.maidintelligence.feature.status.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * The off hand, as a slot this mod is willing to write to.
 *
 * <p>副手在本体那边是个**只能写一次的槽**：任何一条把东西放进去的路，都没有配
 * 套的把东西拿出来的路，而唯一的清空动作藏在"用完一件物品"的回调里。于是本模组
 * 每一处想用副手的地方，都被迫在"什么都不做"和"可能把玩家的东西扔在地上"之间
 * 二选一。这个类是第三个选项。
 *
 * <p>规则只有一条，也是这个类存在的全部理由：**腾空只走背包，整份放得下才放，
 * 放不下就一动不动。**本模组不产生掉落物。
 *
 * <p>为什么必须自己来，而不是用本体现成的两个方法——两个都会掉东西：
 *
 * <ul>
 *   <li>{@code memoryHandItemStack} 把副手物品存进隐藏槽，而**存之前如果那一格
 *       已经有东西，它直接生成 ItemEntity 扔掉**；
 *   <li>{@code backCurrentHandItemStack} 把副手塞回背包，**塞不下的部分同样扔
 *       在地上**。
 * </ul>
 *
 * <p>这不是理论风险，是那一对方法的设计前提：它们假定"存"和"还"总是成对发生，
 * 由 {@code completeUsingItem} 收尾。战斗会打断进食，于是这一对就断了——盾滞留
 * 在隐藏槽、食物滞留在副手，下一顿饭的"存"撞上没人取走的那一格，玩家的盾就落
 * 地了。{@link #recoverStranded} 修的就是这个断口。
 */
public final class MaidOffhand {
    private MaidOffhand() {
    }

    /**
     * Empty the off hand without ever dropping what was in it.
     *
     * <p>调用方拿到 true 之后才可以往副手里写；拿到 false 就必须放弃这一次意图。
     * 失败是常态而不是异常：背包塞不下、或者她正在用副手那件东西，都算失败，而
     * 两种情况下正确的行为都是"这一 tick 不换"。
     *
     * @return whether the off hand is now empty
     */
    public static boolean vacate(EntityMaid maid) {
        ItemStack held = maid.getOffhandItem();
        if (held.isEmpty()) {
            return true;
        }
        // 她正举着盾或正吃着东西。抢走它等于替她打断她自己的动作，而打断进食正是
        // 本体那条掉落路径的触发条件——这里不制造它。
        if (maid.isUsingItem()
                && maid.getUsedItemHand() == InteractionHand.OFF_HAND) {
            return false;
        }
        if (!stow(maid, held)) {
            return false;
        }
        maid.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        return true;
    }

    /**
     * Take back whatever an interrupted meal left in the host's hidden slot.
     *
     * <p>本体把副手物品临时存进 {@code hideInv} 第 0 格，只有走完
     * {@code completeUsingItem} 才会还回来。这一顿饭被打断（战斗里每次换武器都
     * 会打断）就再也没人还，而下一次"存"会把这一格里的东西扔掉。
     *
     * <p>所以在每一次可能触发"存"的动作之前，先把这一格清干净：副手空着就还回
     * 副手，本体自己也是这么还的；副手被占就先进背包，背包塞不下就原样留着——
     * 留在隐藏槽里至少东西还在，而这一格有东西时本模组不会再去写它。
     */
    public static void recoverStranded(EntityMaid maid) {
        ItemStack stranded = maid.getHideInv().getStackInSlot(0);
        if (stranded.isEmpty()) {
            return;
        }
        // 正在用东西 = 这一对"存/还"还没走完，那不是滞留，是进行中的一顿饭。
        if (maid.isUsingItem()) {
            return;
        }
        if (maid.getOffhandItem().isEmpty()) {
            maid.setItemSlot(
                    EquipmentSlot.OFFHAND,
                    maid.getHideInv().extractItem(0, stranded.getCount(), false)
            );
            return;
        }
        if (stow(maid, stranded)) {
            maid.getHideInv().extractItem(0, stranded.getCount(), false);
        }
    }

    /**
     * Put a stack into the pack, all of it or none of it.
     *
     * <p>先模拟。整份放不下就一份都不放，因为"放一半"意味着剩下的一半没有归宿，
     * 而没有归宿的那一半在本体的写法里就是掉在地上的那一半。
     *
     * <p>物品在这里存在极短的重复窗口：先按副本写进背包，再由调用方清空来源。
     * 两步之间没有 tick 边界，也没有别的代码跑得进来，而顺序反过来（先清空再放）
     * 才是真正危险的——那样一旦放不下，东西就凭空没了。
     */
    private static boolean stow(EntityMaid maid, ItemStack stack) {
        IItemHandler pack = maid.getAvailableBackpackInv();
        ItemStack leftover = ItemHandlerHelper.insertItemStacked(
                pack, stack.copy(), true
        );
        if (!leftover.isEmpty()) {
            return false;
        }
        ItemHandlerHelper.insertItemStacked(pack, stack.copy(), false);
        return true;
    }
}
