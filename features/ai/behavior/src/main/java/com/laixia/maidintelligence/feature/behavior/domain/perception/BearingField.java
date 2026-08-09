package com.laixia.maidintelligence.feature.behavior.domain.perception;

/**
 * What lies in each direction around her, as one aggregate.
 *
 * <p>Perception was a list of things and a box. That answers "what is near me"
 * and cannot answer "which way is clear", which is the question every retreat,
 * every stand-off and every judgement of being cornered actually asks. Each of
 * those grew its own private answer instead: a fan of candidate angles here, an
 * inverse-distance sum there, a terrain probe somewhere else — three
 * approximations of one missing concept, each tuned separately and each wrong in
 * a different situation.
 *
 * <p>So directions are first class. The circle is divided into sectors, and each
 * sector carries how much threat presses from it and how far she could travel
 * into it. Everything downstream reads sectors instead of inventing geometry:
 * "where do I go" is the best sector, "am I surrounded" is whether any sector is
 * free, "is this a corner" is whether any sector has room.
 *
 * <p>Immutable and free of game types. The adapter fills it; the policies only
 * compare numbers.
 */
public final class BearingField {
    /**
     * How finely the circle is cut.
     *
     * <p>Twelve gives thirty-degree sectors: fine enough that a gap between two
     * hostiles standing shoulder to shoulder is visible, coarse enough that one
     * creature does not smear across half the map. Below eight, a single mob
     * blocks a quarter of the world; above sixteen, sectors start being narrower
     * than she is wide and a gap she cannot fit through looks open.
     */
    public static final int SECTORS = 12;

    private static final double TWO_PI = Math.PI * 2.0D;

    private final double[] pressure;
    private final double[] room;

    private BearingField(double[] pressure, double[] room) {
        this.pressure = pressure;
        this.room = room;
    }

    /** An empty world: nothing in any direction, nowhere measured. */
    public static BearingField empty() {
        double[] clear = new double[SECTORS];
        java.util.Arrays.fill(clear, Double.POSITIVE_INFINITY);
        return new BearingField(clear, new double[SECTORS]);
    }

    /** Start accumulating one. */
    public static Builder builder() {
        return new Builder();
    }

    /** Which sector a bearing in radians falls in. */
    public static int sectorOf(double radians) {
        double wrapped = radians % TWO_PI;
        if (wrapped < 0.0D) {
            wrapped += TWO_PI;
        }
        return (int) (wrapped / TWO_PI * SECTORS) % SECTORS;
    }

    /** The centre bearing of a sector, in radians. */
    public static double bearingOf(int sector) {
        return (sector + 0.5D) / SECTORS * TWO_PI;
    }

    /** How far she could travel this way before terrain stops her. */
    public double room(int sector) {
        return room[Math.floorMod(sector, SECTORS)];
    }

    /**
     * How far she could usefully travel this way, in blocks.
     *
     * <p>The lesser of the ground available and the distance to whatever is
     * standing in it — a direction stops being good either when it runs out of
     * floor or when she reaches what she was avoiding, whichever comes first.
     *
     * <p>Both terms are distances on purpose. A first cut scored "room minus
     * threat pressure" with pressure as an inverse-distance weight; room ran to
     * twelve while pressure sat near a quarter, so room decided everything and
     * "safest" quietly meant "most open" — including straight through the three
     * things chasing her. Measured, it doubled the damage she took. Quantities
     * that get compared have to be in the same unit.
     *
     * <p>Neighbours are folded in at their own value, so a one-sector slot
     * between two hostiles is judged by them rather than by the empty line
     * between: she has width, and a gap she cannot fit through is not a gap.
     */
    public double clearance(int sector) {
        return Math.min(room(sector), bodyClearance(sector));
    }

