package com.laixia.maidintelligence.feature.physics.layout;


import com.laixia.maidintelligence.feature.physics.layout.pivot.PivotSwingRange;

/**
 * How far a driven segment may leave the pose the animation asked for.
 *
 * <p>Three ceilings meet here. {@link #MAX_ANGLE} is the flat limit no segment
 * exceeds whatever its geometry says. {@code safeAngle} is what the attachment
 * frame could actually prove about the pivot, and it tightens the flat limit
 * wherever the rotation centre had to be inferred, because turning a segment
 * far around a guessed pivot magnifies the guess. {@link
 * #MAX_TIP_DISPLACEMENT} bounds the arc the tip travels rather than the angle,
 * which is what keeps a long strand and a short one from looking like they obey
 * different rules: the same angle throws a long tip much further.
 *
 * <p>These used to be copied into the four places that need them, so a change
 * here silently applied to some of the pipeline and not the rest. The layout
 * bakes a cap per node, the projector re-derives it against the runtime scale,
 * the deflection writer clamps the rendered rotation to it, and the wind driver
 * takes a share of it. All four have to agree or physics resolves to one limit
 * and renders to another.
 */
public final class SwingRange {
    /**
     * Flat ceiling, in radians, before per-part scaling. Also what an
     * attachment frame reports when it proved the pivot outright and has no
     * reason of its own to hold the segment back.
     */
    static final float MAX_ANGLE = 1.05F;
    /**
     * Allowed tip travel in pixels before per-part scaling. Divided by the
     * lever arm, so it binds long segments and leaves short ones to
     * {@link #MAX_ANGLE}.
     */
    private static final float MAX_TIP_DISPLACEMENT = 4.5F;

    private SwingRange() {
    }

    public static float maximum(PhysicsSolverLayout.Node node) {
        return maximum(node, 1.0F);
    }

    /**
     * @param runtimeSafetyScale how much larger the segment is drawn than it
     *                           was authored; a scaled-up model throws its tip
     *                           proportionally further for the same angle, so
     *                           the displacement ceiling has to shrink to
     *                           match.
     */
    public static float maximum(
            PhysicsSolverLayout.Node node,
            float runtimeSafetyScale
    ) {
        return PivotSwingRange.maximum(node, runtimeSafetyScale);
    }
}
