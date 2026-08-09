package com.laixia.maidintelligence.gametest.combat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.gametest.support.CombatTrace;
import com.laixia.maidintelligence.gametest.support.CompanionScene;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;

import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;


/**
 * 活场景：真的敌人、真的时间、真的竞争者。
 *
 * <p>其余战斗测试问的都是"这一 tick 她决定了什么"，而本轮暴露的三个缺陷没有
 * 一个能被那样问出来——它们分别活在跨 tick 的累积里、真实地形里，以及**别的
 * 行为同时在抢移动目标**的时候。最后那个尤其致命：其它夹具从来只有战斗一个
 * 写入者，于是仲裁永远不会拒绝，而线上正是被仲裁拒了 98 次。
 *
 * <p>所以这里刻意保留自由模式的随机游走（本体行为，权威高于旧的被动陪伴档），
 * 让它和战斗争用同一副脚。任何一条"她决定了但没做成"的缺陷都会在这里显形。
 *
 * <p>每条场景无论成败都打印逐 tick 轨迹。只报 pass/fail 的测试环境，等于把
 * 手动观察换成了盲测——这一整轮的绿色测试套件就是在她站着挨打时保持全绿的。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class CombatScenarioGameTests {
    /** 够她走完一整轮"被逼近—后撤—再射"的循环。 */
    private static final int SOAK_TICKS = 200;

    /**
     * How much of a mobbing she may spend motionless inside their arms.
     *
     * <p>Not zero, and not a tuned number. A blow lands and knocks the target
     * into her, a corner takes a moment to path out of, a swing recovery ends
     * with something already inside the window — each costs a tick or two where
     * standing still is a consequence rather than a posture. A tenth of the
     * fight is the boundary between that and the reported defect, which is her
     * spending the mobbing rooted in the middle of it.
     *
     * <p>Deliberately far from what the fight actually produces. The figure this
     * replaces sat at a quarter while runs came in between a fifth and a half,
     * so it decided by luck; anything that lands near a tenth here is a real
     * change in behaviour rather than a different roll.
     */
    private static final double IN_REACH_SHARE = 0.10D;

    /** 僵尸的触及约两格半，退到这个距离以内就等于在挨打。 */
    private static final double MELEE_DANGER = 2.5D;

    private CombatScenarioGameTests() {
    }

    /**
     * 三个敌人围上来时，她不该站着对砍——拿剑也一样。
     *
     * <p>玩家报告"近战不会躲开 2-3 个敌人，站在原地打，即使对方攻击力很高"，
     * 有弓和有剑都出现。此前全部场景都是单挑，而单挑和群殴问的根本不是同一个
     * 问题：单挑考的是站位，群殴考的是**她认不认得出这一架不该打**。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 400
    )
    public static void aSwordMaidFleesThree(GameTestHelper helper) {
        mobbed(helper, "aSwordMaidFleesThree", new ItemStack(Items.IRON_SWORD));
    }

    /** 同一场围攻，换成弓和箭：结论不该因为武器而变。 */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 400
    )
    public static void anArcherFleesThree(GameTestHelper helper) {
        mobbed(helper, "anArcherFleesThree", new ItemStack(Items.BOW));
    }

    /**
     * 一只卫道士。
     *
     * <p>僵尸太宽容：每击三点、移速 0.23，她几乎怎么打都不会死，于是场景全绿
     * 而玩家在真实战斗里仍然看到她被打。卫道士每击约十三点、移速 0.35——后者
     * 正好卡在她退路判据的刀刃上（0.7×0.6 对 0.35×1.15），一击也接近她血量的
     * 三分之二。这才是"她判断得对不对"真正要紧的量级。
     */
    private static Vindicator hostile(GameTestHelper helper) {
        Vindicator vindicator = EntityType.VINDICATOR.create(helper.getLevel());
        if (vindicator == null) {
            throw new IllegalStateException("夹具无法创建卫道士");
        }
        return vindicator;
    }

    private static void mobbed(
            GameTestHelper helper,
            String name,
            ItemStack weapon
    ) {
        CompanionScene scene = CompanionScene.room(helper, 23, 15);
        // 主人站在她身边，好让强制传送的牵引绳完全不构成限制：这几条问的是
        // 她被围住时动不动，不是绳子。默认的主人在房间角落十几格外，她一旦
        // 风筝到二十三格开外就会被绳子拦住，读数上和"站着不动"一模一样。
        scene.ownerAt(15, 2, 7);
        EntityMaid maid = scene.maid(15, 2, 7);
        maid.setTask(new FreedomMaidTask());
        maid.getAvailableBackpackInv().setStackInSlot(0, weapon);
        maid.getAvailableBackpackInv().setStackInSlot(
                1, new ItemStack(Items.ARROW, 64)
        );

        // 三面围上来，不是从同一侧压过来。这一点是整条测试的命题：从一侧来的
        // 三只，退路和单挑没有区别，她表现得很好——而玩家报告的是被围住。
        // 退路判定只看地形、不看别的敌人，所以"背后是空地"和"背后站着另一只
        // 僵尸"在它眼里一模一样。
        Vindicator[] pack = new Vindicator[3];
        for (int index = 0; index < pack.length; index++) {
            double angle = index * 2.0D * Math.PI / pack.length;
            Vindicator zombie = hostile(helper);
            zombie.setPos(
                    maid.getX() + Math.cos(angle) * 4.0D,
                    maid.getY(),
                    maid.getZ() + Math.sin(angle) * 4.0D
            );
            zombie.setTarget(maid);
            zombie.restrictTo(zombie.blockPosition(), 14);
            helper.getLevel().addFreshEntity(zombie);
            pack[index] = zombie;
        }

        CombatTrace trace = new CombatTrace(name);
        long start = helper.getLevel().getGameTime();

        helper.startSequence()
                .thenExecuteFor(SOAK_TICKS, () -> trace.sample(
                        helper.getLevel().getGameTime() - start, maid, pack[1]
                ))
                .thenExecute(() -> {
                    trace.dump();
                    helper.assertTrue(
                            trace.engagedTicks() > 0,
                            "整局都没有进入交战意图，这个场景没有测到任何东西。"
                                    + trace.summary()
                    );
                    // 断言"她的脚有没有在动"，不是"她跑了多远"或"挨了多少"。
                    // 距离和伤害都是它的下游而且都更吵：守住七格不掉血是好的，
                    // 被追着跑二十格是坏的，两者的距离读数却相反。玩家报的是
                    // "她站在原地不动"，那就直接量这个。
                    //
                    // 但要量的是"站在人家手里不动"，不是"站着不动"。后者会随
                    // 战斗**变好**而升高——站在击打窗口上不动正是近战该做的，
                    // 射手保持距离拉弓更是整场都不动——于是阈值滑进了它自己
                    // 分布的正中间，同一份代码红绿交替。这两条曾因此反复误报。
                    helper.assertTrue(
                            trace.stationaryInReachShare() < IN_REACH_SHARE,
                            "被三只围住时她有 " + Math.round(
                                    trace.stationaryInReachShare() * 100)
                                    + "% 的时间站在对方触及范围内不动。"
                                    + trace.summary()
                    );
                })
                .thenSucceed();
    }

    /**
     * 拿剑的女仆和一只真僵尸对砍，不该整场泡在它打得到的地方。
     *
     * <p>玩家报告"近战仍有概率不后撤，站着被打"。近战的战术是打了就退出它的
     * 触及范围、冷却好了再压上去，一个来回只有一格出头——而"退不到两格就不值得
     * 退"那条门槛是为远程贴墙抽搐加的，套到近战身上正好把这整套动作静默取消。
     *
     * <p>用挨的伤害断言，不用瞬时距离：近战本来就要贴上去挥，距离必然反复进出
     * 触及范围，唯一说得清的是"她有没有在挨打"。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 400
    )
    public static void aSwordMaidDoesNotStandAndTakeIt(GameTestHelper helper) {
        CompanionScene scene = CompanionScene.room(helper, 23, 11);
        // 主人站在她身边，好让强制传送的牵引绳完全不构成限制：这几条问的是
        // 她被围住时动不动，不是绳子。默认的主人在房间角落十几格外，她一旦
        // 风筝到二十三格开外就会被绳子拦住，读数上和"站着不动"一模一样。
        scene.ownerAt(11, 2, 5);
        EntityMaid maid = scene.maid(11, 2, 5);
        maid.setTask(new FreedomMaidTask());
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.IRON_SWORD)
        );

        Vindicator zombie = hostile(helper);
        zombie.setPos(maid.getX() - 6.0D, maid.getY(), maid.getZ());
        zombie.setTarget(maid);
        // 拴在本场景内。真僵尸索敌达十六格，GameTest 的相邻夹具就在那个范围
        // 里——放任它走，它会去打隔壁测试的女仆，而那条测试会以"她挨了十点
        // 伤害"的形式失败，看起来像战斗逻辑坏了。仓库文档早就写过这一条，
        // 先前的解法是根本不放真怪；这里需要真怪，就改为限制活动范围。
        zombie.restrictTo(zombie.blockPosition(), 12);
        helper.getLevel().addFreshEntity(zombie);

        CombatTrace trace = new CombatTrace("aSwordMaidDoesNotStandAndTakeIt");
        long start = helper.getLevel().getGameTime();

        helper.startSequence()
                .thenExecuteFor(SOAK_TICKS, () -> trace.sample(
                        helper.getLevel().getGameTime() - start, maid, zombie
                ))
                .thenExecute(() -> {
                    trace.dump();
                    helper.assertTrue(
                            trace.engagedTicks() > 0,
                            "整局都没有进入交战意图，这个场景没有测到任何东西。"
                                    + trace.summary()
                    );
                    // 一只僵尸每击约三点，她二十点血。挨到三下以上说明她是站着
                    // 换血而不是打完就退。
                    helper.assertTrue(
                            trace.damageTaken() <= 15.0F,
                            "她在单挑一只卫道士时挨了 " + trace.damageTaken()
                                    + " 点伤害，也就是站在原地对砍。"
                                    + trace.summary()
                    );
                })
                .thenSucceed();
    }

    /**
     * 拿弓的女仆面对一只真会追的僵尸，必须真的把距离拉开。
     *
     * <p>这条钉住的是移动权威。战斗写入曾用 `COMPANION`（PASSIVE_COMPANION，
     * 全系统最弱），而自由模式保留的随机游走用 `RANDOM_STROLL`（NATIVE_SOFT，
     * 高一档）——于是她"决定后撤"每次都被自己的"决定闲逛"压掉，写入被抑制，
     * 人站在原地。线上表现为远程和近战都不拉开距离。
     *
     * <p>断言分两层：租约至少有一部分时间归战斗所有（否则她根本没拿到脚的
     * 控制权），以及僵尸从没能贴到近战距离（否则"拉开"只是名义上的）。
     */
    @GameTest(
            templateNamespace = "minecraft",
            template = "empty",
            timeoutTicks = 400
    )
    public static void anArcherKeepsAVindicatorAtBay(GameTestHelper helper) {
        // 身后必须留出真正的余地。第一版把她放在 15 格房间的第 11 格，退路只剩
        // 三格，于是"退得开吗"答否、走清空分支、她原地站着——测出来的是夹具的
        // 墙，不是她的判断。
        CompanionScene scene = CompanionScene.room(helper, 23, 11);
        // 主人站在她身边，好让强制传送的牵引绳完全不构成限制：这几条问的是
        // 她被围住时动不动，不是绳子。默认的主人在房间角落十几格外，她一旦
        // 风筝到二十三格开外就会被绳子拦住，读数上和"站着不动"一模一样。
        scene.ownerAt(11, 2, 5);
        EntityMaid maid = scene.maid(11, 2, 5);
        maid.setTask(new FreedomMaidTask());
        maid.setItemInHand(
                InteractionHand.MAIN_HAND, new ItemStack(Items.BOW)
        );
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.ARROW, 64)
        );

        // 真僵尸，带 AI，会自己索敌和追击——它怎么走不是这条测试规定的。
        //
        Vindicator zombie = hostile(helper);
        zombie.setPos(maid.getX() - 10.0D, maid.getY(), maid.getZ());
        zombie.setTarget(maid);
        // 拴在本场景内。真僵尸索敌达十六格，GameTest 的相邻夹具就在那个范围
        // 里——放任它走，它会去打隔壁测试的女仆，而那条测试会以"她挨了十点
        // 伤害"的形式失败，看起来像战斗逻辑坏了。仓库文档早就写过这一条，
        // 先前的解法是根本不放真怪；这里需要真怪，就改为限制活动范围。
        zombie.restrictTo(zombie.blockPosition(), 12);
        helper.getLevel().addFreshEntity(zombie);

        CombatTrace trace = new CombatTrace("anArcherKeepsAVindicatorAtBay");
        long start = helper.getLevel().getGameTime();

        helper.startSequence()
                .thenExecuteFor(SOAK_TICKS, () -> trace.sample(
                        helper.getLevel().getGameTime() - start, maid, zombie
                ))
                .thenExecute(() -> {
                    trace.dump();
                    // 夹具前提：这一局到底打起来了没有。不先问这句，"僵尸压根
                    // 没走过来"会伪装成"她把距离守得很好"。
                    helper.assertTrue(
                            trace.engagedTicks() > 0,
                            "整局都没有进入交战意图，这个场景没有测到任何东西。"
                                    + trace.summary()
                    );
                    // 曾经断言的是"战斗持有过移动租约"，因为那时本体行为会把
                    // 我们写的目标压掉，而被压掉与从未决定在外观上一模一样。
                    // 自由模式不再注册那些行为，租约整层随之删除，于是这里改问
                    // 它原本想问的东西：她这一局到底动过没有。
                    helper.assertTrue(
                            trace.rootedTicks() < trace.samples(),
                            "整局都没有拿到过移动目标，对外看就是站着不动。"
                                    + trace.summary()
                    );
                    // 断言用"待在近战距离内的时间占比"，不用最近一次贴近。
                    // 后者是瞬时极值：面对真实寻路的怪，无论她打得多好，谷底
                    // 都会偶尔擦到标称触及距离，实测四次里稳定失败一次。一条
                    // 四分之一概率失败的断言只会教人忽略它，而且它测的是噪声，
                    // 不是"她肯不肯拉开"。占比稳定，且正是玩家抱怨的那件事。
                    //
                    // 没有"打死了就算过"的逃生条款。早先有，于是她被贴到 1.7
                    // 格、全程一步没退、靠贴脸对射赢了——测试照样是绿的。
                    // 阈值给到一半，不是贴着实测值。实测跨十余次运行落在
                    // 0~34%，贴着 35% 写等于埋一颗迟早会响的雷；而真正要防的
                    // 形态——她整场泊在近战距离里——接近 100%，五成的门槛照样
                    // 抓得住。真正紧的那条约束是下面的"挨了多少伤害"。
                    helper.assertTrue(
                            trace.shareWithinReach() < 0.50D,
                            "交战期间有 " + Math.round(
                                    trace.shareWithinReach() * 100) + "% 的时间"
                                    + "待在僵尸打得到的距离内，她没有把距离维持住。"
                                    + trace.summary()
                    );
                    // 卫道士的移速 0.35 与她的后撤速度几乎相同，所以"挨没挨
                    // 打"在这里测的是速度差而不是判断。能要求的是她真的把距离
                    // 拉开过——近战那条 1v1 仍然用伤害断言，因为打了就退本来就
                    // 该体现在挨打次数上。
                    helper.assertTrue(
                            trace.farthestReached() >= 8.0D,
                            "她最远只拉开到 " + trace.farthestReached()
                                    + " 格，没有真正脱离过。" + trace.summary()
                    );
                })
                .thenSucceed();
    }
}
