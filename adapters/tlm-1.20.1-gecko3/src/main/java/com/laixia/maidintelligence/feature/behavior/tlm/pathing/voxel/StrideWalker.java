package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

import com.laixia.maidintelligence.feature.behavior.tlm.pathing.LeapFlight;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.sweep
        .LeapContract;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * 自有执行器：照着 {@link VoxelPath} 的合同走，一步不猜。
 *
 * <p>旧执行器的十几个段判据都是在**反猜**格差背后的动作，猜错一步就是
 * 摔；自有路径的每一步带着动作类型与初速合同（图侧已拿扫掠仿真按这股初
 * 速验收过），这里照单执行。驱动也自管：朝向与速度直接写实体，不喂原版
 * 移动控制——它的贴身转向噪声（rotlerp 对病态方向角）是原地旋转的根，
 * 死区补丁就此退役。
 *
 * <p>判到对**锚点**收，公差由锚点的支撑窄度给：满面 0.35、横杆 0.175，
 * 转弯永远发生在支撑的交点上——四十五度切进虚空的斜线在口径上就不存在。
 */
public final class StrideWalker {
    /** 地面走速（每 tick 位移），随边合同的步速档缩放。 */
    private static final double WALK_SPEED = 0.16D;

    /** 每 tick 最多拧多少度朝向——够灵活，不够甩尾。 */
    private static final float TURN_RATE = 30.0F;

    /** 迈步前往前看多少 tick：转过九十度约要六 tick，看到转弯之后。 */
    private static final int LOOKAHEAD_TICKS = 8;

    private final Mob mob;
    private final LeapFlight flight;
    private final EdgeVeto veto;
    private final CrowdSteer crowd;

    private VoxelPath path;

    /** 行车记录：最后一步的动作名，黑匣子与读数带用。 */
    private String note = "-";

    /** 回锚开始的 tick；-1 = 不在回锚。回锚也要有闹钟。 */
    private int regroupSince = -1;

    public StrideWalker(Mob mob, LeapFlight flight, EdgeVeto veto) {
        this.mob = mob;
        this.flight = flight;
        this.veto = veto;
        this.crowd = new CrowdSteer(mob);
    }

    /** 换一条路（null = 清路）。同一单（下一站没变）保留组织计时——
     *  重下单是调度的事，她这一跳的酝酿不该跟着清零。 */
    public void follow(VoxelPath fresh) {
        boolean sameLeg = fresh != null && path != null
                && path.alive() && fresh.alive()
                && fresh.next().at().distanceToSqr(path.next().at())
                        < 0.01D;
        this.path = fresh;
        if (!sameLeg) {
            this.regroupSince = -1;
        }
    }

    public VoxelPath path() {
        return path;
    }

    public String note() {
        return note;
    }

