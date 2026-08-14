package com.laixia.maidintelligence.feature.orchestration.tlm.errand.needs;

import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .ApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand
        .ContainerSlotApproachTarget;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.Errand;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/**
 * Fetch a meal from a snack cabinet.
 *
 * <p>Where the loose-food errand claims an item, this claims one shelf of one
 * cabinet — two maids may raid the same cupboard at once so long as they reach
 * for different shelves.
 */
public final class CabinetMealErrand implements Errand {
    private final MaidSnackCabinetMealSource meals;

    public CabinetMealErrand(MaidSnackCabinetMealSource meals) {
        this.meals = Objects.requireNonNull(meals, "meals");
    }

    @Override
    public String name() {
        return "snack";
    }

    /**
     * Gets up off a decorative seat first. Sitting is not something she was
     * told to do — unlike a commanded seat, which the eligibility check below
     * still refuses to override.
     */
    @Override
    public void prepare(EntityMaid maid) {
        if (isPassiveTlmSeat(maid.getVehicle())) {
            maid.stopRiding();
        }
    }

    @Override
    public ApproachTarget find(EntityMaid maid, long gameTime) {
        return meals.findAvailableMealTarget(maid, gameTime)
                .map(target -> (ApproachTarget) new ContainerSlotApproachTarget(
                        target.position(),
                        target.slot()
                ))
                .orElse(null);
    }

    @Override
    public boolean stillWorthwhile(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        return meals.findAvailableMealTarget(maid, gameTime).isPresent();
    }

    /**
     * Resolved again here rather than carried from the walk. The shelf may have
     * been emptied while she crossed the room, and the source re-checks the
     * item's fingerprint before removing anything.
     */
    @Override
    public boolean commit(
            EntityMaid maid,
            ApproachTarget target,
            long gameTime
    ) {
        return target instanceof ContainerSlotApproachTarget slot
                && meals.tryTakeAndStartMeal(maid, slot.position());
    }

    private static boolean isPassiveTlmSeat(Entity vehicle) {
        return vehicle != null
                && (vehicle.getType() == EntityChair.TYPE
                || vehicle.getType() == EntitySit.TYPE);
    }
}
