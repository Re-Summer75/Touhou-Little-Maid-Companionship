package com.laixia.maidintelligence.feature.physics.client.audit;

import java.util.Locale;

final class AuditRow {
    int segments;
    /**
     * Worst gap the solver measured at an endpoint, negative when it wanted
     * more room than it had.
     *
     * <p>Not a penetration figure. The projection holds the axis a sheet's
     * half thickness clear of a surface so that the surface lands on it, so
     * an axis settling inside the padded bound is the intended result — on
     * kluonoa's Tail this reads -0.89 px while the mesh itself is 0.06 px in.
     * Read {@code geom} for what a viewer sees, and read this for whether the
     * solver is being asked for something it cannot deliver.
     */
    float axis;
    float surface;
    float layerDepth;
    float path;
    int reversals;
    /**
     * Mean frames between direction changes on the buzziest segment.
     *
     * <p>The number that says what kind of motion it is. One or two frames is
     * the frame-rate chatter a viewer reads as shaking; ten or more is a part
     * swinging, however far it travels. Amplitude cannot make that
     * distinction — a lurch every thirtieth frame and a tremble every frame
     * report the same travel per frame.
     */
    float period;
    int contacts;
    int layerContacts;
    String label = "-";
    String geometricLabel = "-";
    String layerLabel = "-";
    String jitterLabel = "-";

    void considerAxis(float clearance, String name) {
        if (clearance < axis) {
            axis = clearance;
            label = name;
        }
    }

    // A depth, so the worst case is the largest, unlike the clearances.
    void considerGeometric(float depth, String name) {
        if (depth > surface) {
            surface = depth;
            geometricLabel = name;
        }
    }

    void considerLayer(float clearance, String name) {
        if (clearance < layerDepth) {
            layerDepth = clearance;
            layerLabel = name;
        }
    }

    void considerJitter(
            float travel,
            int reversed,
            float halfPeriod,
            String name
    ) {
        if (travel > path) {
            path = travel;
            reversals = reversed;
            period = halfPeriod;
            jitterLabel = name;
        }
    }

    void accumulate(AuditRow other) {
        segments = Math.max(segments, other.segments);
        contacts = Math.max(contacts, other.contacts);
        layerContacts = Math.max(layerContacts, other.layerContacts);
        considerAxis(other.axis, other.label);
        considerGeometric(other.surface, other.geometricLabel);
        considerLayer(other.layerDepth, other.layerLabel);
        considerJitter(
                other.path, other.reversals, other.period,
                other.jitterLabel
        );
    }

    static void print(String name, AuditRow row) {
        System.out.printf(
                Locale.ROOT,
                "%-22s %4d %6.2f %7.2f %6d %5d %6.1f %5d %5.1f  %-16s %-16s"
                        + " %s%n",
                name,
                row.segments,
                row.axis * AuditSupport.PIXELS_PER_BLOCK,
                row.surface * AuditSupport.PIXELS_PER_BLOCK,
                row.contacts,
                row.layerContacts,
                row.path,
                row.reversals,
                row.period,
                row.label,
                row.geometricLabel,
                row.jitterLabel
        );
    }
}