    /**
     * 每 tick 一步。返回 false 表示没有活路可走（调用方转别的处置）。
     */
    public boolean run() {
        if (flight.locked()) {
            flight.steer();
            note = "flight";
            return true;
        }
        if (path == null || !path.alive()) {
            return false;
        }
        if (!mob.onGround() && !mob.isInWater()) {
            note = "airborne";
            return true;
        }
        Anchor next = path.next();
        double toX = next.at().x - mob.getX();
        double toZ = next.at().z - mob.getZ();
        double flat = Math.hypot(toX, toZ);
        double rise = next.at().y - mob.getY();
        // **路径要贴着现实**：摔落、被击退之后，旧路径的锚可能悬在头顶
        // 五格——照单执行就是对着墙反复起跳（实测一轮一千七百跳、跑到超
        // 时）。锚够不着（高差过大或水平走丢）就作废，等下一次下单按新
        // 位置重铺。
        // 作废的判据是**离这条腿多远**，不是"下一站多远"：任意角的一
        // 条腿本来就能有七八格长（这正是 Theta* 的收益），按到站距离判
        // 就是每接到一条长直线立刻自我作废、原地打转（栅栏圈实测 stale
        // f7.4）。她被击退、摔落之后偏离的是**线**，量线才量得对。
        double offLeg = segmentDist(path.from().at(), next.at(),
                mob.getX(), mob.getZ());
        if (rise > 1.5D || rise < -7.0D || offLeg > 3.0D) {
            path = null;
            note = String.format("stale r%.1f o%.1f", rise, offLeg);
            return false;
        }
        // 判到：踏上支撑面就算到——面是"能站的地方"的精确集合，比对
        // 点收圆合理（柱尖的平衡位、贴边的台面从此没有"够不到锚点"）。
        // 点距口径保留作补充（面极小时两者等价）。
        double tolerance = Math.min(0.35D,
                Math.min(path.from().breadth(), next.breadth()) / 2.0D
                        + 0.05D);
        // 边距按**脚掌**给（半脚 0.3 留 0.05 余量）：判到问的是"站没站
        // 住"，脚掌搭上支撑就站住了——烛杆顶 0.125 的条按 0.1 边距收，
        // 判到圈比物理可站还小，落点偏一拳就永远判不到（末地烛中继实测
        // 十副本连摔）。
        //
        // **终点比路点严**：路过一站只要脚尖点到就该继续走，不打断行
        // 进；而终点若也按脚尖算，她会停在目标格外沿宣布到达——看着就
        // 是"差一步不走了"，重铺又立刻再说一次到达（抬高石往返实测：停
        // 在 rel 1.2 整整一百九十 tick，供词 orders 一直在涨）。
        // 终点的严只对**正经面**：满面上走到中心是举手之劳，收紧她才不
        // 会停在格外沿；窄面（杆顶、柱尖）物理上就居不了中，同样收紧她
        // 永远判不到、只好继续挪——挪出杆外就是摔（末地烛中继实测十副
        // 本连摔）。
        // 边距**按面宽给**：满面上脚尖点到即可放行（不打断行进），杆
        // 条上就得站得更实——0.25 的边距等于允许她在离杆心 0.35 处宣布
        // 到站，转角上人还没站上就拐弯，一步出杆（横烛桥转角实测三红全
        // 是摔）。终点另收一档紧的。
        boolean lastLeg = path.cursor() == path.length() - 1;
        double standMargin = lastLeg && next.breadth() >= 0.5D
                ? 0.10D
                : Math.min(0.25D, Math.max(0.10D, next.breadth() / 2.0D));
        // **要拐弯就得真站上去**：脚尖搭着外沿就宣布到站，然后开始转
        // 向——三 tick 转九十度、横漂 0.25，与站位偏差叠起来人已在杆
        // 外（横烛桥转角实测三红全是这一下）。抄路点只配直行；一拐弯，
        // 判到就收成点距（那才是"站上去了"）。
        boolean turning = turnAt() >= 15.0D;
        boolean onStand = !turning
                && next.standDist(mob.getX(), mob.getZ()) <= standMargin
                && Math.abs(rise) <= 0.5D;
        if ((flat <= tolerance || onStand) && Math.abs(rise) <= 1.0D) {
            // 这条边实机走通了：账本上的前科（若有）一笔勾销。
            veto.absolve(path.from().cell(), next.cell());
            path.advance();
            if (!path.alive()) {
                note = "done";
                halt();
                return true;
            }
            next = path.next();
            toX = next.at().x - mob.getX();
            toZ = next.at().z - mob.getZ();
            flat = Math.hypot(toX, toZ);
        }
        Stride stride = path.stride();
        note = stride.move().name().toLowerCase();
        switch (stride.move()) {
            case WALK -> walk(toX, toZ, flat);
            case CLIMB, LEAP -> approachAndLeap(next, stride, toX, toZ, flat);
            case DROP -> stepOff(next, stride, toX, toZ, flat);
            case SQUEEZE, CORNER -> walk(toX, toZ, flat);
        }
        return true;
    }

    /** 当前要去的锚；没有活路时为 null。 */
    private Anchor next() {
        return path != null && path.alive() ? path.next() : null;
    }

    /** 下一站要拐多少度（没有下一段就是零）。 */
    private double turnAt() {
        if (path == null || !path.alive()) {
            return 0.0D;
        }
        int i = path.cursor();
        if (i + 1 >= path.length()
                || path.strideAt(i).move() != Stride.Move.WALK) {
            return 0.0D;
        }
        Anchor here = path.from();
        Anchor corner = path.anchorAt(i);
        Anchor after = path.anchorAt(i + 1);
        double ax = corner.at().x - here.at().x;
        double az = corner.at().z - here.at().z;
        double bx = after.at().x - corner.at().x;
        double bz = after.at().z - corner.at().z;
        double la = Math.hypot(ax, az);
        double lb = Math.hypot(bx, bz);
        if (la < 1.0E-4D || lb < 1.0E-4D) {
            return 0.0D;
        }
        double cos = (ax * bx + az * bz) / (la * lb);
        return Math.toDegrees(Math.acos(
                Math.max(-1.0D, Math.min(1.0D, cos))));
    }

