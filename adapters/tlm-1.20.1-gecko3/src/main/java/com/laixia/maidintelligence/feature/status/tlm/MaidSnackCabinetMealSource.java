package com.laixia.maidintelligence.feature.status.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntitySnackCabinet;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Locates legal work meals in nearby TLM snack cabinets without loading chunks.
 */
@SuppressWarnings("null")
public final class MaidSnackCabinetMealSource {
    public static final int SEARCH_RANGE = 16;

    /*
     * Five seconds meant a cabinet stocked in front of her stayed invisible
     * long enough to look like she had not noticed. Two is short enough to read
     * as noticing and still amortises the query across many maids.
     */
    private static final int SEARCH_INTERVAL_TICKS = 40;
    private static final double SEARCH_RANGE_SQUARED =
            (double) SEARCH_RANGE * SEARCH_RANGE;

    private final MaidMealAccess mealAccess;
    private final TlmAffordancePerceptionService perception;
    private final Map<EntityMaid, SearchState> searchStates =
            new WeakHashMap<>();

    public MaidSnackCabinetMealSource(MaidMealAccess mealAccess) {
        this(mealAccess, new TlmAffordancePerceptionService());
    }

    public MaidSnackCabinetMealSource(
            MaidMealAccess mealAccess,
            TlmAffordancePerceptionService perception
    ) {
        this.mealAccess = Objects.requireNonNull(mealAccess, "mealAccess");
        this.perception = Objects.requireNonNull(perception, "perception");
    }

    /**
     * How this source feeds a maid, shared so a sibling action can start a meal
     * on the same terms rather than reimplementing what counts as edible.
     */
    public MaidMealAccess mealAccess() {
        return mealAccess;
    }

    /** How this source sees, shared for the same reason. */
    public TlmAffordancePerceptionService perception() {
        return perception;
    }

    public Optional<BlockPos> findAvailableMeal(
            EntityMaid maid,
            long gameTime
    ) {
        if (!(maid.level() instanceof ServerLevel level)
                || maid.isUsingItem()
                || maid.isSleeping()
                || maid.isBegging()
                || !maid.getTask().enableEating(maid)
                || !maid.getHideInv().getStackInSlot(0).isEmpty()
                || mealAccess.hasLocalHungerMeal(maid)) {
            return Optional.empty();
        }

        SearchState state = searchStates.computeIfAbsent(
                maid,
                ignored -> new SearchState()
        );
        if (gameTime < state.lastGameTime) {
            state.nextSearchTime = 0L;
            state.target = null;
        }
        state.lastGameTime = gameTime;

        if (state.target != null && hasMeal(maid, level, state.target)) {
            return Optional.of(state.target);
        }
        state.target = null;
        if (gameTime < state.nextSearchTime) {
            return Optional.empty();
        }

        state.nextSearchTime = gameTime + SEARCH_INTERVAL_TICKS;
        state.target = indexedNearest(maid, level, gameTime);
        return Optional.ofNullable(state.target);
    }

    public Optional<MealTarget> findAvailableMealTarget(
            EntityMaid maid,
            long gameTime
    ) {
        return findAvailableMeal(maid, gameTime)
                .flatMap(position -> {
                    if (!(maid.level() instanceof ServerLevel level)) {
                        return Optional.empty();
                    }
                    return Optional.ofNullable(targetAt(
                            maid,
                            level,
                            position
                    ));
                });
    }

