package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.api.*;
import com.laixia.maidintelligence.feature.physics.metadata.*;
import com.laixia.maidintelligence.feature.physics.discovery.*;
import com.laixia.maidintelligence.feature.physics.geometry.*;
import com.laixia.maidintelligence.feature.physics.layout.*;
import com.laixia.maidintelligence.feature.physics.engine.*;
import com.laixia.maidintelligence.feature.physics.session.*;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * Reports which of one maid's segments are shaking, measured in the live game.
 *
 * <p>Bound by shift-right-clicking a maid with the debug stick. The bench can
 * only pose a model the way a test writes the pose, and a discrepancy between
 * that and what the game does is invisible from the bench — a chain measured at
 * a tenth of a pixel of shake there was reported as shaking badly in play. This
 * reads the running solver instead.
 *
 * <p>Every solved frame is measured and the reversing travel summed over half a
 * second, rather than printing a snapshot on a timer. Both halves of that matter.
 * Sampling on a tick misses the fault entirely: a tick is three frames and the
 * fastest oscillation the solver can sustain is two, so tick-rate sampling folded
 * the worst jitter down to nothing and ranked smoothly swinging hair above it.
 * And a snapshot cannot show jitter at all, since jitter is a property of a
 * series — what identifies it is travel that keeps reversing, where an animated
 * segment covers its ground once and stops.
 *
 * <p>Displacement is measured from the pose the spring pulls back towards, not
 * from the bind pose, because an animation that keyframes a driven bone moves
 * that equilibrium with it and the difference is the whole quantity of interest:
 * a seated skirt is authored at sixty-five degrees, so measuring from the bind
 * pose would report most of the animation as physics.
 */