    /**
     * 过弯限速，**从她自己的转向动力学解出来**：每 tick 只拧得动
     * {@value #TURN_RATE} 度，转 θ 度就要 θ/30 tick，这段时间她会沿老
     * 方向横漂 v×θ/30——这段漂移必须塞得进转角处的支撑宽度里。
     *
     * <p>0.25 宽的杆条上转九十度：0.25÷3 = 0.083（先前手调的"窄面慢步
     * 0.08"，现在是推导出来的）；满块上转九十度：1.0÷3 = 0.33，高过常
     * 速，自然不限速。地形不必再一种一个常数——同一条式子回答所有形状。
     *
     * <p>只在**快到路点时**收（一格以内）：远处的弯还轮不到她操心。
     */
    private double cornerLimit(double flat) {
        if (flat > 1.0D || path == null || !path.alive()) {
            return Double.MAX_VALUE;
        }
        double turn = turnAt();
        if (turn < 15.0D) {
            return Double.MAX_VALUE;
        }
        int i = path.cursor();
        double ticks = Math.max(1.0D, turn / TURN_RATE);
        double room = Math.min(path.anchorAt(i).breadth(),
                path.anchorAt(i + 1).breadth());
        return room / ticks;
    }

    /**
     * 这一步**容得下多深的下落**：走到下一站该有的高差，加半格宽容。
     * 台阶、贴脚小落差本就是走边的一部分，预演不该把它们当摔。
     */
    private double allowedDrop() {
        double lowest = mob.getY();
        for (int i = path.cursor(); i < path.length(); i++) {
            lowest = Math.min(lowest, path.anchorAt(i).at().y);
        }
        return Math.max(0.0D, mob.getY() - lowest) + 0.6D;
    }

    /** 直驱走：自己拧朝向、自己写水平速度，移动控制靠边站。 */
    private void walk(double toX, double toZ, double flat) {
        if (flat < 1.0E-4D) {
            halt();
            return;
        }
        faceToward(toX, toZ);
        // **先预演再迈步**：按这股速度往前推几 tick，问物理"这么走会不
        // 会真的掉下去"（{@link SweptMotion#dropAhead} 与图侧验跳边同一
        // 个内核）。会掉就换慢一档再问，档档都掉就刹住——由物理挑步速，
        // 不由我按地形写常数（玩家点破："总是在调参，这样很难说适应不
        // 明场景"）。杆桥转角上她自然会慢下来，因为快了真的会掉。
        double want = Math.min(Math.min(WALK_SPEED, flat * 0.5D),
                cornerLimit(flat));
        double allowed = allowedDrop();
        double speed = 0.0D;
        for (double trial : new double[]{want, want * 0.5D, want * 0.25D}) {
            if (trial < 1.0E-4D) {
                continue;
            }
            double drop = com.laixia.maidintelligence.feature.behavior.tlm
                    .pathing.sweep.SweptMotion.dropAhead(mob.level(),
                            mob.position(), toX / flat * trial,
                            toZ / flat * trial, mob.getBbWidth(),
                            mob.getBbHeight(), LOOKAHEAD_TICKS);
            speed = trial;
            if (drop <= allowed) {
                break;
            }
        }
        // **危险不等于停步**：三档都判危时走最慢那档，不是钉在原地。
        // 预演问的是"照这个速度直着走下去会不会掉"——在沿口附近它当然
        // 说会，那正是该慢下来的信号，不是该停下的信号（图已经担保这条
        // 腿走得通）。停过一轮：六类老红当场绿，杆桥却寸步难行（一百九
        // 十次刹停、走不到对岸）。慢行两全。
        if (speed <= 0.0D) {
            halt();
            note = "brake";
            return;
        }
        double vx = toX / flat * speed;
        double vz = toZ / flat * speed;
        // 动态避障（拥挤转向）：期望速度交给 ORCA 轻量版按人群修正——
        // 玩家会养几十只，避障按人群设计。两条纪律：窄面上不避（窄桥上
        // 被挤着侧移就是坠崖，宁可顶住等）；避让不得把她推出支撑（修正
        // 后的预测落点无支撑就回退原速，崖沿排队不互相挤下去）。
        if (path != null && path.alive()
                && path.from().breadth() >= 0.5D) {
            double[] steered = crowd.steer(vx, vz);
            if (steered[0] != vx || steered[1] != vz) {
                var footing = AnchorResolver.resolve(mob.level(),
                        net.minecraft.core.BlockPos.containing(
                                mob.getX() + steered[0] * 2.0D,
                                mob.getY() + 0.1D,
                                mob.getZ() + steered[1] * 2.0D));
                if (footing != null) {
                    vx = steered[0];
                    vz = steered[1];
                }
            }
        }
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(vx, motion.y, vz);
    }

