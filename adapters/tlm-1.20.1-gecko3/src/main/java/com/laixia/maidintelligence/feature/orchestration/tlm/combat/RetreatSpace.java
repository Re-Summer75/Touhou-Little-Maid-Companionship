package com.laixia.maidintelligence.feature.orchestration.tlm.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;

/**
 * Whether there is ground behind her to give.
 *
 * <p>Kiting is only a strategy where it can be carried out. Backing away with a
 * wall behind her spends the fight walking on the spot, and the target keeps
 * hitting her the whole time — so "can she actually retreat" has to be an input
 * to the stance decision, not an assumption baked into it.
 *
 * <p>A cheap probe rather than a path query: this is consulted every tick of
 * every fight, and pathfinding a retreat that will be recomputed next tick
 * anyway costs far more than it tells us.
 */
public final class RetreatSpace {
    /**
     * How far behind her to look.
     *
     * <p>Roughly one stride, used only when the caller has no particular
     * distance in mind. Anything that knows how far it wants to fall back
     * should say so, since room for one step implies nothing about room for
     * four.
     */
    private static final double PROBE_DISTANCE = 3.0D;

    /**
     * Speed edge she needs before giving ground is worth doing.
     *
     * <p>A pursuer gets its own bonus while chasing, so merely matching it on
     * paper still ends with her caught mid-shot.
     */
    private static final double PURSUIT_MARGIN = 1.15D;

    private RetreatSpace() {
    }

    /**
     * Whether backing away while shooting is something she can carry out.
     *
     * <p>Two ways it fails and both look identical from inside the decision:
     * there is nowhere to go, or there is somewhere to go but the target
     * arrives first. Answering only the first — which asking about walls alone
     * does — keeps her at range against anything at all in open ground, so she
     * draws a blade roughly never.
     *
     * <p>The speed question is asked of whatever is actually in front of her,
     * not of a list of mobs written down here, so a slow one can be kited and
     * a fast one is met with steel without either being named.
     */
    public static boolean canGiveGround(
            EntityMaid maid,
            LivingEntity victim,
            double speedModifier,
            double blocksNeeded
    ) {
        if (cornered(maid, victim.position(), blocksNeeded)) {
            return false;
        }
        double mine = maid.getAttributeValue(Attributes.MOVEMENT_SPEED)
                * speedModifier;
        double theirs = victim.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return mine > theirs * PURSUIT_MARGIN;
    }

    /**
     * The point {@code distance} blocks directly away from {@code threat}.
     *
     * <p>Deliberately not "towards her owner": leading a pack of hostiles to
     * the person she is protecting is worse than the hit she is avoiding.
     */
    public static Vec3 awayFrom(Vec3 from, Vec3 threat, double distance) {
        Vec3 escape = from.subtract(threat);
        if (escape.lengthSqr() < 1.0E-4D) {
            // Standing exactly on it; any direction beats dividing by zero.
            escape = new Vec3(1.0D, 0.0D, 0.0D);
        }
        return from.add(escape.normalize().scale(distance));
    }

    /** Whether one stride back is blocked, for callers with no distance in mind. */
    public static boolean cornered(EntityMaid maid, Vec3 threat) {
        return cornered(maid, threat, PROBE_DISTANCE);
    }

    /**
     * Whether backing up {@code blocksNeeded} blocks is blocked.
     *
     * <p>Walked one block at a time rather than sampling the far end alone.
     * Room at the destination says nothing about the wall in between, and a
     * retreat that discovers the wall halfway is a retreat that stops with the
     * target still closing — which is indistinguishable, to whoever is
     * watching, from her deciding to stand there.
     */
    public static boolean cornered(
            EntityMaid maid,
            Vec3 threat,
            double blocksNeeded
    ) {
        Level level = maid.level();
        Vec3 from = maid.position();
        double needed = Math.max(1.0D, blocksNeeded);
        for (double step = 1.0D; step <= needed; step += 1.0D) {
            if (!standable(
                    level, BlockPos.containing(awayFrom(from, threat, step))
            )) {
                return true;
            }
        }
        return false;
    }

    /** Whether she could occupy this block: body fits, and there is a floor. */
    private static boolean standable(Level level, BlockPos pos) {
        // Her body has to fit through, not just her feet.
        for (int height = 0; height < 2; height++) {
            if (!passable(level, pos.above(height))) {
                return false;
            }
        }
        // And there has to be something to land on. Backing off a ledge is a
        // worse outcome than the hit she was avoiding, so an open drop counts
        // as cornered just like a wall does.
        return !passable(level, pos.below());
    }

    private static boolean passable(Level level, BlockPos pos) {
        return level.getBlockState(pos)
                .isPathfindable(level, pos, PathComputationType.LAND);
    }
}
