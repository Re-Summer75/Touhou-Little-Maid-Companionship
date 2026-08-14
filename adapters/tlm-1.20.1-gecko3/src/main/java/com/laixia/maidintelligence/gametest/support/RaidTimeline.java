package com.laixia.maidintelligence.gametest.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatField;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.MeleeSwing;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ThreatProfile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 一 tick 一行：她在哪、准备往哪走、正在做什么，以及每一个敌人在哪。
 *
 * <p>结果读数（击杀、挨打、存活）说不清**她为什么会死**。同一个"死于第 178 tick"
 * 底下可以是四件完全不同的事：站在沟里出不来、决定撤退但每一步都被墙挡回来、
 * 判定为交战却始终够不着、或者四个敌人根本是同时到的。这四种在 `hurt/dealt` 上长得
 * 一模一样，而它们要改的地方毫无交集。
 *
 * <p>此前这一局只打坐标，而且二十 tick 一次——她死在第 129 tick 时全程只有六个采样点，
 * 每个点还只说她在哪，不说她想去哪。所以这里把三样东西并到同一行上：
 *
 * <ul>
 *   <li><b>她在哪</b>——位置、是否着地、朝向、实际速度与方向；</li>
 *   <li><b>她准备往哪走</b>——移动目标（谁写的、多远、往哪个方位）与移动控制这一
 *       tick 真正在操纵的点。两者不一致本身就是一类 bug：目标写下了、导航没走；</li>
 *   <li><b>她在做什么</b>——意图、警觉、风险裁决、姿态、退路余量、手里拿着什么、
 *       攻击冷却到哪儿了。</li>
 * </ul>
 *
 * <p>方位一律用 MC 的偏航角，和 {@code yaw} 那一列同一套刻度，所以"脸朝哪"与
 * "脚往哪"可以直接相减——举盾倒退时这两个数应当差约一百八十度，那一列本身就是
 * 那条特性的读数。
 */
public final class RaidTimeline {
    private RaidTimeline() {
    }

    /**
     * 组一行。
     *
     * @param index 第几局
     * @param tick  这一局的第几 tick
     * @param foes  这一局的全部敌人，按固定顺序，死了也留在行里
     */
    public static String line(
            int index, long tick, EntityMaid maid, LivingEntity[] foes
    ) {
        StringBuilder line = new StringBuilder(320);
        line.append(String.format(
                "RAID#%d t=%3d hp=%.1f+%.1f pos=(%.1f,%.1f,%.1f) g=%s "
                        + "yaw=%d vel=%.2f@%s",
                index, tick, maid.getHealth(), maid.getAbsorptionAmount(),
                maid.getX(), maid.getY(), maid.getZ(),
                maid.onGround() ? "y" : "N",
                (int) wrap(maid.getYRot()),
                horizontal(maid.getDeltaMovement()),
                bearingOf(maid.getDeltaMovement().x, maid.getDeltaMovement().z)
        ));
        line.append(' ').append(headedFor(maid));
        line.append(' ').append(steering(maid));
        line.append(" nav=").append(CombatProbe.navigation(maid));
        line.append(" act=").append(CombatProbe.intent(maid));
        line.append(" alert=").append(CombatProbe.alertness(maid));
        line.append(' ').append(decision(maid));
        line.append(' ').append(hands(maid));
        for (int slot = 0; slot < foes.length; slot++) {
            line.append(' ').append(foe(slot, foes[slot], maid));
        }
        return line.toString();
    }

    /**
     * 她被**告知**要去哪：方位、距离、到多近算到、什么速度。
     *
     * <p>这是"准备移动方向"里长期的那一半——差事骨架与战斗站位写下的东西。它和
     * 下面那个即时的转向点分开列，因为两者不一致正是一类实实在在的毛病：目标写在
     * 记忆里、导航却在走别的地方，或者根本没走。
     */
    private static String headedFor(EntityMaid maid) {
        WalkTarget walk = maid.getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (walk == null) {
            return "goal=-";
        }
        Vec3 to = walk.getTarget().currentPosition();
        Vec3 away = to.subtract(maid.position());
        return String.format(
                "goal=%s/%.1f@%d,v%.2f",
                bearingOf(away.x, away.z),
                Math.sqrt(away.x * away.x + away.z * away.z),
                walk.getCloseEnoughDist(),
                walk.getSpeedModifier()
        );
    }

