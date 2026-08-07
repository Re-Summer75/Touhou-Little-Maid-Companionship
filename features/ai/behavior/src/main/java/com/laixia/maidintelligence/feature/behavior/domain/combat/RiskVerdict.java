package com.laixia.maidintelligence.feature.behavior.domain.combat;

/**
 * What the arithmetic says about taking this fight.
 *
 * <p>Three answers rather than two, because "cannot win it standing there" and
 * "cannot win it at all" call for opposite movement: the first wants distance
 * kept, the second wants distance made.
 */
public enum RiskVerdict {
    /** She out-trades them; fight normally. */
    ENGAGE,
    /**
     * She loses a straight trade but they cannot answer from afar.
     *
     * <p>Requires a usable ranged weapon — without one this is just standing
     * still while they walk over.
     */
    SKIRMISH,
    /**
     * Losing and unable to fix it by moving; stop fighting.
     *
     * <p>Withdrawing must never be toward her owner. Whatever is beating her
     * follows, and delivering it to the person she is guarding turns a lost
     * fight into a lost owner. Direction is the adapter's to choose, but that
     * constraint is not negotiable.
     */
    WITHDRAW,
    /** Nothing to fight. */
    STAND_DOWN
}
