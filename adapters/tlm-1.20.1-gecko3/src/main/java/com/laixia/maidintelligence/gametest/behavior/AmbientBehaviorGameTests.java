package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.IdleGazePolicy;
import com.laixia.maidintelligence.feature.behavior.domain.combat.weapon
        .WeaponStowPolicy;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient.TlmIdleGaze;
import com.laixia.maidintelligence.feature.orchestration.tlm.ambient
        .TlmWeaponStow;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 每 tick 都发生、与"她此刻在做什么"无关的那些。
 *
 * <p>两条都属于"设计好了但从没接上"的那一类：环顾从来没有人写过注视目标，收武器
 * 的策略有纯 JVM 验证却零调用。所以这里问的不是算术对不对（那在纯 JVM 那侧），
 * 是**它们到底跑起来了没有**。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class AmbientBehaviorGameTests {
    /** 她的感知半径，也是她最早能看见一支箭的距离。 */
    private static final double SHOT_RANGE = 16.0D;

    /** 骷髅箭每 tick 的位移。 */
    private static final double ARROW_SPEED = 1.6D;

    /** 让开自己半个身位加上飞行物的判定余量，才算真的让开了。 */
    private static final double CLEARED_BODY = 0.6D;

    /** 闪避是让一步，不是横穿房间——上界比下界更容易忘，也更容易出事。 */
    private static final double A_STEP_ASIDE = 2.5D;

    private AmbientBehaviorGameTests() {
    }

    /**
     * 闲着的时候她会看向什么，那一眼要停得住，**而且要跟着她走**。
     *
     * <p>两条都是实机报出来的毛病换来的。停不住就是抽搐——每 tick 重挑一个方向不是
     * 环顾，这和游走落点踩的是同一条契约。
     *
     * <p>"跟着她走"是第二条：第一版记的是世界里一个固定的点，她站着不动时没问题，
     * 一走起来相对那个点就在移动——走过头要回头看，而原版会钳制头部偏航，表现是
     * 猛地一甩；上个台阶之后那个点低于视线，表现是盯着地面。玩家报的两个症状是同
     * 一个原因。改成记**方位**之后，每 tick 以她当下的位置重算，"往左前方看着"在
     * 她走动时保持成立。
     *
     * <p>用没有主人、周围也没有活物的场景，好让抽签必然落在"看某个方向"那一支上
     * ——另外两支盯的是实体，实体本来就会自己动，测不出这条。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void idlySheLooksAroundAndTheGlanceFollowsHer(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 6, 6);
        EntityMaid maid = scene.strayMaid(1, 2, 1);
        TlmIdleGaze gaze = TlmIdleGaze.create();
        long now = scene.gameTime();

        gaze.tick(maid, now);
        Vec3 first = lookAt(maid);
        helper.assertTrue(
                first != null,
                "闲着的时候没有任何人告诉她该看哪儿——她的头会停在上次被摆过的方向"
        );
        for (int tick = 1; tick < IdleGazePolicy.SHORTEST_GLANCE_TICKS; tick++) {
            gaze.tick(maid, now + tick);
            helper.assertTrue(
                    lookAt(maid).distanceTo(first) < 1.0E-6D,
                    "第 " + tick + " tick 就换了方向——那是抽搐，不是环顾"
            );
        }

        Vec3 before = maid.position();
        maid.setPos(before.x + 3.0D, before.y, before.z);
        gaze.tick(maid, now + 1);
        Vec3 moved = lookAt(maid);
        helper.assertTrue(
                Math.abs(moved.x - first.x - 3.0D) < 1.0E-6D
                        && Math.abs(moved.z - first.z) < 1.0E-6D,
                "她走了三格，注视点却留在原地——固定的点会让她回头看，"
                        + "而原版钳制头部偏航，表现就是猛地一甩"
        );
        helper.assertTrue(
                Math.abs(moved.y - maid.getEyeY())
                        <= Math.abs(IdleGazePolicy.INSTANCE.offsetY(0.0D)),
                "注视点相对她的视线高了或低了太多——低头盯地板就是这么来的"
        );
        helper.succeed();
    }

    /**
     * 墙那边的那一只不看。
     *
     * <p>玩家报的是"周围没有其它生物，她却盯着地面"。隔着方块的东西如果进得了候选，
     * 它多半在**脚下**——岩洞里的一只僵尸直线距离八格以内，注视点就落在正下方，
     * 于是她低头盯着地板，而地面上确实一只生物也没有。
     *
     * <p>**这条猜想被这条测试否掉了**：加过一句显式的视线检查，再把它去掉重跑——
     * 本条依旧绿。那份记忆的 {@code findAll} 自己就带视线过滤，她取不到墙后的东西。
     * 于是冗余的那一句删了，性质留在这里钉着：哪天宿主或原版不再过滤，红的是它。
     *
     * <p>断言方式：场上只有这一只、而且没有主人，所以"看活物"那一支只可能选中它。
     * 反复重挑四十次，一次都不该落在它身上。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void sheDoesNotStareThroughAWall(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 6, 6);
        EntityMaid maid = scene.strayMaid(1, 2, 1);
        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX() + 4.0D, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, zombie);
        seeOnly(maid, zombie);
        // 中间砌一堵墙，高到挡住视线。用**世界坐标**从她身上推：房间抬高十二格，
        // 而 helper.setBlock 收的是相对坐标——按相对坐标砌会把墙砌到虚空里，而那
        // 正是这条测试第一次绿得毫无意义的原因。
        for (int lift = 0; lift < 3; lift++) {
            helper.getLevel().setBlockAndUpdate(
                    BlockPos.containing(
                            maid.getX() + 2.0D,
                            maid.getY() + lift,
                            maid.getZ()
                    ),
                    Blocks.STONE.defaultBlockState()
            );
        }
        helper.assertFalse(
                maid.hasLineOfSight(zombie),
                "夹具没把视线挡住，这条什么都测不到"
        );

        TlmIdleGaze gaze = TlmIdleGaze.create();
        long now = scene.gameTime();
        for (int trip = 0; trip < 40; trip++) {
            gaze.tick(maid, now + (long) trip
                    * IdleGazePolicy.LONGEST_GLANCE_TICKS);
            Vec3 at = lookAt(maid);
            helper.assertTrue(
                    at.distanceTo(zombie.getEyePosition()) > 0.5D,
                    "她在盯着墙那边的僵尸——在真实世界里那多半是脚下岩洞里的一只，"
                            + "表现就是低头看地板"
            );
        }
        helper.succeed();
    }

    /**
     * 安静久了她会把武器收起来；周围不安静就一直拿着。
     *
     * <p>{@code WeaponStowPolicy} 早就写好、也有纯 JVM 验证，但**生产代码里一个调用
     * 都没有**，所以她一旦拔刀就再也不放下。这条钉的正是"接上了没有"。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void quietForLongEnoughSheStowsTheSword(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 4, 4);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        TlmWeaponStow stow = TlmWeaponStow.create();
        long now = scene.gameTime();
        int calm = WeaponStowPolicy.INSTANCE.calmTicks();

        stow.tick(maid, now);
        helper.assertTrue(
                maid.getMainHandItem().is(Items.IRON_SWORD),
                "刚见到她就把刀收了——安静得从第一次见到她开始算，不是从开天辟地"
        );
        stow.tick(maid, now + calm);
        helper.assertTrue(
                maid.getMainHandItem().isEmpty(),
                "安静满 " + calm + " tick 之后她仍握着 "
                        + maid.getMainHandItem().getItem()
                        + "——一直拿着刀的女仆读起来是卫兵不是陪伴"
        );
        helper.assertTrue(
                packHolds(maid),
                "刀离开了手，却没有进背包——那等于她把自己解除了武装"
        );
        helper.succeed();
    }

    /** 视野里有东西的时候不收：怪是成波来的，两波之间收刀要边挨打边拔。 */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void withSomethingAboutSheKeepsItInHand(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 6, 6);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX() + 4.0D, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, zombie);
        seeOnly(maid, zombie);

        TlmWeaponStow stow = TlmWeaponStow.create();
        long now = scene.gameTime();
        int calm = WeaponStowPolicy.INSTANCE.calmTicks();
        for (int tick = 0; tick <= calm * 2; tick += calm / 4) {
            stow.tick(maid, now + tick);
        }
        helper.assertTrue(
                maid.getMainHandItem().is(Items.IRON_SWORD),
                "视野里站着一只僵尸，她把刀收进了背包"
        );
        helper.succeed();
    }

    /**
     * 盾也是拿在手上的家伙，安静了同样要收。
     *
     * <p>盾**不是**武器种类的一种——{@code classifyFor} 认不出它——所以少问一句的
     * 表现是刀收了、盾还挂在副手上，比两样都拿着更怪。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void theShieldGoesAwayToo(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 4, 4);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        maid.setItemInHand(
                InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)
        );
        TlmWeaponStow stow = TlmWeaponStow.create();
        long now = scene.gameTime();

        stow.tick(maid, now);
        stow.tick(maid, now + WeaponStowPolicy.INSTANCE.calmTicks());
        helper.assertTrue(
                maid.getOffhandItem().isEmpty(),
                "刀收了，盾还挂在副手上——她只是换了个姿势站岗"
        );
        helper.assertTrue(
                maid.getMainHandItem().isEmpty(),
                "主手的刀没收"
        );
        helper.succeed();
    }

    /**
     * 背包满了就继续拿着，绝不丢在地上。
     *
     * <p>一件掉在地上的武器等于她自己解除了自己的武装。背包满只是这一次收不起来，
     * 不是一个需要靠丢东西解决的问题——她照样能在需要时换武器，因为换装走的是交换。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void aFullPackMeansSheKeepsHoldingIt(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 4, 4);
        EntityMaid maid = scene.maid(1, 2, 1);
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)
        );
        var pack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < pack.getSlots(); slot++) {
            pack.insertItem(slot, new ItemStack(Items.COBBLESTONE, 64), false);
        }
        TlmWeaponStow stow = TlmWeaponStow.create();
        long now = scene.gameTime();

        stow.tick(maid, now);
        stow.tick(maid, now + WeaponStowPolicy.INSTANCE.calmTicks());
        helper.assertTrue(
                maid.getMainHandItem().is(Items.IRON_SWORD),
                "背包塞满了，她把刀弄丢了——手里现在是 "
                        + maid.getMainHandItem().getItem()
        );
        helper.assertTrue(
                helper.getLevel().getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class,
                        maid.getBoundingBox().inflate(6.0D)
                ).isEmpty(),
                "刀被丢到了地上"
        );
        helper.succeed();
    }

    /**
     * 十六格外正对她射来的一箭，她会让开。
     *
     * <p>算术那一侧（{@code EvasionVerification}）已经问过"该不该让、往哪边让"，
     * 这里问的是**接上了没有、来不来得及**。所以不手动调 {@code consider}——挂上
     * 自由模式让整条链自己跑，跑不通就该红，那正是这个项目反复踩到的"设计好了、
     * 验证过了、从没被调用过"。
     *
     * <p>十六格是她的感知半径，也就是她最早能注意到这一支的时刻——预警窗口约十
     * tick，是这个能力最宽裕的一档。近处射来的箭她躲不掉，那条由
     * {@link com.laixia.maidintelligence.feature.behavior.domain.combat.DodgePolicy}
     * 的最短预警钉着。
     *
     * <p>箭关掉重力：三分之二秒的飞行里下坠近三格，那是弹道问题，不是横向闪避
     * 的问题，留着只会让这条测试在测别的东西。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 120
    )
    public static void sheStepsOutOfTheLineOfALongShot(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.walledRoom(helper, 9, 9);
        // 围墙不是装饰。共用的房间抬高十二格且四周悬空，而这条测试挂的是自由
        // 模式——她会自己游走出地板，摔下去正好九到十点，断言随即报"让开了却还
        // 是中了"。实测抓到过：来源 fall、落点 y=-60。那不是没躲开箭，那是她已经
        // 不在场了，连上面两条横移断言都失去意义。
        EntityMaid maid = scene.maid(4, 2, 4);
        maid.setTask(new FreedomMaidTask());

        double lineX = maid.getX();
        float unhurt = maid.getHealth();
        Arrow arrow = new Arrow(
                helper.getLevel(),
                maid.getX(),
                maid.getY() + maid.getBbHeight() / 2.0D,
                maid.getZ() - SHOT_RANGE
        );
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0.0D, 0.0D, ARROW_SPEED);
        helper.getLevel().addFreshEntity(arrow);

        helper.startSequence()
                .thenExecuteFor(28, () -> {
                })
                .thenExecute(() -> {
                    double aside = Math.abs(maid.getX() - lineX);
                    // 先确认这一箭真的飞过来了：不飞的话"没掉血"是白给的。
                    helper.assertTrue(
                            !arrow.isAlive()
                                    || arrow.getZ() > maid.getZ()
                                    - SHOT_RANGE / 2.0D,
                            "那支箭根本没飞——这条测试什么也没测到"
                    );
                    helper.assertTrue(
                            aside >= CLEARED_BODY,
                            "她一直站在弹道上，只横移了 " + aside + " 格"
                    );
                    // 上界和下界一样要紧。第一版按飞行时间保持，她以满速横移了
                    // 八格冲出房间——"躲开了"却把自己送到了别处，而只看下界的
                    // 断言对此一句话都不会说。
                    helper.assertTrue(
                            aside <= A_STEP_ASIDE,
                            "她不是让了一步，是横穿了房间 — 挪了 " + aside + " 格"
                    );
                    // 伤害来源要印出来。这条断言只说"她掉了血"，而掉血的原因
                    // 不止一种——中箭、摔下平台、隔壁夹具跑过来的怪，三者要改
                    // 的地方完全不同，只报一个数字分不出是哪一种。
                    helper.assertTrue(
                            maid.getHealth() >= unhurt,
                            "让开了却还是中了 — 掉了 "
                                    + (unhurt - maid.getHealth()) + " 点血，来源="
                                    + (maid.getLastDamageSource() == null
                                            ? "?"
                                            : maid.getLastDamageSource()
                                                    .getMsgId())
                                    + " 直接实体="
                                    + (maid.getLastDamageSource() == null
                                            || maid.getLastDamageSource()
                                                    .getDirectEntity() == null
                                            ? "?"
                                            : maid.getLastDamageSource()
                                                    .getDirectEntity()
                                                    .getType()
                                                    .toShortString())
                                    + " 落点 y=" + String.format(
                                            "%.1f", maid.getY())
                    );
                })
                .thenSucceed();
    }

    private static Vec3 lookAt(EntityMaid maid) {
        return maid.getBrain()
                .getMemory(MemoryModuleType.LOOK_TARGET)
                .map(PositionTracker::currentPosition)
                .orElse(null);
    }

    private static boolean packHolds(EntityMaid maid) {
        var pack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < pack.getSlots(); slot++) {
            if (pack.getStackInSlot(slot).is(Items.IRON_SWORD)) {
                return true;
            }
        }
        return false;
    }

    private static void seeOnly(EntityMaid maid, LivingEntity hostile) {
        maid.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(maid, List.of(hostile))
        );
    }
}
