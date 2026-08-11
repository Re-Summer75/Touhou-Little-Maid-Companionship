package com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 落下来那一刀值多少——与 {@link JumpStrike}"什么时候离地"分开的两件事。
 *
 * <p>和横扫欠的是同一笔账：暴击住在 {@code Player#attack} 里，别处没有，所以一个
 * 正在下落的女仆砍中目标时，拿到的是跳跃攻击的几何、却没有它的回报。
 *
 * <p>这一半从来没出过问题——五轮失败全在"什么时候跳"那一半。它也不只服务跳劈：
 * 她从台阶上掉下来、被击退到空中时砍中，同样该算暴击，因为玩家就是这样。
 */
public final class CriticalBlow {
    /** Multiplier vanilla pays a falling attacker, on base damage only. */
    private static final double CRITICAL_MULTIPLIER = 0.5D;

    private static final UUID CRITICAL_MODIFIER_ID =
            UUID.fromString("6f3a1b52-6b8c-4a1e-9d20-0d3c8a5f7e41");

    private CriticalBlow() {
    }

    /**
     * Whether this blow lands as a critical.
     *
     * <p>Vanilla's own conditions, copied rather than approximated: falling,
     * off the ground, not on a ladder, not swimming, not blinded and not a
     * passenger. Approximating them would give her a jump attack in situations
     * a player would not get one, and the point is that her sword works like
     * his.
     */
    public static boolean falling(EntityMaid maid) {
        return maid.fallDistance > 0.0F
                && !maid.onGround()
                && !maid.onClimbable()
                && !maid.isInWater()
                && !maid.hasEffect(MobEffects.BLINDNESS)
                && !maid.isPassenger();
    }

    /**
     * Pay the blow at critical strength, then put the attribute back.
     *
     * <p>Through a transient attribute modifier because {@code doHurtTarget}
     * works out the damage itself and takes no argument for it. Vanilla
     * multiplies only the attribute portion and leaves the enchantment bonus
     * alone, and a modifier on {@code ATTACK_DAMAGE} lands in exactly that
     * place — so sharpness is not quietly multiplied along with the arm.
     *
     * <p>Removed in a finally: an attribute left boosted because something
     * threw mid-swing is a maid who hits like that forever.
     */
    public static void strike(
            EntityMaid maid,
            LivingEntity victim,
            Runnable blow
    ) {
        AttributeInstance damage =
                maid.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage == null) {
            blow.run();
            return;
        }
        AttributeModifier critical = new AttributeModifier(
                CRITICAL_MODIFIER_ID,
                "maid critical",
                CRITICAL_MULTIPLIER,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        damage.addTransientModifier(critical);
        try {
            blow.run();
        } finally {
            damage.removeModifier(critical);
        }
        JumpLedger.noteCrit(maid);
        announce(maid, victim);
    }

    /** The sparks and the sound, which are the whole of how a player reads it. */
    private static void announce(EntityMaid maid, LivingEntity victim) {
        maid.level().playSound(
                null,
                maid.getX(), maid.getY(), maid.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.NEUTRAL,
                1.0F,
                1.0F
        );
        if (maid.level() instanceof ServerLevel server) {
            server.sendParticles(
                    ParticleTypes.CRIT,
                    victim.getX(),
                    victim.getY(0.5D),
                    victim.getZ(),
                    10, 0.2D, 0.2D, 0.2D, 0.1D
            );
        }
    }
}
