package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.audit.MeshPenetrationAuditCli;

/**
 * Diagnostic: measures penetration as a viewer sees it, surface against
 * surface, and reports whether the pose it settles into is actually at rest.
 *
 * <p>The solver resolves a point on the bone axis against a collider box. A
 * viewer sees a panel with real width against a body with real width, so the
 * two differ by however far the mesh extends past its own axis. Reporting both
 * splits "the solver thinks it is done" from "the model looks right".
 *
 * <p>Sitting is the pose that matters most here. Legs fold into a skirt, so the
 * overlap is deep, lasting, and entirely authored — exactly the case the rest
 * allowance exists for and the case where excusing too much is invisible to
 * every clearance-based check. The motion columns are there because the failure
 * mode is not only depth: a segment with nowhere legal to be buzzes, and that
 * shows up as accumulated path with reversals while the pose looks settled.
 */
public final class MeshPenetrationAudit {
    private MeshPenetrationAudit() {
    }

    public static void main(String[] args) throws Exception {
        MeshPenetrationAuditCli.run(args);
    }
}