    /**
     * How far this way she gets before meeting somebody, ignoring terrain.
     *
     * <p>Separate from {@link #clearance} because the two limits are not
     * interchangeable to a caller deciding whether to run. Open floor with
     * something standing on it is not ground she can have: a retreat reported
     * as twelve blocks, down a line with a vindicator three blocks along it, is
     * a retreat that ends in three blocks and a hit. Measured, she withdrew
     * with {@code escape 12.0} while the pack closed from 3.4 blocks to 0.7 and
     * took thirty-odd damage doing it — every tick correctly deciding to leave,
     * on a number that was not true.
     */
    public double bodyClearance(int sector) {
        return Math.min(
                threatDistance(sector),
                Math.min(
                        threatDistance(sector - 1),
                        threatDistance(sector + 1)
                )
        );
    }

    /** Distance to the nearest hostile lying along a bearing, or infinity. */
    public double threatDistance(int sector) {
        return pressure[Math.floorMod(sector, SECTORS)];
    }

    /**
     * The direction that most deserves to be walked into.
     *
     * @param minimumRoom clearances below this are not worth walking
     * @return the sector, or {@code -1} when nothing clears the bar
     */
    public int safestBearing(double minimumRoom) {
        int best = -1;
        double bestClear = Double.NEGATIVE_INFINITY;
        double bestQuiet = Double.NEGATIVE_INFINITY;
        for (int sector = 0; sector < SECTORS; sector++) {
            double clear = clearance(sector);
            if (clear < minimumRoom) {
                continue;
            }
            // Ties broken by how far the nearest body in that direction is.
            //
            //
            // Without it the first maximum wins, which is sector zero — due
            // east, chosen for no reason at all. In an open field clearances
            // differ and it never shows; in a small room the walls cap every
            // direction at the same distance, every clearance ties, and she
            // walks east into whatever happens to be standing there. That is
            // how the melee step-out started closing on its target instead of
            // stepping off it.
            double quiet = bodyClearance(sector);
            if (clear > bestClear
                    || (clear == bestClear && quiet > bestQuiet)) {
                bestClear = clear;
                bestQuiet = quiet;
                best = sector;
            }
        }
        return best;
    }

    /**
     * Whether no direction offers both room and distance from them.
     *
     * <p>The measured form of "surrounded". Previously this was inferred from a
     * search having failed, which conflates being hemmed in by bodies with being
     * hemmed in by walls — they call for opposite answers.
     */
    public boolean surrounded(double minimumRoom) {
        return safestBearing(minimumRoom) < 0;
    }

    /**
     * One sector per entry, as {@code room/threat}, clockwise from due east.
     *
     * <p>Printed into the combat timeline: a direction chosen wrongly is
     * invisible in the outcome and obvious in the field it was chosen from.
     */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder("[");
        for (int sector = 0; sector < SECTORS; sector++) {
            if (sector > 0) {
                text.append(' ');
            }
            text.append((int) room(sector)).append('/');
            double near = threatDistance(sector);
            text.append(near == Double.POSITIVE_INFINITY
                    ? "-"
                    : String.format("%.1f", near));
        }
        return text.append(']').toString();
    }

    /** Accumulates observations into a field. */
    public static final class Builder {
        private final double[] nearest = new double[SECTORS];
        private final double[] room = new double[SECTORS];

        {
            java.util.Arrays.fill(nearest, Double.POSITIVE_INFINITY);
        }

        private Builder() {
        }

        /**
         * Record something hostile at a bearing.
         *
         * <p>The nearest one wins rather than the count, because what decides
         * whether a direction is walkable is how soon she meets something in
         * it, not how many are eventually down there.
         */
        public Builder threat(double radians, double distance) {
            int sector = sectorOf(radians);
            nearest[sector] = Math.min(nearest[sector], distance);
            return this;
        }

        /** Record how far she could walk along a bearing. */
        public Builder room(double radians, double blocks) {
            int sector = sectorOf(radians);
            room[sector] = Math.max(room[sector], blocks);
            return this;
        }

        public BearingField build() {
            return new BearingField(nearest.clone(), room.clone());
        }
    }
}
