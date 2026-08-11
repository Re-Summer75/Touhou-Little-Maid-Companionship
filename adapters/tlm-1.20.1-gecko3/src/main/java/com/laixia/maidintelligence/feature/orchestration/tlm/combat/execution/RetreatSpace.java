package com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.ai.tlm.OwnerFollowBridge;
import com.laixia.maidintelligence.feature.behavior.domain.perception.BearingField;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

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

    /**
     * The shortest move that counts as retreating at all.
     *
     * <p>Settling for less ground rather than none was the right correction and
     * this is its missing other half. Accepting a single block produced a
     * destination inside her own arrival radius: the host saw her already there,
     * erased the walk target, and the next tick wrote another one-block goal.
     * Measured, that is a walk target appearing and vanishing every other tick,
     * zero distance covered, and hostiles closing from four blocks to one and a
     * half while it happened.
     *
     * <p>So "take what you can get" now has a floor. Below it she is pinned, and
     * pinned has an answer already — turn and swing — which is a far better use
     * of the tick than starting a walk she has finished before it began.
     *
     * <p>A whole number, because the probe walks in whole blocks and can only
     * ever report 1, 2, 3… Set to 2.5 this was unsatisfiable for every request
     * between two and three blocks: the probe returned 2.0, the floor demanded
     * 2.5, and "can she give ground" answered no while a twelve-block escape sat
     * available. Measured as `canOpen NO` beside `escape 12.0` and `spd
     * 0.42/0.35` — every input said yes and the comparison said no.
     */
    public static final double MINIMUM_RETREAT = 2.0D;

    private RetreatSpace() {
    }

    /**
     * Where she should actually back off to, or {@code null} if nowhere.
     *
     * <p>Prefers straight back, settles for the nearest workable angle, and —
     * crucially — settles for <em>less ground</em> rather than for none. An
     * all-or-nothing answer is how a retreat turns into standing still: the
     * caller asks for twelve blocks, no direction offers twelve, and she does
     * nothing at all while something eats her. Four blocks in the second-best
     * direction is a retreat. Only a maid who cannot take one step in any
     * direction is actually pinned, and that is the case the callers who swing
     * instead of running are looking for.
     */
    public static Vec3 escapeTo(
            EntityMaid maid,
            Vec3 threat,
            double distance
    ) {
        return escapeTo(maid, List.of(threat), distance);
    }

    /**
     * The same, but away from a whole crowd rather than from one of them.
     *
     * <p>Retreating from the target alone is what being mobbed exposes: with
     * three hostiles around her, the line directly away from the one she is
     * fighting runs straight into another. Terrain was the only thing ever
     * consulted, and no wall is involved — mobs do not block pathing — so every
     * direction looked equally clear while half of them walked her into a fist.
     * Measured, she withdrew correctly, held the lease correctly, and still
     * could not get further than five blocks.
     *
     * <p>Candidates are therefore scored by the <em>closest</em> hostile at the
     * destination, and the best of those wins. Maximising the nearest distance
     * rather than the total is deliberate: the average is comfortable in the
     * middle of a ring, and the middle of a ring is the one place she must not
     * stand.
     */
    public static Vec3 escapeTo(
            EntityMaid maid,
            List<Vec3> threats,
            double distance
    ) {
        Escape found = escape(maid, threats, distance);
        return found == null ? null : found.to();
    }

    /**
     * Where to go and how far that is, decided once.
     *
     * <p>The distance is carried rather than measured back off the point.
     * Re-deriving it cost the last few bits — a one-block step out of a
     * thirty-degree bearing came back as 0.9999999999999957, which is not one
     * block, and the demand it was compared against was exactly one. Every
     * melee step-out failed on four parts in a quadrillion.
     */
    private record Escape(Vec3 to, double reach) {
    }

    private static Escape escape(
            EntityMaid maid,
            List<Vec3> threats,
            double distance
    ) {
        if (threats.isEmpty()) {
            return null;
        }
        Vec3 from = maid.position();
        BearingField field = survey(maid, threats, distance);
        int bearing = field.safestBearing(required(distance));
        if (bearing < 0) {
            return null;
        }
        // The sector says which way; the exact line within it is still worth
        // finding. Sectors are thirty degrees wide, and aiming at the centre of
        // one can be fifteen degrees off the opening that made it the best
        // sector — enough to walk into the wall beside a doorway. The fan this
        // replaced aimed exactly away from the threat and so never paid that,
        // which is why two close-quarters fixtures regressed the moment the
        // survey started answering on centres alone.
        return refine(maid, from, bearing, distance,
                field.bodyClearance(bearing));
    }

    /**
     * The best line inside a chosen sector: its centre, or either edge.
     *
     * <p>Capped by whoever is standing along it. Terrain is what stops her from
     * walking; a body is what stops the walking from being worth anything, and
     * a caller asking "how much ground can I open" means the second. Reporting
     * only the first is how she committed to twelve-block withdrawals with a
     * vindicator three blocks down the line.
     */
    private static Escape refine(
            EntityMaid maid,
            Vec3 from,
            int bearing,
            double distance,
            double bodies
    ) {
        double centre = BearingField.bearingOf(bearing);
        double half = Math.PI / BearingField.SECTORS;
        Escape best = null;
        for (double offset : new double[] {0.0D, -half, half}) {
            double radians = centre + offset;
            Vec3 direction =
                    new Vec3(Math.cos(radians), 0.0D, Math.sin(radians));
            double reach = Math.min(
                    reachAlong(maid, from, direction, distance), bodies
            );
            if (best == null || reach > best.reach()) {
                best = new Escape(from.add(direction.scale(reach)), reach);
            }
        }
        return best != null && best.reach() > 0.0D ? best : null;
    }

    /**
     * Read the whole circle once, into one comparable picture.
     *
     * <p>This replaced a hand-rolled search: a fan of candidate angles measured
     * from an inverse-distance "away" vector, each candidate scored against the
     * nearest hostile. Every part of that was a private approximation of a
     * direction, and each was tuned on its own — the fan's width had to be
     * widened once it turned out the only gap could lie behind the nearest
     * attacker, the scoring needed a hand-tuned tiebreak so she would not settle
     * for a single safer step, and neither could answer "am I surrounded"
     * without being run and having its failure interpreted.
     *
     * <p>Surveying bearings instead gives all of those from one pass, and gives
     * them to everyone else too: the same field answers where to walk, whether
     * she is boxed in, and whether a gap is wide enough to fit through.
     */
    public static BearingField survey(
            EntityMaid maid,
            List<Vec3> threats,
            double distance
    ) {
        Vec3 from = maid.position();
        BearingField.Builder survey = BearingField.builder();
        for (Vec3 threat : threats) {
            Vec3 gap = new Vec3(
                    threat.x - from.x, 0.0D, threat.z - from.z
            );
            if (gap.lengthSqr() < 1.0E-4D) {
                continue;
            }
            survey.threat(
                    Math.atan2(gap.z, gap.x), gap.length()
            );
        }
        for (int sector = 0; sector < BearingField.SECTORS; sector++) {
            double radians = BearingField.bearingOf(sector);
            Vec3 direction =
                    new Vec3(Math.cos(radians), 0.0D, Math.sin(radians));
            survey.room(
                    radians, reachAlong(maid, from, direction, distance)
            );
        }
        return survey.build();
    }

    /**
     * The reach a request of this size actually has to clear.
     *
     * <p>Floored to whole blocks because the probe steps in whole blocks and
     * can only ever report 1, 2, 3… Comparing a whole-block result against a
     * fractional demand is unsatisfiable by construction: asking for 1.9 and
     * being handed 1.0 reads as "nowhere to go" on completely open ground.
     *
     * <p>That is not hypothetical twice over. At 2.5 it silently disabled every
     * request between two and three blocks; the melee step-out, which asks for
     * about one, was disabled the same way and its test failed on every run.
     * A caller asking for less than the floor means it, and gets it.
     */
    private static double required(double distance) {
        return Math.max(1.0D, Math.min(MINIMUM_RETREAT, Math.floor(distance)));
    }

    /**
     * How far she could actually get along one direction, in whole blocks.
     *
     * <p>One block is the finest thing this can say, so a request smaller than
     * that is answered at one block rather than at nothing. The alternative is
     * an unsatisfiable question: the melee step-out asks for the sliver between
     * her nose and the edge of its reach — measured, 0.93 blocks — the loop
     * never took its first step, every direction reported no room, and "can she
     * step out of range" answered no on completely open ground. She then stood
     * inside a zombie's reach for the whole of every recovery.
     *
     * <p>{@link #required} rounds the demand up to a block for exactly the same
     * reason. Both ends of the comparison have to admit the same resolution, or
     * one of them is always wrong.
     *
     * <p>The owner leash is one of the things that stops her, alongside walls
     * and bodies, and it belongs here rather than on the answer. Applied at the
     * end it would let her pick the bearing with the most open ground and only
     * then discover she may not walk it; applied per bearing, a direction that
     * runs out of leash is simply a direction with less room, and the survey
     * picks around it the same way it picks around a wall.
     */
    static double reachAlong(
            EntityMaid maid,
            Vec3 from,
            Vec3 direction,
            double distance
    ) {
        double leash = OwnerFollowBridge.leashedReach(maid, from, direction);
        double reached = 0.0D;
        double asked = Math.min(Math.max(1.0D, distance), leash);
        // Carried forward so a slope can be followed for several blocks. Judging
        // every step against her starting height would let her climb one stair
        // and then decide the second one is a wall.
        int height = 0;
        int climbed = 0;
        for (double walked = 1.0D; walked <= asked; walked += 1.0D) {
            BlockPos ahead = BlockPos.containing(
                    from.add(direction.scale(walked))
            ).above(height);
            BlockPos landing = footing(maid.level(), ahead);
            if (landing == null) {
                break;
            }
            int rise = landing.getY() - ahead.getY();
            if (rise > 0) {
                climbed += rise;
            }
            height += rise;
            reached = walked;
        }
        // 爬升要收费。这个循环一直在数高度差，却只当成"过不过得去"，于是带坎的
        // 退路和平路报出同样的余地。**一条要爬一格的撤退不是撤退**——净距离是负
        // 的（AirControl.climbCost 约 1.23 格）。收费之后平路自然胜出。
        double taxed = reached - climbed * AirControl.climbCost();
        return Math.min(Math.max(0.0D, taxed), leash);
    }

    /**
     * How much ground she can give in the best available direction.
     *
     * <p>Zero means genuinely boxed in.
     */
    public static double escapeReach(
            EntityMaid maid,
            Vec3 threat,
            double distance
    ) {
        return escapeReach(maid, List.of(threat), distance);
    }

    /** The same, measured against the whole crowd. */
    public static double escapeReach(
            EntityMaid maid,
            List<Vec3> threats,
            double distance
    ) {
        Escape found = escape(maid, threats, distance);
        return found == null ? 0.0D : found.reach();
    }

    /** Unit direction {@code turn} radians off straight-back, in the ground plane. */
    private static Vec3 direction(Vec3 from, Vec3 threat, double turn) {
        Vec3 escape = from.subtract(threat);
        Vec3 flat = new Vec3(escape.x, 0.0D, escape.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            flat = new Vec3(1.0D, 0.0D, 0.0D);
        }
        return turned(flat.normalize(), turn);
    }

    /** A unit direction rotated {@code turn} radians about the vertical. */
    private static Vec3 turned(Vec3 unit, double turn) {
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        return new Vec3(
                unit.x * cos - unit.z * sin,
                0.0D,
                unit.x * sin + unit.z * cos
        );
    }

    /**
     * Whether she is fast enough for giving ground to mean anything.
     *
     * <p>Separated from the terrain question so a caller that has already
     * chosen a destination can ask only the half it still needs. Answering
     * both at once forced callers to pass a distance they no longer cared
     * about, and the two halves then drifted apart.
     */
    public static boolean outpaces(
            EntityMaid maid,
            LivingEntity victim,
            double speedModifier
    ) {
        double mine = maid.getAttributeValue(Attributes.MOVEMENT_SPEED)
                * speedModifier;
        double theirs = victim.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return mine > theirs * PURSUIT_MARGIN;
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
        // Any workable direction, not only straight back — otherwise a maid
        // with three clear sides reports that she is cornered.
        //
        // A worthwhile step is enough to answer yes, even when more was asked
        // for, because giving ground takes whatever is available instead of
        // insisting on the full distance — but a step she finishes on arrival
        // is not one, see MINIMUM_RETREAT. The question this answers is "is
        // retreating possible at all", and the only honest no is a maid who
        // cannot take a single step in any direction — which is exactly the
        // state the callers who swing instead of running are looking for.
        if (escapeReach(maid, victim.position(), blocksNeeded)
                < required(blocksNeeded)) {
            return false;
        }
        return outpaces(maid, victim, speedModifier);
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
        double needed = Math.max(1.0D, blocksNeeded);
        return reachAlong(
                maid,
                maid.position(),
                direction(maid.position(), threat, 0.0D),
                needed
        ) < needed;
    }

    /**
     * Where her feet would end up at this column, or {@code null} if nowhere.
     *
     * <p>A step up or down of one block is walking; only more than that is
     * terrain. Insisting the retreat stay perfectly level made a single raised
     * block — a stair, a path edge, one cobble somebody placed — read as a solid
     * wall, and the answer to "walled in" is to stand still. She was then held
     * in the corner and mobbed by things that had just walked up the same step
     * she had decided was impassable.
     *
     * <p>Returned as a position rather than a yes/no so the caller can carry the
     * height forward and follow a slope for several blocks instead of treating
     * each step as a fresh level surface.
     */
    private static BlockPos footing(Level level, BlockPos at) {
        if (occupiable(level, at)) {
            return at;
        }
        BlockPos up = at.above();
        if (occupiable(level, up)) {
            return up;
        }
        BlockPos down = at.below();
        if (occupiable(level, down)) {
            return down;
        }
        return null;
    }

    /** Whether she could occupy this block: body fits, and there is a floor. */
    private static boolean occupiable(Level level, BlockPos pos) {
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
