package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ScannedThreat;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.ThreatProfile;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
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
    @GameTest(templateNamespace = "minecraft", template = "empty")
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
    @GameTest(templateNamespace = "minecraft", template = "empty")
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
}
