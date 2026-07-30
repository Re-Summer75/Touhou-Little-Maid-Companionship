package com.laixia.maidintelligence.feature.physics.session;


import org.joml.Vector3f;

/**
 * Removes the sustained part of a pose-drive signal and keeps the gusts.
 *
 * <p>A steady breeze would otherwise hold every soft part permanently leaned:
 * the spring settles wherever the bias balances the restoring pull and stays
 * there, so a maid standing in open weather no longer reads as the silhouette
 * her author built. Subtracting a slow running mean leaves only what changes —
 * gust fronts, turbulence, buffeting — so cloth and hair drift around the
 * authored pose instead of away from it.
 *
 * <p>The mean is seeded on the first sample rather than started at zero, which
 * would otherwise show up as a swing outward followed by a slow drift back the
 * moment a model comes into view.
 */
public final class PoseDriveGustFilter {
    /**
     * Time constant of the rejected band. Gust fronts arrive well under a
     * second apart and survive; anything held for several seconds decays.
     */
    private static final float STEADY_SECONDS = 3.0F;

    private final Vector3f sustained = new Vector3f();
    private boolean seeded;

    public void isolateGust(Vector3f signal, float dt, boolean paused) {
        if (!seeded) {
            sustained.set(signal);
            seeded = true;
        } else if (!paused && dt > 0.0F) {
            sustained.lerp(
                    signal,
                    1.0F - (float) Math.exp(-dt / STEADY_SECONDS)
            );
        }
        signal.sub(sustained);
    }

    public void reset() {
        sustained.zero();
        seeded = false;
    }
}
