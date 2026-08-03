package com.laixia.maidintelligence.feature.status.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitBlocks;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntitySnackCabinet;
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
    public static final int SEARCH_RANGE = 8;

    private static final int VERTICAL_SEARCH_RANGE = 4;
    private static final int SEARCH_INTERVAL_TICKS = 100;
    private static final double SEARCH_RANGE_SQUARED =
            (double) SEARCH_RANGE * SEARCH_RANGE;

    private final MaidMealAccess mealAccess;
    private final Map<EntityMaid, SearchState> searchStates =
            new WeakHashMap<>();

    public MaidSnackCabinetMealSource(MaidMealAccess mealAccess) {
        this.mealAccess = Objects.requireNonNull(mealAccess, "mealAccess");
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
        state.target = searchNearest(maid, level);
        return Optional.ofNullable(state.target);
    }

    public boolean tryTakeAndStartMeal(
            EntityMaid maid,
            BlockPos cabinetPos
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
        if (!(level.getBlockEntity(cabinetPos)
                instanceof TileEntitySnackCabinet cabinet)) {
            invalidate(maid, cabinetPos);
            return false;
        }

        int slot = findMealSlot(maid, cabinet);
        if (slot < 0) {
            invalidate(maid, cabinetPos);
            return false;
        }

        // Extraction and meal start run on the server thread; removing one item
        // makes competing maids observe the updated stack without duplication.
        ItemStack extracted = cabinet.removeItem(slot, 1);
        if (extracted.isEmpty()) {
            invalidate(maid, cabinetPos);
            return false;
        }
        cabinet.setChanged();
        if (mealAccess.tryStartExternalHungerMeal(maid, extracted)) {
            if (findMealSlot(maid, cabinet) < 0) {
                invalidate(maid, cabinetPos);
            }
            return true;
        }

        restore(cabinet, slot, extracted);
        invalidate(maid, cabinetPos);
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

    private BlockPos searchNearest(EntityMaid maid, ServerLevel level) {
        BlockPos origin = maid.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = -VERTICAL_SEARCH_RANGE;
             y <= VERTICAL_SEARCH_RANGE;
             y++) {
            for (int x = -SEARCH_RANGE; x <= SEARCH_RANGE; x++) {
                for (int z = -SEARCH_RANGE; z <= SEARCH_RANGE; z++) {
                    cursor.setWithOffset(origin, x, y, z);
                    double distance = maid.distanceToSqr(
                            cursor.getX() + 0.5D,
                            cursor.getY() + 0.5D,
                            cursor.getZ() + 0.5D
                    );
                    if (distance > SEARCH_RANGE_SQUARED
                            || distance >= nearestDistance
                            || !level.isLoaded(cursor)
                            || !level.getBlockState(cursor).is(
                            InitBlocks.SNACK_CABINET.get()
                    )) {
                        continue;
                    }
                    if (level.getBlockEntity(cursor)
                            instanceof TileEntitySnackCabinet cabinet
                            && findMealSlot(maid, cabinet) >= 0) {
                        nearest = cursor.immutable();
                        nearestDistance = distance;
                    }
                }
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
}