    /** 跳与登：走到沿口，按合同初速起跳，落点锁交给弧线合同。 */
    private void approachAndLeap(Anchor next, Stride stride, double toX,
            double toZ, double flat) {
        // 登一格（CLIMB）在**窗口带**起跳，不贴脸：贴到半格再跳，弧还没
        // 升过立面就撞上弹回，人就在台阶前起落循环（凸石台实测四红全是
        // 它）。窗口一格一出头，弧顶恰好越沿。
        if (stride.move() == Stride.Move.CLIMB) {
            // 升差已消就不再跳：落上台面后落点稍偏、还没进判到圈时，无
            // 条件重跳会把她弹成台顶的皮球（凸石台实测：y 在走面与台顶
            // 之间波动整场、一次被弹出台摔）。已同层的收尾交给走。
            if (next.at().y - mob.getY() < 0.5D) {
                walk(toX, toZ, flat);
                return;
            }
            if (flat > 1.15D) {
                walk(toX, toZ, flat);
                return;
            }
            double speed = LeapContract.launchSpeed(flat, 1);
            mob.setDeltaMovement(
                    toX / flat * speed, LeapContract.JUMP_RISE,
                    toZ / flat * speed);
            flight.lock(next.at(), speed, toX / flat, toZ / flat);
            veto.strike(path.from().cell(), next.cell());
            note = "takeoff";
            System.out.println("[voxel-takeoff] climb from="
                    + mob.position() + " to=" + next.at()
                    + " v=" + String.format("%.2f", speed));
            return;
        }
        // 跳（LEAP）的组织全部对**支撑面**：出了面先回面；在面上但离沿
        // 口远就沿跳向贴到前沿（面内走的是支撑，无掉落之虞——从大平台
        // 后沿起跳"实距超合同、封顶下必短"的病根就此拔掉）；贴沿即跳，
        // 初速按实距重解。点口径年代的两代回锚界（0.6 圆、方向拆分的
        // lag 门）随格语法一起退役："她物理上站不到锚点"（柱尖平衡位、
        // 碗壁顶着）在面口径下无病可生。全程一只闹钟：三秒还没组织起这
        // 一跳，作废路径重铺——被账本拉黑的边此时自然换路。
        if (regroupSince < 0) {
            regroupSince = mob.tickCount;
        }
        if (mob.tickCount - regroupSince > 60) {
            regroupSince = -1;
            path = null;
            note = "regroup-quit";
            return;
        }
        // **向前贴沿不算走丢**：回锚看的是"离出发面多远"，可贴沿本来
        // 就是要她往沿口挪——两条规则于是互相拉扯（实测流水：regroup
        // b0.43 → approach t0.40 → regroup b0.43 → …，每次起跳前都要
        // 空耗几轮，面再窄一点就成了原地不动）。只有**逆着跳向**或横向
        // 偏出去才算走丢；顺着跳向的位移交给贴沿管。
        // 贴沿预走只配**常规面**：窄面（烛顶、杆条）上前挪是自杀——直
        // 驱带着横向漂移，0.125 的条上走两步就滑出侧沿（末地烛中继实测
        // 十副本全摔）。窄面的合同本就按锚心计价、仿真按锚心验收，站着
        // 就跳。
        double alongX = next.at().x - path.from().at().x;
        double alongZ = next.at().z - path.from().at().z;
        double alongLen = Math.hypot(alongX, alongZ);
        if (path.from().breadth() >= 0.5D && alongLen > 1.0E-4D) {
            double ax = alongX / alongLen;
            double az = alongZ / alongLen;
            // 沿口要**探**，不能只看脚下这一格的面：支撑面是逐方块的，
            // 站在三格长台的中间那格上，那格的面一格就到头，她会以为自
            // 己已在沿口——于是从台中央起跳，跨度比图定价时多出一格，推
            // 力封顶下必然短（末地烛中继实测：全场唯一一次起跳 span 2.68
            // 、v 已封顶 0.50，摔进缺口）。沿着跳向一路问支撑到哪为止，
            // 那才是真沿。
            double toBrink = 0.0D;
            for (double d = 0.4D; d <= 3.0D; d += 0.4D) {
                Anchor ahead = AnchorResolver.resolve(mob.level(),
                        net.minecraft.core.BlockPos.containing(
                                mob.getX() + ax * d, mob.getY() + 0.1D,
                                mob.getZ() + az * d));
                if (ahead == null || ahead.breadth() < 0.4D
                        || Math.abs(ahead.at().y - mob.getY()) > 0.3D) {
                    break;
                }
                toBrink = d;
            }
            // 贴沿只给一秒机会窗：走不进沿（前沿贴障、薄面滑步）就按现
            // 在站的地方跳——实距重解给速，短了有账本记边换路，总比在
            // 面上无限踱步烧掉整场强（tee 批实测：七场齐卡 approach）。
            // 探到上限还没到沿：前方支撑一路延伸，这一跳的沿口不在这个
            // 方向上（多半是路径已经换了腿）——别贴了，按现在站的地方
            // 起跳，实距重解给速。贴到底才跳会把她推着走出老远（活板门
            // 立门案实测：note 常驻 approach t3.00，人在原地来回）。
            if (toBrink > 0.25D && toBrink < 3.0D
                    && mob.tickCount - regroupSince <= 20) {
                walk(ax * toBrink, az * toBrink, toBrink);
                note = String.format("approach t%.2f", toBrink);
                return;
            }
        }
        // 走丢的判定**排在贴沿之后**：两者曾用互不相干的尺子打架——贴
        // 沿看"前方支撑还有多远"（长平台上她本就该走出两三格），回锚看
        // "离出发那一格的面多远"（0.35），她一走出那格就被拽回来，于是
        // approach ↔ regroup 摆上几十秒（立门板案实测）。反过来，用"顺
        // 跳向不算走丢"去绕，真沿口也不拽她了，她径直走出台沿摔下去（细
        // 柱案九红）。次序才是答案：先把沿贴完，还偏着才是真偏了。
        // 走丢＝离出发那一格的支撑面太远。
        //
        // 换过三种参照物，都更差：按"脚下有没有支撑"，踩着杆条边沿也算
        // 有，她从偏心位起跳、跨度短一截（细柱七红）；再加"窄面拢心"仍
        // 是八红；改按"离这条腿的走廊多远"更糟，十红。原版这把尺子在细
        // 柱案上一直是零到一红——它虽然在长平台上会和贴沿争几轮（摆动
        // 的来处，靠次序压住：贴沿先行，贴完才判），但**跳得准**是更贵
        // 的东西。数据面前不硬扭。
        double backDist = path.from().standDist(mob.getX(), mob.getZ());
        if (backDist > 0.35D) {
            double backX = path.from().at().x - mob.getX();
            double backZ = path.from().at().z - mob.getZ();
            walk(backX, backZ, Math.hypot(backX, backZ));
            note = String.format("regroup b%.2f", backDist);
            return;
        }
        regroupSince = -1;
        // 初速按**实际站位**重解：launchSpeed 与图侧、仿真同一条弧线方
        // 程，代入实距等比配平——贴沿起跳的实距比锚心距短，重解自动收
        // 速防过冲；窄面上的站位偏差同样由它找补。
        int band = (int) Math.round(next.at().y - path.from().at().y);
        double speed = LeapContract.launchSpeed(flat, band);
        mob.setDeltaMovement(
                toX / flat * speed, LeapContract.JUMP_RISE,
                toZ / flat * speed);
        flight.lock(next.at(), speed, toX / flat, toZ / flat);
        veto.strike(path.from().cell(), next.cell());
        note = "takeoff";
        System.out.println("[voxel-takeoff] leap from="
                + mob.position() + " to=" + next.at()
                + " v=" + String.format("%.2f", speed));
    }

