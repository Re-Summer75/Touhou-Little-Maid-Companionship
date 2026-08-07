package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.perception.PerceptionRange;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.ThreatProfile;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
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
}
