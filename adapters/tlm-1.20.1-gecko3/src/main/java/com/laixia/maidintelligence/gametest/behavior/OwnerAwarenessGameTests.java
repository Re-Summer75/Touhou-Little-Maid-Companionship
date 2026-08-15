package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.domain.OwnerFollowPolicy;
import com.laixia.maidintelligence.feature.behavior.domain.owner.OwnerFacts;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMovement;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.RetreatSpace;
import com.laixia.maidintelligence.feature.behavior.handler.OwnerGazeRecallHandler;
import com.laixia.maidintelligence.feature.orchestration.tlm.context.TlmOwnerFactReader;
import com.laixia.maidintelligence.feature.perception.tlm.TlmAffordancePerceptionService;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * What a maid notices about her owner and about what is lying around her.
 *
 * <p>These are the claims that only a running world can settle. That aim finds
 * her through a wall is a statement about real block collision; that her owner
 * is holding food is a statement about a real inventory; that a thrown steak
 * becomes visible to her is a statement about real entities being indexed. The
 * plain unit suites alongside them can check the arithmetic of all three, and
 * none of the three.
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class OwnerAwarenessGameTests {
    private static final double GAZE_RANGE = 16.0D;

    private OwnerAwarenessGameTests() {
    }

    // Aim.

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aimFindsTheMaidLookedAt(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        lookAt(fixture.owner(), fixture.maid());

        helper.assertTrue(
                OwnerGazeRecallHandler.findAimedAtMaid(
                        fixture.owner(),
                        GAZE_RANGE
                ) == fixture.maid(),
                "Aiming straight at the maid did not find her"
        );
        helper.succeed();
    }

    /**
     * Addressing someone is not the same as having line of sight to them, and
     * a maid one room over is still the maid being spoken to.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aimReachesThroughWalls(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        for (int y = 2; y <= 4; y++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
            }
        }
        lookAt(fixture.owner(), fixture.maid());

        helper.assertTrue(
                OwnerGazeRecallHandler.findAimedAtMaid(
                        fixture.owner(),
                        GAZE_RANGE
                ) == fixture.maid(),
                "A stone wall hid the maid from the owner's aim"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aimingElsewhereFindsNobody(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        lookAt(fixture.owner(), fixture.maid());
        // Turn a right angle away from her.
        fixture.owner().setYRot(fixture.owner().getYRot() + 90.0F);
        fixture.owner().setYHeadRot(fixture.owner().getYRot());

        helper.assertTrue(
                OwnerGazeRecallHandler.findAimedAtMaid(
                        fixture.owner(),
                        GAZE_RANGE
                ) == null,
                "Looking away still singled out a maid"
        );
        helper.succeed();
    }

    /**
     * Pointing at one of several is about which one is being pointed at, not
     * which one happens to be closest to the pointer.
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aimPrefersTheCentredMaidOverTheNearer(
            GameTestHelper helper
    ) {
        Fixture fixture = fixture(helper);
        EntityMaid nearer = maid(helper, fixture.owner(), 3, 2, 2);
        lookAt(fixture.owner(), fixture.maid());

        EntityMaid aimed = OwnerGazeRecallHandler.findAimedAtMaid(
                fixture.owner(),
                GAZE_RANGE
        );
        helper.assertTrue(aimed == fixture.maid(),
                "Aim picked the nearer maid instead of the one aimed at");
        helper.assertFalse(aimed == nearer,
                "Aim picked a maid off to the side");
        helper.succeed();
    }

    // Owner state.

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void heldFoodIsNoticed(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        Player owner = fixture.owner();
        TlmOwnerFactReader reader = new TlmOwnerFactReader();
        long gameTime = helper.getLevel().getGameTime();

        OwnerFacts empty = reader.read(owner, gameTime);
        helper.assertTrue(empty.handsEmpty() == 1.0D,
                "Empty hands did not read as empty");
        helper.assertTrue(empty.holdingFood() == 0.0D,
                "Empty hands read as holding food");

        owner.setItemInHand(
                InteractionHand.MAIN_HAND,
                new ItemStack(Items.COOKED_BEEF)
        );
        OwnerFacts fed = reader.read(owner, gameTime + 1L);
        helper.assertTrue(fed.holdingFood() == 1.0D,
                "A steak in hand was not noticed");
        helper.assertTrue(fed.heldFoodQuality() > 0.0D,
                "A steak was worth nothing");
        helper.assertTrue(fed.handsEmpty() == 0.0D,
                "Hands holding a steak read as empty");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void ownerVitalsAreRead(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        Player owner = fixture.owner();
        owner.setHealth(owner.getMaxHealth() / 2.0F);
        TlmOwnerFactReader reader = new TlmOwnerFactReader();

        OwnerFacts facts = reader.read(
                owner,
                helper.getLevel().getGameTime()
        );
        helper.assertTrue(
                facts.healthFraction() > 0.4D && facts.healthFraction() < 0.6D,
                "Half health read as " + facts.healthFraction()
        );
        helper.assertTrue(facts.onFire() == 0.0D,
                "An owner who is not alight read as burning");
        helper.assertTrue(!Double.isNaN(facts.foodFraction()),
                "A real player had no hunger reading");
        helper.succeed();
    }

    /** No owner is not the same as an owner with nothing going on. */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void noOwnerReadsAsUnknown(GameTestHelper helper) {
        TlmOwnerFactReader reader = new TlmOwnerFactReader();
        OwnerFacts facts = reader.read(
                null,
                helper.getLevel().getGameTime()
        );
        helper.assertTrue(Double.isNaN(facts.healthFraction()),
                "A missing owner reported a health reading");
        helper.assertTrue(Double.isNaN(facts.holdingFood()),
                "A missing owner reported what they were holding");
        helper.succeed();
    }

    // What is lying about.

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void thrownFoodBecomesVisible(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        EntityMaid maid = fixture.maid();
        ItemEntity steak = drop(helper, Items.COOKED_BEEF, 2, 2, 1);
        TlmAffordancePerceptionService perception =
                new TlmAffordancePerceptionService();

        List<ItemEntity> seen = perception.queryLooseFood(
                maid,
                8,
                helper.getLevel().getGameTime()
        );
        helper.assertTrue(seen.contains(steak),
                "A steak dropped beside her was not noticed, saw "
                        + seen.size() + " item(s)");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void inediblesAreNotAdvertisedAsFood(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ItemEntity cobble = drop(helper, Items.COBBLESTONE, 2, 2, 1);
        TlmAffordancePerceptionService perception =
                new TlmAffordancePerceptionService();

        List<ItemEntity> seen = perception.queryLooseFood(
                fixture.maid(),
                8,
                helper.getLevel().getGameTime()
        );
        helper.assertFalse(seen.contains(cobble),
                "Cobblestone was advertised as something to eat");
        helper.succeed();
    }

    /**
     * Nothing reports an item being picked up, so its advertisement has to
     * lapse on its own. This is the one place the index's expiry is what keeps
     * her from walking to a patch of empty ground.
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 200
    )
    public static void takenFoodStopsBeingAdvertised(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        EntityMaid maid = fixture.maid();
        ItemEntity steak = drop(helper, Items.COOKED_BEEF, 2, 2, 1);
        TlmAffordancePerceptionService perception =
                new TlmAffordancePerceptionService();
        long gameTime = helper.getLevel().getGameTime();

        helper.assertTrue(
                perception.queryLooseFood(maid, 8, gameTime).contains(steak),
                "The steak was never noticed to begin with"
        );
        steak.discard();

        // Past the advertisement's life, without it ever being re-observed.
        //
        // 只看她脚边这一圈。那个 8 是 topK 不是半径——广告板的空间范围是
        // PERCEPTION_RANGE，够得着隔壁那格结构，于是不加这一句时这条断言真正
        // 在说的是"整个感知范围内没有任何食物广告"，而隔壁摆什么不归它管。
        // 表现为加进来一批无关的测试就把它挤红，且红的理由与它检验的东西无关。
        long later = gameTime + 120L;
        List<ItemEntity> nearby = perception.queryLooseFood(maid, 8, later)
                .stream()
                .filter(item -> item.distanceToSqr(maid) < 8.0D * 8.0D)
                .toList();
        helper.assertTrue(
                nearby.isEmpty(),
                "A vanished steak was still being advertised: " + nearby
        );
        helper.succeed();
    }

    // Fixtures.

    // The teleport leash, as something she plans around rather than suffers.

    /**
     * 她自己走不到会被拉回来的地方去。
     *
     * <p>兜底传送一直是发生在她身上的事，不是她知道的事：任何计划都可以把
     * 目的地设在二十四格之外，走到了就被拽回主人身边，看起来像瞬移 bug。
     * 检验点选在所有移动写入的唯一出口上——后撤、追击、差事、消遣都从这里
     * 过，所以规则只需要在这里成立一次。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void sheIsNeverSentPastTheLeash(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        Vec3 owner = fixture.owner().position();
        Vec3 tooFar = owner.add(OwnerFollowPolicy.TELEPORT_DISTANCE * 4.0D,
                0.0D, 0.0D);

        FreedomMovement.write(
                fixture.maid(), new WalkTarget(tooFar, 0.6F, 1)
        );

        Vec3 sent = fixture.maid().getBrain()
                .getMemory(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("她没有被派往任何地方"))
                .getTarget()
                .currentPosition();
        helper.assertTrue(
                !OwnerFollowPolicy.INSTANCE.shouldTeleport(
                        sent.distanceToSqr(owner)
                ),
                "她被派往主人 " + sent.distanceTo(owner)
                        + " 格外，走到就会被传送回来"
        );
        // 而且是朝着原本想去的方向走到边界，不是被打回原地——否则"限制在
        // 范围内移动"就成了"不许移动"。
        helper.assertTrue(
                sent.x > fixture.maid().getX(),
                "目的地被压回了她身后，她根本没有往想去的方向走"
        );
        helper.succeed();
    }

    /**
     * 后撤同样受这条线约束，而且是按方向约束。
     *
     * <p>后撤挑的是朝向而不是点：拿"最开阔的方向"再截一刀，会让她一头扎进
     * 绳子绷紧的那一侧然后走两步就停。所以牵引绳要在逐扇区测距里一起算，
     * 这条钉住结果——她退到的地方必须仍在绳内。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aRetreatStaysInsideTheLeash(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        EntityMaid maid = fixture.maid();
        // 站在夹具地板西端，威胁在她西侧，于是后撤只会朝东。地板本身给得起
        // 好几格，两次测量的差别因此只可能来自牵引绳。
        maid.setPos(GameTestPositions.center(helper, 2, 2, 1));
        Vec3 threat = maid.position().subtract(2.0D, 0.0D, 0.0D);
        // 主人远在威胁那一侧，且已经快到绳子尽头：远离威胁的那个方向，
        // 正好也是走出传送范围的方向。
        Vec3 owner = maid.position().subtract(
                OwnerFollowPolicy.TELEPORT_DISTANCE - 2.0D, 0.0D, 0.0D
        );
        fixture.owner().setPos(owner);

        Vec3 escape = RetreatSpace.escapeTo(
                maid, List.of(threat), PerceptionRange.BLOCKS
        );

        helper.assertTrue(
                escape != null,
                "绳子把她钉住了；限制在范围内移动不等于不许移动"
        );
        // 而且确实是在后撤，不是"没动所以当然没出界"。
        helper.assertTrue(
                escape.distanceTo(threat) > maid.position().distanceTo(threat),
                "她选的落点离威胁更近，这根本不是一次后撤"
        );
        helper.assertTrue(
                !OwnerFollowPolicy.INSTANCE.shouldTeleport(
                        escape.distanceToSqr(owner)
                ),
                "她退到主人 " + escape.distanceTo(owner)
                        + " 格外，正好是会被传送回战斗里的距离"
        );
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Player owner = helper.makeMockPlayer();
        owner.setPos(GameTestPositions.center(helper, 1, 2, 1));
        EntityMaid maid = maid(helper, owner, 7, 2, 1);
        return new Fixture(owner, maid);
    }

    private static EntityMaid maid(
            GameTestHelper helper,
            Player owner,
            int x,
            int y,
            int z
    ) {
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(GameTestPositions.center(helper, x, y, z));
        maid.setTame(true);
        maid.setTask(
                TaskManager.findTask(FreedomMaidTask.UID).orElseThrow()
        );
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
        return maid;
    }

    private static ItemEntity drop(
            GameTestHelper helper,
            net.minecraft.world.item.Item item,
            int x,
            int y,
            int z
    ) {
        Vec3 position = GameTestPositions.center(helper, x, y, z);
        ItemEntity entity = new ItemEntity(
                helper.getLevel(),
                position.x,
                position.y,
                position.z,
                new ItemStack(item)
        );
        // Freshly spawned drops ignore anyone for a moment; the point here is
        // whether she can see it, not whether she may take it yet.
        entity.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void lookAt(Player owner, EntityMaid maid) {
        Vec3 offset = maid.getEyePosition().subtract(owner.getEyePosition());
        double flat = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        float yaw = (float) (Math.toDegrees(
                Math.atan2(offset.z, offset.x)
        ) - 90.0D);
        float pitch = (float) -Math.toDegrees(Math.atan2(offset.y, flat));
        owner.setYRot(yaw);
        owner.setYHeadRot(yaw);
        owner.setXRot(pitch);
    }

    private record Fixture(Player owner, EntityMaid maid) {
    }
}
