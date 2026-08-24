package com.laixia.maidintelligence.gametest.pathing.patrol;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing
        .SureFootedNavigation;
import com.laixia.maidintelligence.gametest.support.PathwalkTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * 往返跑的车与仪表：怎么开、怎么记、怎么判卡住。路长什么样不归这里。
 *
 * <p>从 {@code RoundTripGameTests} 按职责拆出（单文件五百行的布局纪律）。
 * 拆的那一刀落在"驾驶"与"地形"之间：同一套驱动此前在三个场景类里各抄了
 * 一遍，而每次给读数带加一列都要抄三处——摔那一刻的供词就是这么漏掉的。
 */
final class BridgePatrol {
    /** 悬空桥面的基准高度，场景与夹具共用一个数。 */
    static final int DECK = 5;

    /** 一条测试盯多久：往返几趟够把边际时序问题跑出来。 */
    static final int WATCHED_TICKS = 400;

    private BridgePatrol() {
    }

    /**
     * 停滞表：连续多少 tick 没挪出半格。玩家报的"原地打转一段时间"不摔也
     * 不永久卡死，趟数断言抓不住它——只有停滞窗超时才现形。
     */
    static final class StallWatch {
        /** 容忍的最长原地时段：掉头、起跳预备都远短于它。 */
        static final int TOLERATED_TICKS = 100;

        private final EntityMaid maid;
        private double anchorX;
        private double anchorZ;
        private int anchorTick;
        private int longest;

        StallWatch(EntityMaid maid) {
            this.maid = maid;
            this.anchorX = maid.getX();
            this.anchorZ = maid.getZ();
        }

        void sample(int tick) {
            double moved = Math.hypot(
                    maid.getX() - anchorX, maid.getZ() - anchorZ
            );
            if (moved > 0.6D) {
                anchorX = maid.getX();
                anchorZ = maid.getZ();
                anchorTick = tick;
                return;
            }
            longest = Math.max(longest, tick - anchorTick);
        }

        int longest() {
            return longest;
        }
    }

    /** 执行器的行车记录；导航不是我们的类型时给个占位。 */
    static String diaryOf(EntityMaid maid) {
        // 报出它**是什么**："n/a" 分不清宿主地面导航（升级钩子没跑，那正是
        // 她摔下去的原因）与游泳导航（与寻路无关）——供词得说话。
        return maid.getNavigation() instanceof SureFootedNavigation sure
                ? sure.pathwalkDiary()
                : "diary=n/a(导航是 "
                        + maid.getNavigation().getClass().getSimpleName()
                        + "，没升级成认路的那套)";
    }

