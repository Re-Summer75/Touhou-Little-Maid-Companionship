package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.threat.ThreatRelation;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.execution.MeleeSwing;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ThreatProfile;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ThreatReachLedger;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import com.laixia.maidintelligence.gametest.support.CombatProbe;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 端到端：她对敌人的量,是不是真的问过敌人。
 *
 * <p>风险裁决、姿态选择、退避收益全都建立在三个数上——对方多远能打到、一下多
 * 疼、多久一下。这三个数一度是常量,于是所有生物在她眼里是同一个生物,而"打不
 * 打得过"回答的是那个不存在的生物。纯逻辑测试喂不出这一层,它接收的正是这三个
 * 数,所以只能在真实世界里对着真实生物问。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class ThreatMeasurementGameTests {
    /** 夹具给她临时加的那三格手长。 */
    private static final UUID A_LONGER_ARM =
            UUID.fromString("8f3d1c72-5b6e-4a19-9c2f-1d4e7a0b6c53");

    private static final double GRANTED_REACH = 3.0D;

    private ThreatMeasurementGameTests() {
    }

    /**
     * 敌人的攻击距离、伤害、频率都要问它自己。
     *
     * <p>这三样曾是三个常量套在所有生物上：2.5 格、每秒一次、不声明伤害就按 9。
     * 那不是风险评估里的舍入误差,那就是风险评估的输入——末影人的手长、恶魂的
     * 射程、蜘蛛的攻击间隔被抹成同一组数字,于是"打不打得过"回答的是一个不存在
     * 的生物。
     *
     * <p>断言用体型差异很大的两种生物对比,而不是押某个具体数值:数值会随版本和
     * 模组变,"大的比小的手长"不会。
     */
    /**
     * 追着的敌人**活着脱离了感知**，攻击记忆要跟着放——不放她就永远静止。
     *
     * <p>玩家稳定复现：敌人不是死在感知内、而是以别的方式离开感知半径，
     * 之后她不再执行任何行为，直到手动更新状态。每件差事的资格判据都要求
     * {@code ATTACK_TARGET} 为空，而旧清理只肯扔"尸体"——活着走丢的从没
     * 人管。感知定义这一仗：脱离感知与死亡同款处理。
     */
    @GameTest(batch = "threat7", templateNamespace = "minecraft",
            template = "empty", timeoutTicks = 400)
    public static void aFoeWhoLeftHerSensesIsLetGoOf(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 7, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.IRON_SWORD)
        );
        Zombie foe = new Zombie(helper.getLevel());
        foe.setPos(maid.getX() + 5.0D, maid.getY(), maid.getZ());
        foe.setNoAi(true);
        helper.getLevel().addFreshEntity(foe);

        boolean[] engaged = new boolean[]{false};
        for (int tick = 5; tick <= 80; tick += 5) {
            helper.runAfterDelay(tick, () -> {
                if (maid.getBrain()
                        .getMemory(MemoryModuleType.ATTACK_TARGET)
                        .isPresent()) {
                    engaged[0] = true;
                }
            });
        }
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(
                    engaged[0],
                    "She never engaged, so the scene proves nothing"
            );
            // 活着离开感知：垂直挪出四十格（不横移，免得闯进邻座场地）。
            foe.teleportTo(foe.getX(), foe.getY() + 40.0D, foe.getZ());
            foe.setNoGravity(true);
        });
        helper.runAfterDelay(220, () -> {
            helper.assertTrue(
                    maid.getBrain()
                            .getMemory(MemoryModuleType.ATTACK_TARGET)
                            .isEmpty()
                            && maid.getTarget() == null,
                    "A foe who left her senses was never let go of — the"
                            + " total standstill the player reproduces"
            );
            maid.discard();
            foe.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "threat1", templateNamespace = "minecraft", template = "empty")
    public static void sheMeasuresEachFoeNotAStereotype(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 5, 5);
        EntityMaid maid = scene.maid(2, 2, 2);

        Zombie small = new Zombie(helper.getLevel());
        small.setPos(maid.getX() + 2.0D, maid.getY(), maid.getZ());
        Ravager large = EntityType.RAVAGER.create(helper.getLevel());
        large.setPos(maid.getX() + 3.0D, maid.getY(), maid.getZ());

        double smallReach = ThreatProfile.reach(small, maid);
        double largeReach = ThreatProfile.reach(large, maid);
        helper.assertTrue(
                largeReach > smallReach,
                "A ravager and a zombie were credited with the same reach ("
                        + largeReach + " vs " + smallReach
                        + "), which is the flattening this replaced"
        );

        double smallHit = ThreatProfile.strikeDamage(small);
        double largeHit = ThreatProfile.strikeDamage(large);
        helper.assertTrue(
                largeHit > smallHit,
                "A ravager hits for " + largeHit + " and a zombie for "
                        + smallHit + ", so damage is not being read from them"
        );

        // 远程生物按它自己的索敌范围算,而不是一个写死的十六格。
        Skeleton shooter = EntityType.SKELETON.create(helper.getLevel());
        shooter.setPos(maid.getX() + 4.0D, maid.getY(), maid.getZ());
        double shooterReach = ThreatProfile.reach(shooter, maid);
        helper.assertTrue(
                shooterReach > largeReach,
                "A skeleton's reach came out as " + shooterReach
                        + ", no further than a melee attacker's"
        );
        helper.assertTrue(
                shooterReach <= PerceptionRange.BLOCKS,
                "Its reach was reported as " + shooterReach
                        + " blocks, beyond anything she can perceive"
        );
        helper.succeed();
    }

    /**
     * 墙那边的那群不算。
     *
     * <p>威胁扫描改成自己扫世界之后漏掉了这一条,而漏掉它不会让任何测试变红——
     * 夹具里的敌人都站在空地上。代价是在真实世界里付的:地下和夜里,十六格内几乎
     * 永远隔着墙站着点什么,于是她定价的那场仗根本不是屋里这一场。表现出来是
     * 要么对着空气后撤,要么穿过身边三只去够一只她根本够不到的。
     *
     * <p>所以这条直接钉"看不看得见",而不是钉它下游的裁决:下游有十几个数一起
     * 决定结果,而这里错的是最上面那个。
     */
    @GameTest(batch = "threat2", templateNamespace = "minecraft", template = "empty")
    public static void sheDoesNotCountWhatSheCannotSee(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 7, 5);
        EntityMaid maid = scene.maid(1, 2, 2);

        Zombie behindWall = new Zombie(helper.getLevel());
        behindWall.setPos(maid.getX() + 4.0D, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, behindWall);

        // 先确认没有墙时她确实数得到它,否则下面的"数不到"什么都不说明。
        //
        // 问的是"这一只在不在清单里",不是"清单里有几个"。GameTest 共用一个
        // 世界,邻座夹具的怪就在十六格内,按数量断言等于让这条测试的成败取决于
        // 隔壁这一轮放了几只怪。
        helper.assertTrue(
                sees(maid, behindWall),
                "空地上四格外的僵尸都没被扫到,这条测试没有验证到任何东西"
        );

        // 砌一堵挡在中间的墙。
        for (int y = 2; y <= 4; y++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(3, y + LIFT - 1, z), Blocks.STONE);
            }
        }
        helper.assertFalse(
                maid.hasLineOfSight(behindWall),
                "夹具没能挡住视线"
        );

        helper.assertFalse(
                sees(maid, behindWall),
                "隔着一堵墙的僵尸仍然进了她的威胁清单,于是她按一场看不见的仗"
                        + "决定站位"
        );
        helper.succeed();
    }

    /** 这一只在不在她当前的威胁清单里。 */
    private static boolean sees(EntityMaid maid, Zombie zombie) {
        // 每次都用新的扫描器:生产实例带 5 tick 缓存,同一 tick 内问两次会拿到
        // 砌墙之前的那一份。
        for (ScannedThreat threat : new TlmThreatScanner().scan(maid)) {
            if (threat.entity() == zombie) {
                return true;
            }
        }
        return false;
    }

    /** 与 {@link CompanionScene} 的抬升保持一致,好把墙砌在她那一层。 */
    private static final int LIFT = 12;

    /**
     * 主人身边的敌人算数，哪怕她自己离得够远。
     *
     * <p>感知此前只有她一个中心，于是主人走开十几格之后，围着主人的那一圈就
     * 落在她的球外——她"看不见"正在打她主人的东西，而那正是她在场的理由。
     *
     * <p>断言用"扫到没扫到"，不用"她去没去"：去不去是交战判据的事，这一条只
     * 问感知的形状对不对。
     */
    @GameTest(batch = "threat3", templateNamespace = "minecraft", template = "empty")
    public static void aHostileByTheOwnerIsSeenFromBeyondHerOwnReach(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 40, 6);
        // 主人站远处；她留在原地。两人相距超过她自己的感知半径。
        scene.ownerAt(34, 2, 3);
        EntityMaid maid = scene.maid(2, 2, 3);

        Zombie byTheOwner = new Zombie(helper.getLevel());
        byTheOwner.setPos(
                maid.getX() + 30.0D, maid.getY(), maid.getZ()
        );
        CompanionScene.placeInert(helper, byTheOwner);

        List<ScannedThreat> seen = new TlmThreatScanner().scan(maid);
        helper.assertTrue(
                seen.stream().anyMatch(t -> t.entity() == byTheOwner),
                "站在主人身边的敌人没有进入她的感知——"
                        + "她守的那个点没有算作第二个中心"
        );
        helper.succeed();
    }

    /**
     * 打得比它承认的远的东西，她挨一下就记住了。
     *
     * <p>模组怪物常把攻击距离硬写在自己的 Goal 里而从不覆写
     * {@code getMeleeAttackRangeSqr}，于是从外面读到的永远只是它的碰撞箱宽度。
     * 这里用一记来自僵尸、却发生在僵尸够不着的距离上的近战伤害来复现那种生物——
     * 判据不认识任何一个物种，所以复现方式和真的模组怪物没有区别。
     *
     * <p>褪去的那一半在 {@code verifyThreatReach} 里问，那是纯算术。这里问的是
     * **接上了没有**：事件筛得对不对、按物种记的那份读不读得回来。
     */
    @GameTest(batch = "threat4", templateNamespace = "minecraft", template = "empty")
    public static void sheLearnsTheReachAFoeWillNotAdmitTo(
            GameTestHelper helper
    ) {
        ThreatReachLedger.forgetEverything();
        CompanionScene scene = CompanionScene.room(helper, 9, 5);
        EntityMaid maid = scene.maid(2, 2, 2);

        Zombie liar = new Zombie(helper.getLevel());
        liar.setPos(maid.getX() + 4.0D, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, liar);

        double declared = ThreatProfile.declaredReach(liar, maid);
        helper.assertTrue(
                ThreatProfile.reach(liar, maid) <= declared + 1.0E-6D,
                "还没挨过打，她已经在给它加距离了"
        );

        // 它够不着，却打中了她——世界唯一肯说出口的那句话。
        maid.hurt(maid.damageSources().mobAttack(liar), 1.0F);
        double learned = ThreatProfile.reach(liar, maid);
        helper.assertTrue(
                learned > declared + 1.0D,
                "它从四格外打中了她，她给它记的还是 " + learned
                        + " 格（它自称 " + declared + "）"
        );

        // 弹射物不算：伤害来源里的直接实体是那支箭，按射手当时的距离学，
        // 学到的会是"骷髅够十五格"。
        ThreatReachLedger.forgetEverything();
        Skeleton shooter = EntityType.SKELETON.create(helper.getLevel());
        shooter.setPos(maid.getX() + 8.0D, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, shooter);
        Arrow arrow = new Arrow(
                helper.getLevel(), maid.getX(), maid.getY(), maid.getZ()
        );
        arrow.setOwner(shooter);
        helper.getLevel().addFreshEntity(arrow);
        maid.invulnerableTime = 0;
        maid.hurt(maid.damageSources().arrow(arrow, shooter), 1.0F);
        helper.assertTrue(
                ThreatProfile.reach(shooter, maid)
                        <= ThreatProfile.declaredReach(shooter, maid) + 1.0E-6D,
                "一支箭教会了她骷髅的近战够到距离"
        );
        helper.succeed();
    }

    /**
     * 她自己的够到距离长了，她站的位置就该跟着变。
     *
     * <p>上面那条问的是"敌人够多远"，这条问的是同一个问题的另一半——**她**够多远。
     * 玩家报的是"模组武器或者什么东西加了攻击距离，她并不知道"，而这一侧读代码看
     * 起来是通的：宿主 {@code EntityMaid} 覆写了 {@code getMeleeAttackRangeSqr}，
     * 返回 {@code (ENTITY_REACH 属性 + 好感度加成)²}，而本模组所有用到"她够多远"
     * 的地方都走这一个口子。
     *
     * <p>"看起来是通的"不是证据，所以这里真的去加一次。用属性修饰符而不是造一件
     * 武器：模组长武器给的正是这个修饰符，而物品注册表在 GameTest 起来时已经冻结，
     * 造不出新物品。第一条断言本身也有价值——它确认这个属性**长在女仆身上**，
     * 否则模组给的加成会被静默丢掉，连游戏本身都不认。
     */
    @GameTest(batch = "threat5", templateNamespace = "minecraft", template = "empty")
    public static void anythingThatLengthensHerArmMovesHerFeet(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 9, 5);
        EntityMaid maid = scene.maid(2, 2, 2);
        Zombie zombie = new Zombie(helper.getLevel());
        zombie.setPos(maid.getX() + 3.0D, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, zombie);

        double before = MeleeSwing.reach(maid, zombie);
        int stoodAt = MeleeSwing.standoff(maid, zombie);

        AttributeInstance reach =
                maid.getAttribute(ForgeMod.ENTITY_REACH.get());
        helper.assertTrue(
                reach != null,
                "女仆身上根本没有够到距离这个属性——模组武器给的加成无处可落，"
                        + "连游戏本身都不会认"
        );
        reach.addTransientModifier(new AttributeModifier(
                A_LONGER_ARM,
                "gametest reach",
                GRANTED_REACH,
                AttributeModifier.Operation.ADDITION
        ));
        try {
            double after = MeleeSwing.reach(maid, zombie);
            helper.assertTrue(
                    after >= before + GRANTED_REACH - 1.0E-6D,
                    "给了她三格额外的够到距离，她算出来只有 " + after
                            + "（原来 " + before + "）"
            );
            helper.assertTrue(
                    MeleeSwing.standoff(maid, zombie) > stoodAt,
                    "手长了三格，她站的位置还是 " + stoodAt + " 格"
            );
        } finally {
            reach.removeModifier(A_LONGER_ARM);
        }
        helper.succeed();
    }

    /**
     * 锁着别人的敌人，不算在打她。
     *
     * <p>玩家报的是"她在远处徘徊、迟迟不敢接近"。远程敌人的够到距离就是它的索敌
     * 范围，于是它在她感知半径内的任何位置都"已经够得到她"——一只在射牛的骷髅在
     * 承伤账本里和一只正瞄着她的骷髅完全相同，而她据此把自己钉在远处。
     *
     * <p>算术那一侧在 {@code verifyCombatCrowdPricing} 里问过。这里问的是**真实
     * 扫描器认不认得出这件事**：关系是从它自己的 {@code getTarget()} 读的，而那正是
     * "广播敌人的目标"这件事已经存在的形式——缺的从来不是信息，是花掉它的地方。
     */
    @GameTest(batch = "threat6", templateNamespace = "minecraft", template = "empty")
    public static void aFoeBusyWithSomebodyElseIsNotShootingHer(
            GameTestHelper helper
    ) {
        CompanionScene scene = CompanionScene.room(helper, 11, 5);
        EntityMaid maid = scene.maid(2, 2, 2);

        Skeleton archer = EntityType.SKELETON.create(helper.getLevel());
        archer.setPos(maid.getX() + 6.0D, maid.getY(), maid.getZ());
        CompanionScene.placeInert(helper, archer);
        Cow bystander = EntityType.COW.create(helper.getLevel());
        bystander.setPos(archer.getX() + 1.0D, archer.getY(), archer.getZ());
        CompanionScene.placeInert(helper, bystander);

        // 先取控制臂：同一只骷髅瞄着她。不先取的话"折价成零"可能只是因为它
        // 根本没被扫到，而那样这条测试什么也没说。
        archer.setTarget(maid);
        double atHer = incomingFrom(maid, archer, helper, "ATTACKING_MAID");
        helper.assertTrue(
                atHer > 0.0D,
                "瞄着她的骷髅算出来的承伤是 " + atHer + "，夹具没成立"
        );

        archer.setTarget(bystander);
        double atACow = incomingFrom(maid, archer, helper, "BUSY_ELSEWHERE");
        helper.assertTrue(
                atACow == 0.0D,
                "它转去射牛了，她的承伤账本里仍然记着 " + atACow
        );
        helper.succeed();
    }

    /** 扫一遍，确认关系，返回这一刻的承伤。 */
    private static double incomingFrom(
            EntityMaid maid,
            Skeleton archer,
            GameTestHelper helper,
            String wanted
    ) {
        // 每次都用新的扫描器：生产实例带 5 tick 缓存，同一 tick 内问两次会拿到
        // 改目标之前的那一份。
        List<ScannedThreat> seen = new TlmThreatScanner().scan(maid);
        ThreatRelation relation = null;
        for (ScannedThreat threat : seen) {
            if (threat.entity() == archer) {
                relation = threat.sample().relation();
            }
        }
        helper.assertTrue(
                relation != null,
                "骷髅根本没进威胁清单，这条测试测不到任何东西"
        );
        helper.assertTrue(
                wanted.equals(relation.name()),
                "它锁着的东西被读成了 " + relation + "，而不是 " + wanted
        );
        return CombatProbe.field(maid, seen).incomingDps();
    }
}
