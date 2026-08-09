package com.laixia.maidintelligence.feature.behavior.tlm.freedom;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * Whether this maid is the one we are allowed to think for.
 *
 * <p>The mod owns exactly one work mode and must leave every other one exactly
 * as the host wrote it. That is a single question, and it was being answered in
 * three places with three copies of the same expression — which is how the
 * fourteen mixins that never asked it at all came to change the host's other
 * modes without anyone deciding they should. A maid set to the host's pickup
 * mode was declining to pick things up near hostiles, because a rule written
 * for free mode had no idea free mode was the only place it applied.
 *
 * <p>Asked of the task rather than of a flag we keep, so it cannot drift out of
 * step with what the player actually selected. Switching modes rebuilds the
 * whole brain ({@code EntityMaid.setTask} → {@code refreshBrain}), so this
 * flips at exactly the moment the behaviour set does.
 */
public final class FreedomMode {
    private FreedomMode() {
    }

    /** Whether free mode is the maid's current work mode. */
    public static boolean isActive(EntityMaid maid) {
        return maid != null
                && maid.getTask() != null
                && FreedomMaidTask.UID.equals(maid.getTask().getUid());
    }

    /**
     * The inverse, named for the guard clause that reads best.
     *
     * <p>Every interception this mod installs starts with this, because the
     * default has to be "leave it alone". An interception that forgets is
     * invisible in free mode — where it does the right thing — and only shows
     * up as the host misbehaving in a mode we never meant to touch.
     */
    public static boolean isHostOwned(EntityMaid maid) {
        return !isActive(maid);
    }
}
