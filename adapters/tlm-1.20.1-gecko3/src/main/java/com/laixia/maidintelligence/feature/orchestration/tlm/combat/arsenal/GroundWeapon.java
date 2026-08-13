package com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * 空手挨追时，地上那件能捡的武器。
 *
 * <p>玩家报告的处境很具体：武器打没了（或者一开始就没带），怪在后面追，而她只会
 * 一直跑——脚边就躺着一把剑也不去捡。
 *
 * <p>不为此另扫一遍世界：掉落物本来就在广告索引里，只是此前只登记"能不能吃"。
 * 现在同一条广告也登记"能不能打"，这里问的就是它。**登记一次、各取所需**，正是
 * 这套索引存在的理由——为每一种需求各写一个扫描器，正是它当初要替掉的东西。
 *
 * <p>只在她**确实空手**时才问。手里有能用的武器时，地上有没有更好的一把不归这里
 * 管：那是换装的判断，而这一条回答的是"她现在有没有还手的可能"。
 */
public final class GroundWeapon {
    /**
     * 一次看几件。
     *
     * <p>三件。广告按商品值排序，所以第一件已经是她够得着的里面最好的；多看两件
     * 只是为了第一件在这一 tick 里刚好被别人捡走或掉进岩浆时还有退路。
     */
    private static final int TOP_K = 3;

    private GroundWeapon() {
    }

    /**
     * 她此刻该去捡的那一件，没有则为 {@code null}。
     *
     * @param perception 掉落物广告索引，与找地上的食物用的是同一份
     */
    public static ItemEntity nearestFor(
            EntityMaid maid,
            TlmAffordancePerceptionService perception
    ) {
        if (perception == null || !hasRoom(maid)) {
            return null;
        }
        List<ItemEntity> found = perception.queryGroundWeapons(
                maid, TOP_K, maid.level().getGameTime()
        );
        for (ItemEntity item : found) {
            if (item.isAlive() && !item.hasPickUpDelay()) {
                return item;
            }
        }
        return null;
    }

    /**
     * 背包还装得下吗。
     *
     * <p>装不下就不该跑过去：捡不起来的一趟只是把她送进对方的触及范围，而她此刻
     * 空着手。这也是玩家点名的那个例外——背包满了就别去。
     *
     * <p>问的是"有没有一格能放得下"，不是"有没有完全空的一格"：半摞的剑也能塞。
     * 判据交给 {@code insertItem} 的模拟，因为容量规则归背包自己，不归这里。
     */
    private static boolean hasRoom(EntityMaid maid) {
        if (maid.getMainHandItem().isEmpty()) {
            // 手是空的，这本来就是她要去捡的原因，也是最直接的一个位置。
            return true;
        }
        IItemHandler pack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < pack.getSlots(); slot++) {
            if (pack.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
