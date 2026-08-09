package com.laixia.maidintelligence.gametest.support;

import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
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
 * several maids, furniture, and a boat at the shore, and behaviour that only holds together in an empty room is not
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
    private final int lift;
    private final List<EntityMaid> maids = new ArrayList<>();

    private CompanionScene(GameTestHelper helper, Player owner, int lift) {
        this.helper = helper;
        this.owner = owner;
        this.lift = lift;
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
        return room(helper, width, depth, LIFT);
    }

    private static CompanionScene room(
            GameTestHelper helper,
            int width,
            int depth,
            int lift
    ) {
        for (int x = 0; x <= width; x++) {
            for (int z = 0; z <= depth; z++) {
                helper.setBlock(new BlockPos(x, lift, z), Blocks.STONE);
            }
        }
        Player owner = helper.makeMockPlayer();
        owner.setPos(GameTestPositions.center(helper, 1, lift + 1, 1));
        return new CompanionScene(helper, owner, lift);
    }

    public Player owner() {
        return owner;
    }

    public CompanionScene ownerAt(int x, int y, int z) {
        owner.setPos(GameTestPositions.center(helper, x, y + lift - 1, z));
        return this;
    }

    /**
     * One tamed maid. Home mode is off and follow mode on, matching a maid
     * freshly tamed rather than one already given instructions.
     */
    /**
     * A maid in free mode, because that is the only maid this mod has opinions
     * about.
     *
     * <p>The task used to be left at the host's default, and every fixture in
     * here quietly tested behaviour that now — correctly — only exists in free
     * mode. Leaving it that way would have meant nineteen tests asserting that
     * this mod changes work modes it is not supposed to touch.
     *
     * <p>Set after she enters the level. {@code setTask} rebuilds the brain and
     * calls {@code stopAll} on the old one, which touches level state; doing it
     * to an entity the level has not accepted yet leaves the brain in a state
     * whose symptoms surface much later and nowhere near here.
     */
    public EntityMaid maid(int x, int y, int z) {
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(GameTestPositions.center(helper, x, y + lift - 1, z));
        maid.setTame(true);
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
        maid.setTask(
                TaskManager.findTask(FreedomMaidTask.UID).orElseThrow()
        );
        maids.add(maid);
        return maid;
    }

    /** The host's own default mode, for checking we left it alone. */
    public EntityMaid hostModeMaid(int x, int y, int z) {
        EntityMaid maid = maid(x, y, z);
        maid.setTask(
                TaskManager.getTaskIndex().stream()
                        .filter(task ->
                                !FreedomMaidTask.UID.equals(task.getUid()))
                        .findFirst()
                        .orElseThrow()
        );
        return maid;
    }

    /** A maid nobody owns, for checking what she declines to do. */
    public EntityMaid strayMaid(int x, int y, int z) {
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(GameTestPositions.center(helper, x, y + lift - 1, z));
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
        Vec3 position = GameTestPositions.center(helper, x, y + lift - 1, z);
        chair.setPos(position.x, position.y, position.z);
        helper.getLevel().addFreshEntity(chair);
        return chair;
    }

    /**
     * A boat, which is both a seat and a way to travel, and therefore the one
     * piece of furniture that answers two commodities at once.
     */
    public Boat boat(int x, int y, int z) {
        Vec3 position = GameTestPositions.center(helper, x, y + lift - 1, z);
        Boat boat = new Boat(helper.getLevel(), position.x, position.y,
                position.z);
        helper.getLevel().addFreshEntity(boat);
        return boat;
    }

    public ItemEntity drop(Item item, int x, int y, int z) {
        Vec3 position = GameTestPositions.center(helper, x, y + lift - 1, z);
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
     * Puts a hostile in the world without letting it join in.
     *
     * <p>Writing one into her visible-entity memory used to be enough. It is
     * not any more: the threat scan sweeps the world rather than reading that
     * memory, so a monster that exists only in her brain is a monster she
     * cannot find, and every combat scenario quietly became a maid standing in
     * an empty room.
     *
     * <p>Inert, because that is what these scenarios were always written
     * against. A hostile that was never added to the level never pathed, never
     * swung and never burned; switching its AI on as a side effect of making it
     * findable would trade one fiction for a noisier one, and the assertions
     * here are about what <em>she</em> does.
     */
    public static <T extends Mob> T placeInert(GameTestHelper helper, T hostile) {
        hostile.setNoAi(true);
        hostile.setPersistenceRequired();
        helper.getLevel().addFreshEntity(hostile);
        return hostile;
    }

    /**
     * Puts a maid into combat, without putting a fight into the world.
     *
     * <p>What these scenarios examine is what she declines to do while
     * occupied, and the occupancy classifier only asks whether she holds an
     * attack target at all. A monster spawned to supply one is a monster every
     * other test in the grid can also see — targeting reaches sixteen blocks
     * and pays no attention to the height these scenes are lifted by — so the
     * target is somebody already standing here. Who she is nominally fighting
     * is not what any assertion here is about.
     */
    public void engageInCombat(EntityMaid maid) {
        maid.setTarget(owner);
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, owner);
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
        return helper.absolutePos(new BlockPos(x, y + lift - 1, z));
    }

    public Entity vehicleOf(EntityMaid maid) {
        return maid.getVehicle();
    }
}