@OnlyIn(Dist.CLIENT)
public final class PhysicsDisplacementLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Seconds of frames gathered into one report. */
    private static final float WINDOW_SECONDS = 0.5F;
    /** Below this a segment is at rest and not worth a line. */
    private static final float QUIET_PIXELS = 0.01F;
    /** Below this a frame's movement is numerical noise, not travel. */
    private static final float NOISE_PIXELS = 0.002F;
    /** Only the worst few segments are worth printing per window. */
    private static final int REPORTED = 12;

    private static WeakReference<EntityMaid> tracked =
            new WeakReference<>(null);
    private static Window window;

    private PhysicsDisplacementLog() {
    }

    /** Binds the maid, or unbinds it if it was already the tracked one. */
    public static void toggle(Player player, EntityMaid maid) {
        if (tracked.get() == maid) {
            tracked = new WeakReference<>(null);
            tell(player, "已解除物理体位移日志绑定", ChatFormatting.YELLOW);
            return;
        }
        tracked = new WeakReference<>(maid);
        window = null;
        tell(
                player,
                "已绑定 " + maid.getName().getString()
                        + " 的物理体抖动日志，每 0.5 秒输出最抖的段落",
                ChatFormatting.AQUA
        );
    }

    public static void forget(EntityMaid maid) {
        if (tracked.get() == maid) {
            tracked = new WeakReference<>(null);
            window = null;
        }
    }

    private static void tell(
            Player player,
            String message,
            ChatFormatting colour
    ) {
        player.sendSystemMessage(
                Component.literal(message).withStyle(colour)
        );
    }

    /** Accumulates one solved frame, reporting once a window has filled. */
    static void sample(LivingEntity maid, SpringBoneSolver solver, float dt) {
        EntityMaid bound = tracked.get();
        if (bound == null || bound != maid) {
            return;
        }
        // A resource reload rebuilds the layout, and slot indices gathered against
        // the old one would name the wrong bones.
        if (window != null && !window.matches(solver.layout())) {
            window = null;
        }
        if (window == null) {
            window = new Window(solver.layout());
        }
        window.add(solver);
        if (window.elapsed(dt) < WINDOW_SECONDS) {
            return;
        }
        window.report(bound.getName().getString());
        window = null;
    }


    /**
     * How far the segment's tip sits from where the spring wants it, in model
     * pixels. An angle alone understates a long strand and overstates a stub, so
     * it is scaled by the lever arm — the same figure the bench audit reports, so
     * the two can be compared directly.
     */
    private static float displacement(
            Vector3f current,
            Vector3f rest,
            PhysicsSolverLayout.Node node
    ) {
        float dot = Math.max(-1.0F, Math.min(1.0F, current.dot(rest)));
        return (float) Math.acos(dot)
                * node.kinematics().leverArm() * 16.0F;
    }

    /**
     * One reporting window's worth of per-frame measurements.
     *
     * <p>Jitter is a property of a series of frames, not of any one of them, so
     * nothing useful can be said by printing a snapshot. What identifies it is
     * travel that keeps reversing: a segment following an animation covers ground
     * steadily in one direction, while a segment fighting a constraint covers the
     * same ground back and forth. Only the reversing part is counted, which is
     * why a smooth swing of thirty pixels scores zero here.
     */
    private static final class Window {
        private final PhysicsSolverLayout layout;
        private final int[] slots;
        private final String[] names;
        private final float[] previous;
        private final float[] lastStep;
        private final float[] travel;
        private final float[] peakStep;
        private final int[] reversals;
        private final int[] contactFrames;
        private final int[] collisionFrames;
        private final int[] swingFrames;
        private final float[] supportSum;
        private final float[] dampingSum;
        private final float[] dampingPeak;
        private final boolean[] seen;
        private final Vector3f current = new Vector3f();
        private final Vector3f rest = new Vector3f();
        private float seconds;
        private int frames;

        private Window(PhysicsSolverLayout layout) {
            this.layout = layout;
            int count = 0;
            for (int index = 0; index < layout.activeNodeCount(); index++) {
                if (layout.node(index).driven()) {
                    count++;
                }
            }
            slots = new int[count];
            names = new String[count];
            int cursor = 0;
            for (int index = 0; index < layout.activeNodeCount(); index++) {
                PhysicsSolverLayout.Node node = layout.node(index);
                if (!node.driven()) {
                    continue;
                }
                slots[cursor] = index;
                names[cursor] = node.bone().getName();
                cursor++;
            }
            previous = new float[count];
            lastStep = new float[count];
            travel = new float[count];
            peakStep = new float[count];
            reversals = new int[count];
            contactFrames = new int[count];
            collisionFrames = new int[count];
            swingFrames = new int[count];
            supportSum = new float[count];
            dampingSum = new float[count];
            dampingPeak = new float[count];
            seen = new boolean[count];
        }

        private float elapsed(float dt) {
            seconds += Float.isFinite(dt) && dt > 0.0F ? dt : 0.0F;
            return seconds;
        }

        private boolean matches(PhysicsSolverLayout other) {
            return layout == other;
        }

        private void add(SpringBoneSolver solver) {
            frames++;
            for (int entry = 0; entry < slots.length; entry++) {
                PhysicsSolverLayout.Node node = layout.node(slots[entry]);
                int slot = node.drivenSlot();
                if (!solver.copyCurrentDirection(slot, current)
                        || !solver.copyRestDirection(slot, rest)) {
                    continue;
                }
                accumulate(entry, displacement(current, rest, node), slot, solver);
            }
        }

        private void accumulate(
                int entry,
                float offset,
                int slot,
                SpringBoneSolver solver
        ) {
            float support = solver.contactSupport(slot);
            supportSum[entry] += support;
            float damping = solver.projectionDamping(slot);
            dampingSum[entry] += damping;
            dampingPeak[entry] = Math.max(dampingPeak[entry], damping);
            if (support > 0.0F) {
                contactFrames[entry]++;
            }
            int source = solver.lastProjectionSource(slot);
            if ((source & 2) != 0) {
                collisionFrames[entry]++;
            }
            if ((source & 1) != 0) {
                swingFrames[entry]++;
            }
            if (!seen[entry]) {
                seen[entry] = true;
                previous[entry] = offset;
                return;
            }
            float step = offset - previous[entry];
            previous[entry] = offset;
            if (Math.abs(step) < NOISE_PIXELS) {
                return;
            }
            /*
             * Only travel that reverses is counted. Steady travel in one direction
             * is the segment following its animation, and a metric that counted it
             * ranked a smoothly swinging strand of hair above a segment buzzing
             * against a collider.
             */
            if (step * lastStep[entry] < 0.0F) {
                reversals[entry]++;
                travel[entry] += Math.abs(step);
                peakStep[entry] = Math.max(peakStep[entry], Math.abs(step));
            }
            lastStep[entry] = step;
        }

        private void report(String maidName) {
            if (frames < 2) {
                return;
            }
            int worst = -1;
            int printed = 0;
            StringBuilder line = new StringBuilder();
            boolean[] done = new boolean[slots.length];
            while (printed < REPORTED) {
                worst = -1;
                for (int entry = 0; entry < slots.length; entry++) {
                    if (done[entry] || travel[entry] < QUIET_PIXELS) {
                        continue;
                    }
                    if (worst < 0 || travel[entry] > travel[worst]) {
                        worst = entry;
                    }
                }
                if (worst < 0) {
                    break;
                }
                done[worst] = true;
                printed++;
                append(line, worst);
            }
            LOGGER.info(
                    "physics jitter [{}] frames={} shaking={}{}",
                    maidName,
                    frames,
                    printed,
                    line.isEmpty() ? " (nothing reversing)" : line.toString()
            );
        }

        private void append(StringBuilder line, int entry) {
            line.append(String.format(
                    Locale.ROOT,
                    "%n  %-20s buzz=%6.3fpx/f peak=%6.3f revs=%3d per=%5.1f"
                            + " off=%6.2fpx sup=%4.2f damp=%4.2f/%4.2f"
                            + " hitF=%3d colF=%3d swgF=%3d",
                    names[entry],
                    travel[entry] / frames,
                    peakStep[entry],
                    reversals[entry],
                    /*
                     * Frames per reversal separates the two faults that look alike
                     * in a total: a value near two is a segment trapped between
                     * constraints flipping every frame, while a large one is an
                     * occasional lurch on an otherwise settled segment.
                     */
                    reversals[entry] > 0
                            ? (float) frames / reversals[entry]
                            : 0.0F,
                    previous[entry],
                    supportSum[entry] / frames,
                    dampingSum[entry] / frames,
                    dampingPeak[entry],
                    contactFrames[entry],
                    collisionFrames[entry],
                    swingFrames[entry]
            ));
        }
    }
}
