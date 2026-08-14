package com.laixia.maidintelligence.feature.orchestration.tlm.errand.leisure;

import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .ApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .EntityApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.Errand;
import com.github.tartaricacid.touhoulittlemaid.entity.item.AbstractEntityFromItem;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;

import java.util.List;
import java.util.Objects;

/**
 * Sit down somewhere when there is nothing else worth doing.
 *
 * <p>Seats have been advertised since the perception layer was built, and until
 * now only the command-seat bridge ever asked for one — a maid left to herself
 * had no way to use a chair standing right beside her.
 *
 * <p>Unlike keeping near her owner, this reserves what it walks to. A chair
 * holds one person, so two maids heading for the same one is a race that has to
 * be settled before either arrives rather than after both sit.
 */
public final class RestOnSeatErrand implements Errand {
    private static final int CANDIDATES = 4;
    private static final double SEARCH_RANGE = 12.0D;

    private final TlmAffordancePerceptionService perception;

    public RestOnSeatErrand(TlmAffordancePerceptionService perception) {
        this.perception = Objects.requireNonNull(perception, "perception");
    }

    @Override
    public String name() {
        return "rest_on_seat";
    }

    @Override
    public ApproachTarget find(EntityMaid maid, long gameTime) {
        if (maid.isPassenger()) {
            // Already seated: nothing to go to, and re-seating her every tick
            // would look like fidgeting.
            return null;
        }
        List<Entity> seats = perception.queryCompatibleSeats(
                maid,
                CANDIDATES,
                SEARCH_RANGE,
                gameTime
        );
        for (Entity seat : seats) {
            if (restable(maid, seat)) {
                return new EntityApproachTarget(seat);
            }
        }
        return null;
    }

    /**
     * Something she may sit on to rest, which is narrower than something that
     * would accept her as a passenger.
     *
     * <p>The seat query answers the second question, because it was written for
     * seating a maid or her owner on command. Left unfiltered it also offers her
     * colleagues: two idle maids standing together would each sit on the other,
     * and a mob's navigation follows the vehicle it is controlling, so the pair
     * pointed at each other and the server died of a stack overflow.
     */
    /**
     * Something she may sit on, stated as what it is rather than as what it is
     * not.
     *
     * <p>The seat query asks whether an entity would accept a passenger, and in
     * vanilla very nearly everything will — a maid offered the choice sat down
     * on a dropped steak. Two attempts at excluding the wrong answers both
     * leaked: excluding living entities also excluded chairs, which are living
     * entities in this mod, and excluding colleagues still left the steak.
     *
     * <p>So this lists what a seat actually is. Missing a modded chair is a
     * maid who stays on her feet; guessing wrong the other way is a maid
     * perched on somebody's lunch.
     */
    private static boolean restable(EntityMaid maid, Entity seat) {
        return seat.isAlive()
                && seat != maid
                && isSeat(seat)
                && seat.getPassengers().isEmpty()
                // Belt and braces: anything already carrying her, however
                // indirectly, would close the same loop.
                && !seat.hasIndirectPassenger(maid);
    }

    private static boolean isSeat(Entity entity) {
        return entity instanceof AbstractEntityFromItem
                || entity instanceof Boat
                || entity instanceof AbstractMinecart;
    }

    /** Somebody may have taken it while she crossed the room. */
    @Override
    public boolean stillWorthwhile(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        return !maid.isPassenger()
                && target instanceof EntityApproachTarget seat
                && restable(maid, seat.entity());
    }

    @Override
    public boolean commit(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        if (!(target instanceof EntityApproachTarget seat)
                || !restable(maid, seat.entity())) {
            return false;
        }
        return maid.startRiding(seat.entity());
    }
}