    public boolean tryTakeAndStartMeal(
            EntityMaid maid,
            BlockPos cabinetPos
    ) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return false;
        }
        MealTarget target = targetAt(maid, level, cabinetPos);
        return target != null && tryTakeAndStartMeal(maid, target);
    }

    public boolean tryTakeAndStartMeal(
            EntityMaid maid,
            MealTarget target
    ) {
        if (!(maid.level() instanceof ServerLevel level)
                || maid.isUsingItem()
                || maid.isSleeping()
                || maid.isBegging()
                || !maid.getTask().enableEating(maid)
                || !maid.getHideInv().getStackInSlot(0).isEmpty()
                || mealAccess.hasLocalHungerMeal(maid)) {
            return false;
        }
        if (!(level.getBlockEntity(target.position())
                instanceof TileEntitySnackCabinet cabinet)) {
            invalidate(maid, target.position());
            return false;
        }

        int slot = target.slot();
        if (slot < 0
                || slot >= cabinet.getContainerSize()
                || !mealAccess.canStartExternalHungerMeal(
                maid,
                cabinet.getItem(slot)
        )
                || !ItemStack.isSameItemSameTags(
                target.fingerprint(),
                cabinet.getItem(slot)
        )) {
            invalidate(maid, target.position());
            return false;
        }

        // Extraction and meal start run on the server thread; removing one item
        // makes competing maids observe the updated stack without duplication.
        ItemStack extracted = cabinet.removeItem(slot, 1);
        if (extracted.isEmpty()) {
            invalidate(maid, target.position());
            return false;
        }
        cabinet.setChanged();
        if (mealAccess.tryStartExternalHungerMeal(maid, extracted)) {
            if (findMealSlot(maid, cabinet) < 0) {
                invalidate(maid, target.position());
            }
            return true;
        }

        restore(cabinet, slot, extracted);
        invalidate(maid, target.position());
        return false;
    }

    public void invalidate(EntityMaid maid, BlockPos cabinetPos) {
        SearchState state = searchStates.get(maid);
        if (state == null
                || state.target == null
                || !state.target.equals(cabinetPos)) {
            return;
        }
        state.target = null;
        state.nextSearchTime = 0L;
    }

    private BlockPos indexedNearest(
            EntityMaid maid,
            ServerLevel level,
            long gameTime
    ) {
        BlockPos nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (BlockPos position
                : perception.querySnackCabinets(maid, 8, gameTime)) {
            if (!hasMeal(maid, level, position)) {
                continue;
            }
            double distance = maid.distanceToSqr(
                    position.getX() + 0.5D,
                    position.getY() + 0.5D,
                    position.getZ() + 0.5D
            );
            if (distance < nearestDistance) {
                nearest = position;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private boolean hasMeal(
            EntityMaid maid,
            ServerLevel level,
            BlockPos pos
    ) {
        if (maid.distanceToSqr(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        ) > SEARCH_RANGE_SQUARED) {
            return false;
        }
        return level.isLoaded(pos)
                && level.getBlockEntity(pos)
                instanceof TileEntitySnackCabinet cabinet
                && findMealSlot(maid, cabinet) >= 0;
    }

    private int findMealSlot(
            EntityMaid maid,
            TileEntitySnackCabinet cabinet
    ) {
        for (int slot = 0; slot < cabinet.getContainerSize(); slot++) {
            if (mealAccess.canStartExternalHungerMeal(
                    maid,
                    cabinet.getItem(slot)
            )) {
                return slot;
            }
        }
        return -1;
    }

    private MealTarget targetAt(
            EntityMaid maid,
            ServerLevel level,
            BlockPos position
    ) {
        if (!level.isLoaded(position)
                || !(level.getBlockEntity(position)
                instanceof TileEntitySnackCabinet cabinet)) {
            return null;
        }
        int slot = findMealSlot(maid, cabinet);
        if (slot < 0) {
            return null;
        }
        return new MealTarget(
                position.immutable(),
                slot,
                cabinet.getItem(slot)
        );
    }

    private static void restore(
            TileEntitySnackCabinet cabinet,
            int slot,
            ItemStack extracted
    ) {
        ItemStack current = cabinet.getItem(slot);
        if (current.isEmpty()) {
            cabinet.setItem(slot, extracted);
        } else if (ItemStack.isSameItemSameTags(current, extracted)) {
            current.grow(extracted.getCount());
            cabinet.setChanged();
        }
    }

    private static final class SearchState {
        private long lastGameTime = Long.MIN_VALUE;
        private long nextSearchTime;
        private BlockPos target;
    }

    public record MealTarget(
            BlockPos position,
            int slot,
            ItemStack fingerprint
    ) {
        public MealTarget {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(fingerprint, "fingerprint");
            if (slot < 0 || fingerprint.isEmpty()) {
                throw new IllegalArgumentException("Invalid meal target");
            }
            position = position.immutable();
            fingerprint = fingerprint.copyWithCount(1);
        }
    }
}