    /**
     * 移动控制这一 tick 真正在把她往哪儿推。
     *
     * <p>比移动目标近一层：宿主的 {@code MaidMoveControl} 每 tick 从这个点算出偏航
     * 与前进量。"目标在东、转向在西"意味着导航正在绕路，而那在结果读数里完全看不见。
     */
    private static String steering(EntityMaid maid) {
        Vec3 wanted = new Vec3(
                maid.getMoveControl().getWantedX(),
                maid.getMoveControl().getWantedY(),
                maid.getMoveControl().getWantedZ()
        );
        Vec3 away = wanted.subtract(maid.position());
        double flat = Math.sqrt(away.x * away.x + away.z * away.z);
        if (flat < 0.05D) {
            return "steer=-";
        }
        return String.format(
                "steer=%s/%.1f", bearingOf(away.x, away.z), flat
        );
    }

    /** 裁决、姿态、退路、方位场——她为什么走这一步。 */
    private static String decision(EntityMaid maid) {
        List<ScannedThreat> seen = CombatProbe.scan(maid);
        List<Vec3> crowd = CombatProbe.crowd(seen);
        if (seen.isEmpty()) {
            return "verdict=- stance=- esc=- open=- sector=-";
        }
        ThreatField field = CombatProbe.field(maid, seen);
        boolean canOpen = CombatProbe.canOpenGround(maid, seen.get(0).entity());
        return String.format(
                "verdict=%s stance=%s esc=%.1f open=%s sector=%s",
                CombatProbe.verdict(maid, field, canOpen),
                CombatProbe.stance(
                        maid, seen.get(0).sample(), field, canOpen
                ),
                CombatProbe.escapeReach(maid, crowd),
                canOpen ? "y" : "N",
                CombatProbe.bearing(maid, crowd)
        );
    }

    /**
     * 手里拿着什么、在用什么、这一刀准备好了没有。
     *
     * <p>{@code ready} 是"够得着也不冷却却没挥"和"根本没机会挥"的分界，而结尾那
     * 一行的 `readyInRange` 只给出总数——总数说不出那些 tick 落在哪一段。
     */
    private static String hands(EntityMaid maid) {
        return String.format(
                "hand=%s/%s use=%s ready=%s",
                name(maid.getMainHandItem()),
                name(maid.getOffhandItem()),
                maid.isUsingItem()
                        ? name(maid.getUseItem()) + ":"
                                + maid.getTicksUsingItem()
                        : "-",
                MeleeSwing.recovered(maid) ? "y" : "N"
        );
    }

    /**
     * 一个敌人：在哪、离多远、在追谁，以及她给它记的够到距离。
     *
     * <p>最后一列是**声明值 + 学到的差额**。她站得太远打不着、还是站得太近挨了打，
     * 都取决于这个数，而它现在会随她挨打而变——不打出来的话，"她为什么突然不肯靠近"
     * 在时间线上是无迹可寻的。
     */
    private static String foe(int slot, LivingEntity foe, EntityMaid maid) {
        if (!foe.isAlive()) {
            return "v" + slot + "=DEAD";
        }
        Vec3 away = foe.position().subtract(maid.position());
        String hunting = "-";
        if (foe instanceof Mob mob && mob.getTarget() != null) {
            hunting = mob.getTarget() == maid ? "HER" : "other";
        }
        return String.format(
                "v%d=(%.1f,%.1f,%.1f)/%s d=%.1f on=%s rch=%.2f+%.2f",
                slot, foe.getX(), foe.getY(), foe.getZ(),
                bearingOf(away.x, away.z), foe.distanceTo(maid), hunting,
                ThreatProfile.declaredReach(foe, maid),
                ThreatProfile.reach(foe, maid)
                        - ThreatProfile.declaredReach(foe, maid)
        );
    }

    private static String name(ItemStack stack) {
        if (stack.isEmpty()) {
            return "-";
        }
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).getPath();
        return path;
    }

    private static double horizontal(Vec3 motion) {
        return Math.sqrt(motion.x * motion.x + motion.z * motion.z);
    }

    /** 方向按 MC 偏航角表达，好和 {@code yaw} 那一列直接相减。 */
    private static String bearingOf(double dx, double dz) {
        if (dx * dx + dz * dz < 1.0E-6D) {
            return "--";
        }
        return String.valueOf(
                (int) wrap((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D))
        );
    }

    private static float wrap(float degrees) {
        float value = degrees % 360.0F;
        if (value > 180.0F) {
            value -= 360.0F;
        }
        if (value <= -180.0F) {
            value += 360.0F;
        }
        return value;
    }
}
