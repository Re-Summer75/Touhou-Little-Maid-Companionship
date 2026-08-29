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

    /**
     * 同样的房间，四周砌两格墙。
     *
     * <p>房间抬高十二格且默认悬空，而挂了自由模式的女仆会自己游走出去。摔十二格
     * 正好九到十点，落在世界地面——实测抓到过一次，表现是一条闪避测试报"让开了却
     * 还是中了"，而她其实已经不在场了。
     *
     * <p>做成另一个入口而不是改 {@link #room}：有些场景要的正是"边缘就是断崖"
     * （退无可退那一支靠它成立），统一砌墙会让那些测试改测别的东西。
     *
     * <p>两格高，因为箭走的是她的眼高——挡得住脚，挡不住这条测试要的那一箭。
     */
    public static CompanionScene walledRoom(
            GameTestHelper helper,
            int width,
            int depth
    ) {
        CompanionScene scene = room(helper, width, depth);
        for (int y = 1; y <= 2; y++) {
            for (int x = 0; x <= width; x++) {
                helper.setBlock(new BlockPos(x, LIFT + y, 0), Blocks.STONE);
                helper.setBlock(new BlockPos(x, LIFT + y, depth), Blocks.STONE);
            }
            for (int z = 0; z <= depth; z++) {
                helper.setBlock(new BlockPos(0, LIFT + y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(width, LIFT + y, z), Blocks.STONE);
            }
        }
        return scene;
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
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(GameTestPositions.center(helper, x, y + lift - 1, z));
        maid.setTame(true);
        // 拾物默认是**开**的（本体 `define(DATA_PICKUP, true)`，类型 ALL），而清扫
        // 的感知半径有十六格——够得着同一个世界里紧挨着排的别的测试结构。不关掉的
        // 话，任何一条测试的女仆都可能中途跑去捡邻居的东西，而那条测试量的往往是
        // 别的：跑步动画那条就这么红过，报的是"她已经停下来了还在跑"，其实是她被
        // 一件掉落物拉着没停。
        //
        // 要清扫的测试自己 `setPickup(true)`——那也让"这条测试依赖拾物"变成写在
        // 测试里的一句话，而不是一个默认值。
        maid.setPickup(false);
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
     *
     * <p><b>Inert is not weightless, and it must not become weightless.</b>
     * {@code setNoAi} stops it deciding to move; it does not stop it falling,
     * and a target placed past the edge of the floor drops out of her
     * perception in about twenty-one ticks — these rooms are lifted twelve
     * blocks and the threat sweep only reaches six down. Turning gravity off
     * here looks like the obvious repair and is measurably worse: {@code
     * TlmThreatScanner.airborne} reads {@code isNoGravity()} as "this one is in
     * the air", so it would relabel <em>every</em> dummy in the suite airborne
     * and move the threat pricing under all of them. Measured: two unrelated
     * arsenal tests went red, six runs out of six.
     *
     * <p>So the target keeps its weight and gets something to stand on instead
     * — see {@link #floorAt}.
     */
    public static <T extends Mob> T placeInert(GameTestHelper helper, T hostile) {
        hostile.setNoAi(true);
        hostile.setPersistenceRequired();
        helper.getLevel().addFreshEntity(hostile);
        return hostile;
    }

    /**
     * One more block of floor, for a target standing past the room's edge.
     *
     * <p>Scenes that need distance put the target further away than the room is
     * wide, and a target over nothing falls. Widening the whole room would push
     * this scene's blocks further into its neighbours' plots — these rooms are
     * already built out of an {@code empty} template — so what gets placed is
     * the one block the dummy is standing on.
     */
    public CompanionScene floorAt(int x, int z) {
        helper.setBlock(new BlockPos(x, lift, z), Blocks.STONE);
        return this;
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
