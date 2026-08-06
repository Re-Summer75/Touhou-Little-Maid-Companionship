package com.laixia.maidintelligence.gametest.support;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the room a companion test happens in.
 *
 * <p>Written because the interesting failures are the ones nobody set out to
 * look for. Every maid used to be spawned alone in a bare box, and the one
 * scenario that happened to include a second maid found a crash that had been
 * sitting in the seating errand from the day it was written. A household has
 * several maids, furniture, a boat at the shore and something hostile in the
 * dark, and behaviour that only holds together in an empty room is not
 * behaviour that holds together.
 *
 * <p>Also the one place these fixtures live. The same twenty-line maid spawner
 * had been copied into five test files, which is how two of them quietly
 * disagreed about whether a maid starts in home mode.
 */
public final class CompanionScene {
    /**
     * How far above the template floor these scenes are built.
     *
     * <p>Game tests share one world laid out as a grid, and the furniture these
     * scenes put out is exactly what a neighbouring test's seat query is
     * looking for — a command-seating test three rooms over started failing
     * because it found a chair belonging to this suite. Horizontal spacing is
     * the framework's business and cannot be widened from here, but the seat
     * search only reaches four blocks up and down, so height is a separation
     * this code does control.
     */
    private static final int LIFT = 12;

    private final GameTestHelper helper;
    private final Player owner;
    private final List<EntityMaid> maids = new ArrayList<>();

    private CompanionScene(GameTestHelper helper, Player owner) {
        this.helper = helper;
        this.owner = owner;
    }

    /**
     * A floored room with an owner standing in it.
     *
     * @param width  floor extent along x, in blocks
     * @param depth  floor extent along z, in blocks
     */
    public static CompanionScene room(
            GameTestHelper helper,
            int width,
            int depth
    ) {
        for (int x = 0; x <= width; x++) {
            for (int z = 0; z <= depth; z++) {
                helper.setBlock(new BlockPos(x, LIFT, z), Blocks.STONE);
            }
        }
        Player owner = helper.makeMockPlayer();
        owner.setPos(GameTestPositions.center(helper, 1, LIFT + 1, 1));
        return new CompanionScene(helper, owner);
    }

    public Player owner() {
        return owner;
    }

    public CompanionScene ownerAt(int x, int y, int z) {
        owner.setPos(GameTestPositions.center(helper, x, y + LIFT - 1, z));
        return this;
    }

    /**
     * One tamed maid. Home mode is off and follow mode on, matching a maid
     * freshly tamed rather than one already given instructions.
     */
    public EntityMaid maid(int x, int y, int z) {
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(GameTestPositions.center(helper, x, y + LIFT - 1, z));
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
        maids.add(maid);
        return maid;
    }

    /** A maid nobody owns, for checking what she declines to do. */
    public EntityMaid strayMaid(int x, int y, int z) {
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, x, y + LIFT - 1, z));
        helper.getLevel().addFreshEntity(maid);
        maids.add(maid);
        return maid;
    }

    public List<EntityMaid> maids() {
        return List.copyOf(maids);
    }

    /** Furniture: a seat that is itself a living entity in this mod. */
    public EntityChair chair(int x, int y, int z) {
        EntityChair chair = EntityChair.TYPE.create(helper.getLevel());
        if (chair == null) {
            throw new AssertionError("A chair could not be created");
        }
        Vec3 position = GameTestPositions.center(helper, x, y + LIFT - 1, z);
        chair.setPos(position.x, position.y, position.z);
        helper.getLevel().addFreshEntity(chair);
        return chair;
    }

    /**
     * A boat, which is both a seat and a way to travel, and therefore the one
     * piece of furniture that answers two commodities at once.
     */
    public Boat boat(int x, int y, int z) {
        Vec3 position = GameTestPositions.center(helper, x, y + LIFT - 1, z);
        Boat boat = new Boat(helper.getLevel(), position.x, position.y,
                position.z);
        helper.getLevel().addFreshEntity(boat);
        return boat;
    }

    public ItemEntity drop(Item item, int x, int y, int z) {
        Vec3 position = GameTestPositions.center(helper, x, y + LIFT - 1, z);
        ItemEntity entity = new ItemEntity(
                helper.getLevel(),
                position.x,
                position.y,
                position.z,
                new ItemStack(item)
        );
        entity.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    /**
     * Something hostile, and a maid told to fight it.
     *
     * <p>Setting the target explicitly rather than waiting for her to notice:
     * what these tests are about is what a maid in combat declines to do, and
     * how long her senses take to find a zombie is a different question.
     */
    public Zombie threat(int x, int y, int z, EntityMaid engagedBy) {
        Zombie zombie = net.minecraft.world.entity.EntityType.ZOMBIE
                .create(helper.getLevel());
        if (zombie == null) {
            throw new AssertionError("A zombie could not be created");
        }
        Vec3 position = GameTestPositions.center(helper, x, y + LIFT - 1, z);
        zombie.setPos(position.x, position.y, position.z);
        zombie.setNoAi(true);
        /*
         * Deliberately never added to the level.
         *
         * <p>What these tests need is a maid who has something to fight, not a
         * fight. A zombie actually in the world is visible to every other test
         * in the grid — targeting reaches sixteen blocks in any direction, so
         * neither rooting it in place nor lifting these scenes clear of the
         * floor below put it out of reach, and a command-seating test three
         * rooms over kept failing because its maid had gone hostile at ours.
         *
         * <p>The occupancy classifier asks whether she holds an attack target,
         * which this satisfies without putting anything hostile in the world.
         */
        if (engagedBy != null) {
            engagedBy.setTarget(zombie);
            engagedBy.getBrain().setMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType
                            .ATTACK_TARGET,
                    zombie
            );
        }
        return zombie;
    }

    /** The production action dispatcher, wired the way the mod wires it. */
    public TlmMaidIntentActions actions() {
        return new TlmMaidIntentActions(
                ignored -> {
                },
                new MaidSnackCabinetMealSource(
                        new MaidMealAccess(),
                        new TlmAffordancePerceptionService()
                )
        );
    }

    public long gameTime() {
        return helper.getLevel().getGameTime();
    }

    /** Absolute position of a template-relative block, for home points. */
    public BlockPos at(int x, int y, int z) {
        return helper.absolutePos(new BlockPos(x, y + LIFT - 1, z));
    }

    public Entity vehicleOf(EntityMaid maid) {
        return maid.getVehicle();
    }
}