    /** 下崖：小步迈出，锁落点，重力接管。 */
    private void stepOff(Anchor next, Stride stride, double toX, double toZ,
            double flat) {
        double push = stride.speed();
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(
                toX / flat * push, motion.y, toZ / flat * push);
        flight.lock(next.at(), push, toX / flat, toZ / flat);
        note = "stepoff";
    }

    /** 点到线段的水平距离：她偏离这条腿多远。 */
    private static double segmentDist(Vec3 a, Vec3 b, double px, double pz) {
        double abx = b.x - a.x;
        double abz = b.z - a.z;
        double len2 = abx * abx + abz * abz;
        if (len2 < 1.0E-8D) {
            return Math.hypot(px - a.x, pz - a.z);
        }
        double t = ((px - a.x) * abx + (pz - a.z) * abz) / len2;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return Math.hypot(px - (a.x + abx * t), pz - (a.z + abz * t));
    }

    /** 站定：清水平残速，一步不写就是静止（原版移动控制无人喂自回落）。 */
    private void halt() {
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(0.0D, motion.y, 0.0D);
    }

    /** 平滑拧向行进方向。 */
    private void faceToward(double toX, double toZ) {
        float wanted = (float) (Mth.atan2(toZ, toX) * (180.0D / Math.PI))
                - 90.0F;
        mob.setYRot(Mth.approachDegrees(mob.getYRot(), wanted, TURN_RATE));
        mob.yBodyRot = mob.getYRot();
    }
}