    /** 两侧护栏加高到视线高度，桥的两端敞着——掉头掉出去就是掉头的罪。 */
    static void sideRails(GameTestHelper helper, int length, int top) {
        for (int x = -1; x <= length + 1; x++) {
            for (int y = DECK + 1; y <= top; y++) {
                helper.setBlock(new BlockPos(x, y, -1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }
    }

    /** 自由模式的女仆，站在给定的绝对坐标上。 */
    static EntityMaid maidAt(GameTestHelper helper, double x, double y,
            double z) {
        EntityMaid maid = new EntityMaid(helper.getLevel());
        maid.setPos(x, y, z);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        // 入世之后再设差事，与姊妹夹具 stayOnYourFeet 一致。（查过宿主：
        // setTask 在 level 是 ServerLevel 时就 refreshBrain，先设并不会坏，
        // 所以这只是统一，不是修复——别把它当病因写进任何结论。）
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        return maid;
    }

    /**
     * 把这一片区块钉住（**只钉不松**：setChunkForced 无引用计数，先完成的
     * 一松邻居的地就塌；测试世界一次性，钉着无代价）。
     *
     * <p>GameTest 只强加载**结构自己那点范围**，而场景写方块远超模板边界。
     * 她一走出加载区就**不再被 tick**：执行器停摆在最后一帧、速度恒 0.00，
     * 而现场复铺照样给出可达路（测试线程不受影响）。实测坐实：附加脑出勤
     * 在存续期内 100% 不漏，红的那次 135 tick 就断（测试要 940），冻住位置
     * 全在刚出圈墙一格处——与那支箭同一条教训：加载区外的实体不被 tick。
     */
    private static void holdTheGround(GameTestHelper helper, boolean hold) {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        int chunkX = SectionPos.blockToSectionCoord(zero.getX());
        int chunkZ = SectionPos.blockToSectionCoord(zero.getZ());
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                helper.getLevel().setChunkForced(
                        chunkX + dx, chunkZ + dz, hold);
            }
        }
    }

    /** 读数带的一行：相对模板原点的坐标。 */
    static void tapeRow(GameTestHelper helper, StringBuilder tape, int at,
            EntityMaid maid) {
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        tape.append(String.format("%d:(%.1f,%.1f,%.1f) ", at,
                maid.getX() - zero.getX(),
                maid.getY() - zero.getY(),
                maid.getZ() - zero.getZ()));
    }

    /** 把走目标写到她脑子里——测试里唯一的驱动手段。 */
    static void sendTo(GameTestHelper helper, EntityMaid maid, BlockPos goal) {
        maid.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(
                        helper.absolutePos(goal)), 0.7F, 0)
        );
    }

    /** 她离这个路标还有多远（水平）。 */
    static double flatTo(GameTestHelper helper, EntityMaid maid, BlockPos goal) {
        BlockPos at = helper.absolutePos(goal);
        return Math.hypot(maid.getX() - at.getX() - 0.5D,
                maid.getZ() - at.getZ() - 0.5D);
    }

    /** 到站半径。跟随自己写着 close_distance:2——主人小跑靠近的那半程她就
     *  算追上、走目标当场清掉，于是她稳定停在离站点两格上下（实测 7.0 对
     *  8.5，五条同时红）。判据比这还紧，腿就永远不计数：量错，不是没走到。 */
    private static final double ARRIVED_NEAR = 3.0D;

    /**
     * 奔跑跟随的往返：主人跑到一头，她用**自己的跟随意图**追；她到了，他
     * 再跑回另一头。
     *
     * <p>与喂走目标的驱动是两回事，而实机的摔全发生在这一种里：跟随的锚点
     * 是个会动的人，路标每 tick 都在变，掉头也不是测试喊的、是她自己在终点
     * 重新决定的——玩家点名要这种开法。
     *
     * @param stationA 她先去的那一站（主人起始位置，也是第一条腿的终点）
     * @param fallLine 绝对 y；低于它就是摔下去了，当场定罪
     * @return 跑完的腿数（调用方判够不够）
     */
    static int[] followPatrol(
            GameTestHelper helper,
            Vec3 spawn,
            Vec3 stationA,
            Vec3 stationB,
            int legsWanted,
            double fallLine,
            int driveTicks,
            String traceName
    ) {
        holdTheGround(helper, true);
        Player owner = helper.makeMockPlayer();
        owner.setPos(stationA.x, stationA.y, stationA.z);
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(spawn.x, spawn.y, spawn.z);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
        // 入世之后再设差事，理由见 maidAt。
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        double ceiling = Math.max(spawn.y,
                Math.max(stationA.y, stationB.y)) + 4.0D;
        PathwalkTrace trace = new PathwalkTrace(traceName, zero);
        PathFilm film = new PathFilm(helper, maid, owner);
        int[] legs = new int[]{0};
        boolean[] done = new boolean[]{false};

        for (int tick = 1; tick <= driveTicks; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                if (done[0]) {
                    return;
                }
                // 主人一分钟从她身上碾过去几百次，偶尔会把她带上身——骑乘
                // 状态下导航不是我们的、脚永远不着地，到站判据从此不成立，
                // 她就悬在他头顶上方一动不动（读数带实测两百 tick，执行器
                // 一个 tick 都没转）。真人不会这么走路：当场请她下来。
                if (maid.isPassenger()) {
                    maid.stopRiding();
                }
                trace.sample(at, maid);
                film.roll(at, trace);
                // 天花板与摔线一样要紧。她被连跳顶上天的时候，下界断言一个
                // 字都不会说——实测见过 y=25.49（地面在 9），而供词只报"少
                // 跑了几条腿"。玩家实机也是这个样子：人打着转飘在云层里。
                // 站点之上四格够任何一次正常起跳（满弧一格二五）加一次登阶。
                if (maid.getY() > ceiling) {
                    done[0] = true;
                    film.spill(trace);
                    trace.dump();
                    String why = diaryOf(maid);
                    double upX = maid.getX() - zero.getX();
                    double upZ = maid.getZ() - zero.getZ();
                    double upY = maid.getY() - zero.getY();
                    maid.discard();
                    owner.discard();
                    helper.fail(String.format(
                            "她被顶上天了：t=%d rel=(%.2f,%.2f,%.2f)，"
                                    + "天花板 rel y=%.2f；%s",
                            at, upX, upY, upZ,
                            ceiling - zero.getY(), why));
                    return;
                }
                if (maid.getY() < fallLine) {
                    done[0] = true;
                    film.spill(trace);
                    trace.dump();
                    String diary = diaryOf(maid) + "; " + IntentWitness.of(maid);
                    double relX = maid.getX() - zero.getX();
                    double relZ = maid.getZ() - zero.getZ();
                    maid.discard();
                    owner.discard();
                    helper.fail(String.format(
                            "Leg %d dropped her at t=%d rel=(%.2f,%.2f,%.2f);"
                                    + " %s",
                            legs[0] + 1, at, relX,
                            maid.getY() - zero.getY(), relZ, diary));
                    return;
                }
                Vec3 station = legs[0] % 2 == 0 ? stationA : stationB;
                LeadRunner.runToward(owner, station,
                        legs[0] % 2 == 0 ? stationB : stationA, at);
                boolean arrived = maid.onGround() && Math.hypot(
                        maid.getX() - station.x,
                        maid.getZ() - station.z) < ARRIVED_NEAR;
                if (!arrived) {
                    return;
                }
                legs[0]++;
                if (legs[0] >= legsWanted) {
                    done[0] = true;
                    trace.dump();
                    // 过了也印。附加脑的出勤率是"她到底有没有人管"的底数，
                    // 而它只在卡住时才印，就等于把证据押在"这一轮恰好出事"
                    // 上——今晚已经在崖下探针上栽过同一跤，四轮全绿就一个读
                    // 数都取不到。绿的时候这一行同样有话说：出勤 100% 才叫
                    // 正常，14% 那种就算这一趟侥幸走完也是病。
                    System.out.println("=== patrol " + traceName
                            + " ===\n  " + IntentWitness.of(maid));
                    maid.discard();
                    owner.discard();
                    helper.succeed();
                }
            });
        }

        helper.runAfterDelay(driveTicks + 40, () -> {
            if (done[0]) {
                return;
            }
            done[0] = true;
            film.spill(trace);
            trace.dump();
            // 现场复铺：她冻住的这一刻，拿同样的目标再要一条路。
            //
            // 冻住的三例指纹相同——路是"一节点、终点就是她自己、reach=NO"，
            // 而单独跑规划器时同一格每次都铺得出完整可达路。两者必有一个不
            // 是我以为的样子，可事后回放分不开：读数带记的是**结果**，不是
            // 这次请求问的是什么。就地再问一次，答案当场把范围劈成两半——
            // 复铺是好路，病在"实机那条请求"（目标、精度、谁在问）；复铺也
            // 是残桩，病在她进入的那个状态让这一格发不出邻居。
            // 先把旧路清掉再问，否则问了个寂寞：原版 createPath 开头有一句
            // 「当前这条路还没走完、而且目标没变，就把它原样还给你」——而我
            // 问的正是同一个目标，于是拿回来的是**她手里那根残桩**，不是新
            // 铺的路。据此我曾断定"从这一格铺不出路"，可同一格在隔离测试里
            // 铺得出二十节点的可达路——那不是她的病，是这句问话没问到。
            maid.getNavigation().stop();
            Path onTheSpot = maid.getNavigation()
                    .createPath(owner.blockPosition(), 0);
            String replan = "现场复铺=" + PathwalkTrace.describe(onTheSpot, zero);
            String diary = diaryOf(maid) + "; " + IntentWitness.of(maid)
                    + "; 起点邻居=" + (maid.getNavigation() instanceof SureFootedNavigation sure ? sure.startNeighbours() : -1) + "; " + replan;
            double relX = maid.getX() - zero.getX();
            double relZ = maid.getZ() - zero.getZ();
            maid.discard();
            owner.discard();
            helper.fail(String.format(
                    "Only %d/%d legs — she stalled at rel=(%.2f,%.2f,%.2f); %s",
                    legs[0], legsWanted, relX,
                    maid.getY() - zero.getY(), relZ, diary));
        });
        return legs;
    }

    /**
     * 主人站在她**够不着**的地方（结构外的半空、对岸），判据只有一条：
     * 别摔下去。
     *
     * <p>够不着的时候用"到站"判成败是错的——她永远到不了，那种红说明不了
     * 任何事。而实机里最险的处境恰恰是这一种：主人在结构外，她贴到最近的
     * 落脚点上进退两难，一步迈错就下去了。
     */
    static void stayOnYourFeet(
            GameTestHelper helper,
            Vec3 spawn,
            Vec3 ownerAt,
            double fallLine,
            int driveTicks,
            String traceName
    ) {
        holdTheGround(helper, true);
        Player owner = helper.makeMockPlayer();
        owner.setPos(ownerAt.x, ownerAt.y, ownerAt.z);
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(spawn.x, spawn.y, spawn.z);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        helper.getLevel().addFreshEntity(maid);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());

        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        PathwalkTrace trace = new PathwalkTrace(traceName, zero);
        double[] lowest = new double[]{maid.getY()};
        boolean[] done = new boolean[]{false};

        for (int tick = 1; tick <= driveTicks; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                if (done[0]) {
                    return;
                }
                trace.sample(at, maid);
                lowest[0] = Math.min(lowest[0], maid.getY());
                // 主人脚不停，"他上路了"才立得住；但不离开原地那一小块。
                owner.setPos(ownerAt.x + Math.sin(at * 0.25D) * 0.6D,
                        ownerAt.y, ownerAt.z);
                maid.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new EntityTracker(owner, false),
                                0.75F, 0)
                );
                if (maid.getY() < fallLine) {
                    done[0] = true;
                    trace.dump();
                    String diary = diaryOf(maid) + "; " + IntentWitness.of(maid);
                    maid.discard();
                    owner.discard();
                    helper.fail(String.format(
                            "t=%d 她掉下去了，rel=(%.2f,%.2f,%.2f)；%s",
                            at, maid.getX() - zero.getX(),
                            maid.getY() - zero.getY(),
                            maid.getZ() - zero.getZ(), diary));
                }
            });
        }

        helper.runAfterDelay(driveTicks + 20, () -> {
            if (done[0]) {
                return;
            }
            trace.dump();
            maid.discard();
            owner.discard();
            helper.succeed();
        });
    }

    /**
     * 直桥往返：随机起点，左右两端之间跑满观察窗，计趟数；摔一次就是红。
     *
     * <p>起点随机是要害——每一趟的 tick 相位都不一样，起跳点差半格落点就
     * 从格心挪到沿上，边际时序问题跑几趟自己现形。
     */
    static void roundTrip(
            GameTestHelper helper,
            int spawnFeetY,
            int walkFeetY
    ) {
        double offX = 0.4D
                + helper.getLevel().getRandom().nextDouble() * 1.6D;
        double offZ = 1.2D
                + helper.getLevel().getRandom().nextDouble() * 0.6D;
        BlockPos base = helper.absolutePos(new BlockPos(0, spawnFeetY, 0));
        EntityMaid maid = maidAt(helper, base.getX() + offX, base.getY(),
                base.getZ() + offZ);

        double deckFeet = base.getY();
        double[] lowest = new double[]{maid.getY()};
        boolean[] headingRight = new boolean[]{true};
        int[] legs = new int[]{0};
        StringBuilder tape = new StringBuilder();
        StallWatch stall = new StallWatch(maid);
        // 摔下去那一刻的供词。坐标带说得出"她掉了"，说不出"她当时在走哪
        // 一段"——而摔因永远是后者（抬高石第三趟从东沿滑落，光看坐标分不
        // 清是跳过头、没起跳、还是被安全网刹停）。
        String[] fell = new String[]{null};

        for (int tick = 1; tick <= WATCHED_TICKS; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                lowest[0] = Math.min(lowest[0], maid.getY());
                double relX = maid.getX()
                        - helper.absolutePos(BlockPos.ZERO).getX();
                if (at % 5 == 0 && tape.length() < 900) {
                    tapeRow(helper, tape, at, maid);
                }
                if (maid.getY() < deckFeet - 1.5D) {
                    if (fell[0] == null) {
                        fell[0] = "fell at t=" + at + " x="
                                + String.format("%.2f", relX) + " "
                                + diaryOf(maid);
                    }
                    return;
                }
                stall.sample(at);
                if (headingRight[0] && relX >= 8.3D) {
                    headingRight[0] = false;
                    legs[0]++;
                } else if (!headingRight[0] && relX <= 1.2D) {
                    headingRight[0] = true;
                    legs[0]++;
                }
                sendTo(helper, maid, new BlockPos(
                        headingRight[0] ? 9 : 0, walkFeetY, 1));
            });
        }

        helper.runAfterDelay(WATCHED_TICKS, () -> {
            helper.assertTrue(
                    lowest[0] > deckFeet - 1.5D,
                    "She fell during the round trips (legs done "
                            + legs[0] + ", spawn offX=" + String.format(
                                    "%.2f", offX)
                            + "): lowest y " + lowest[0]
                            + "; " + fell[0]
                            + "; tape(rel)=" + tape
            );
            helper.assertTrue(
                    legs[0] >= 3,
                    "She only finished " + legs[0]
                            + " legs — stuck somewhere; " + diaryOf(maid)
                            + "; tape(rel)=" + tape
            );
            helper.assertTrue(
                    stall.longest() <= StallWatch.TOLERATED_TICKS,
                    "She spun in place for " + stall.longest()
                            + " ticks (legs " + legs[0] + "); " + diaryOf(maid)
                            + "; tape(rel)=" + tape
            );
            maid.discard();
            helper.succeed();
        });
    }
}
