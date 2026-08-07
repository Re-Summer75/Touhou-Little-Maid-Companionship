package com.laixia.maidintelligence.feature.behavior.domain.perception;

/**
 * How far she notices anything at all.
 *
 * <p>One number, because every behaviour is downstream of it. Picking up a
 * dropped carrot, walking to a snack cabinet, taking a free chair and spotting
 * a zombie are the same act performed on different objects — she has to notice
 * the thing before she can want it. Letting each behaviour carry its own reach
 * is how a maid ends up able to fetch food from further than she can see a
 * creeper, and nothing in the code says the two disagree.
 *
 * <p>Deliberately separate from her activity radius. That radius is about where
 * she is willing to <em>be</em>, it is dynamic, and this mod already widens it
 * to twenty-four blocks while her owner stands still so that a fight has room.
 * Perception must not follow it up: noticing further would make her leave to
 * deal with things her owner cannot even see. Radius answers "may I go there",
 * this answers "do I know it is there", and only the second belongs to every
 * behaviour.
 *
 * <p>New behaviours should express their reach in terms of this rather than
 * introducing another literal. Something that genuinely needs less — a chair
 * across the room is not worth crossing a field for — should narrow it
 * explicitly and say why.
 */
public final class PerceptionRange {
    /** Maximum blocks at which anything may be perceived. */
    public static final double BLOCKS = 16.0D;

    /** Convenience for the many callers comparing squared distances. */
    public static final double SQUARED = BLOCKS * BLOCKS;

    private PerceptionRange() {
    }

    /**
     * Clamp a proposed reach so nothing perceives further than this.
     *
     * <p>Used where the reach arrives from somewhere else — an activity radius,
     * a config value — and must not be allowed to exceed perception.
     */
    public static double clamp(double proposedBlocks) {
        return Math.min(proposedBlocks, BLOCKS);
    }
}
