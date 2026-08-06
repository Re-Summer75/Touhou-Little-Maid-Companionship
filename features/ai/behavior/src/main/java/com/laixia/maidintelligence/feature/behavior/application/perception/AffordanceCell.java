package com.laixia.maidintelligence.feature.behavior.application.perception;

import com.laixia.maidintelligence.feature.behavior.domain.perception.AffordancePosition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One cube of world an advertisement can be filed under.
 *
 * <p>Exists so a query bounded by distance can visit the handful of cubes that
 * distance actually reaches, instead of walking every advertisement of its kind
 * and hoping the relevant one turns up early. Without it, the per-tick
 * examination allowance truncated candidates in registration order: a maid was
 * told there was no seat nearby while examining chairs in somebody else's house
 * and running out of allowance before reaching the one at her feet.
 *
 * <p>The dimension is part of the identity because distance between dimensions
 * is infinite, so mixing them in one cube would mean paying to reject them.
 *
 * @param dimension which world this cube belongs to
 * @param x         cube index along x, not a block coordinate
 * @param y         cube index along y
 * @param z         cube index along z
 */
record AffordanceCell(String dimension, int x, int y, int z) {
    /**
     * Cube edge in blocks.
     *
     * <p>Sixteen matches the widest range anything queries with, so an ordinary
     * query touches a three-by-three-by-three neighbourhood. Smaller cubes
     * would mean visiting more of them for the same reach; larger ones would
     * put more irrelevant advertisements inside each.
     */
    static final int SIZE = 16;

    AffordanceCell {
        Objects.requireNonNull(dimension, "dimension");
    }

    static AffordanceCell of(AffordancePosition position) {
        return new AffordanceCell(
                position.dimension(),
                index(position.x()),
                index(position.y()),
                index(position.z())
        );
    }

    /**
     * Every cube within {@code radius} of {@code origin}, including partial
     * overlaps — a cube is worth visiting if any part of it is in reach.
     */
    static List<AffordanceCell> covering(
            AffordancePosition origin,
            double radius
    ) {
        int span = (int) Math.ceil(radius / SIZE);
        AffordanceCell centre = of(origin);
        List<AffordanceCell> cells = new ArrayList<>();
        for (int dx = -span; dx <= span; dx++) {
            for (int dy = -span; dy <= span; dy++) {
                for (int dz = -span; dz <= span; dz++) {
                    cells.add(new AffordanceCell(
                            centre.dimension(),
                            centre.x() + dx,
                            centre.y() + dy,
                            centre.z() + dz
                    ));
                }
            }
        }
        return cells;
    }

    private static int index(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), SIZE);
    }
}
