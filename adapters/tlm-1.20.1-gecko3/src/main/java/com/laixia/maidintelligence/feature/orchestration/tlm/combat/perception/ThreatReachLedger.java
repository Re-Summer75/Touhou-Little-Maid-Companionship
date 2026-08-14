package com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatReachMemory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 她从挨打里学到的那一截够到距离。
 *
 * <p>算术在 {@link ThreatReachMemory}；这里只做宿主才能做的两件事：把挨的那一下
 * 折算成一个读数，以及按物种把它记住。
 *
 * <h2>为什么按物种记，而不是按那一只</h2>
 *
 * <p>因为要学的东西正是"这一类东西够多远"，而教会她的那一只多半已经死了。按个体记
 * 等于每一只都要重新拿血换一次。
 *
 * <p>个体之间真正的差别不靠这里表达——{@link #surplusFor} 加的是**差额**，而底数
 * 仍然逐个体去问它自己。一只换了长武器、因而如实声明了更长距离的个体，会照它自己
 * 声明的来；只有世界拒绝说出口的那一截才由这份记忆补上。
 *
 * <p>剩下的那种情形——同物种、都不声明、其中一只就是够得更远——学到的是**更远的那
 * 个**，于是她对够得近的那只偏谨慎。这是安全的那一侧，而且它会自己褪掉。
 */
public final class ThreatReachLedger {
    /**
     * 记得住几个物种。
     *
     * <p>有上限，因为这是一张长在内存里、只增不减的表。六十四远多于任何一场仗里
     * 出现的种类，而满了之后先丢已经褪掉的那些。
     */
    private static final int MOST_SPECIES_REMEMBERED = 64;

    private static final Map<EntityType<?>, ThreatReachMemory> LEARNED =
            new ConcurrentHashMap<>();

    private ThreatReachLedger() {
    }

    /**
     * 这个物种比它声明的多够多远，此刻。
     *
     * <p>没学过、或者已经褪干净时为零——那时她照它自己声明的那个数行事，也就是
     * 这套东西存在之前的行为。
     */
    public static double surplusFor(LivingEntity hostile, long now) {
        ThreatReachMemory learned = LEARNED.get(hostile.getType());
        return learned == null ? 0.0D : learned.surplusAt(now);
    }

    /**
     * 她刚被打中了一下。
     *
     * <p>只收近战的那一下：弹射物的伤害来源里"直接实体"是弹丸本身而不是射手，
     * 爆炸则根本不是够到距离的问题（半径衰减能到七格，按近战记会让她再也不敢靠近
     * 任何一个自爆的东西）。两者都在调用方筛掉。
     *
     * @param struckFrom 挨打那一刻两者的实际距离，与威胁采样用的是同一个量
     */
    public static void noteAHit(
            LivingEntity attacker, EntityMaid maid, double struckFrom, long now
    ) {
        double declared = ThreatProfile.declaredReach(attacker, maid);
        EntityType<?> species = attacker.getType();
        ThreatReachMemory before =
                LEARNED.getOrDefault(species, ThreatReachMemory.NOTHING_LEARNED);
        ThreatReachMemory after =
                before.afterBeingStruckFrom(declared, struckFrom, now);
        if (after == before) {
            return;
        }
        LEARNED.put(species, after);
        if (LEARNED.size() > MOST_SPECIES_REMEMBERED) {
            prune(now);
        }
    }

    /** 丢掉已经褪干净的条目。 */
    private static void prune(long now) {
        Iterator<Map.Entry<EntityType<?>, ThreatReachMemory>> entries =
                LEARNED.entrySet().iterator();
        while (entries.hasNext()) {
            if (!entries.next().getValue().worthKeeping(now)) {
                entries.remove();
            }
        }
    }

    /** 忘掉一切。夹具用——一条测试教会她的东西不该漏给下一条。 */
    public static void forgetEverything() {
        LEARNED.clear();
    }
}
