package com.laixia.maidintelligence.feature.behavior.tlm.pathing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

/**
 * 窄处怎么站住、怎么挤过去——车道纪律。
 *
 * <p>从 {@code Leaper} 按职责拆出（单文件五百行的布局纪律）：那边回答
 * "这一段腾空怎么腾"，这里只回答"路窄到只剩一条线时，脚该踩在哪"。两件
 * 事共用同一把尺（{@code FootingRule}），但触发的时机与手法都不同：跳是
 * 一次性的冲量，车道是每 tick 的贴线。
 *
 * <p>手法统一：**把移动控制停在原地，再直写一股自阻尼的侧向速度**。移动
 * 控制是朝她的朝向推的，没有到位刹车——带着走路动量急转侧向，半格内必过
 * 冲出道外（一格宽道实测冲到 2.3 坠落）。
 */
final class LaneWork {
    private final Mob mob;
    private final SureFootedNavigation nav;
    private final LeapFlight flight;

    LaneWork(Mob mob, SureFootedNavigation nav, LeapFlight flight) {
        this.mob = mob;
        this.nav = nav;
        this.flight = flight;
    }


    /**
     * 贴边段（车道保持）：被占格两旁的窄带是**车道**——入口、柱旁、出口
     * 三点全塞得下身位才算（{@code FootingRule.squeezeLane}）。过柱期间
     * 每 tick 沿行进轴推进半格多、侧向锁死在车道坐标上，过完由调用方交
     * 还格心线。单航点折线绕不过居中柱：正面窄带能站但下一步抵在柱面，
     * 滑挤会跳成九十 tick 的死舞（读数带实测）。
     *
     * @return true 已接管这一 tick 的移动；false 没有车道，调用方另想。
     */
    boolean holdTheLane(BlockPos occupied, int dx, int dz) {
        boolean alongX = Math.abs(dx) >= Math.abs(dz);
        double lane = FootingRule.squeezeLane(
                mob.level(), occupied, alongX,
                alongX ? mob.getZ() : mob.getX());
        if (Double.isNaN(lane)) {
            return false;
        }
        double forward = 0.6D;
        double px = alongX
                ? mob.getX() + Math.signum(dx) * forward
                : lane;
        double pz = alongX
                ? lane
                : mob.getZ() + Math.signum(dz) * forward;
        mob.getMoveControl().setWantedPosition(
                px, occupied.getY(), pz, nav.pace());
        return true;
    }

    /**
     * 穿角：斜着过两根柱子之间那道缝，瞄的是**方块角点**而不是目标格心。
     *
     * <p>栅栏柱只占格子中间 0.375–0.625，斜穿从方块角点过去时身位箱是
     * 0.7–1.3，两边各留 0.075 格——**过得去，但只够 0.075 格**。普通走段瞄
     * 的是目标格心，移动控制又沿她的朝向推，朝向稍有摆动就蹭上柱子、被撞
     * 回来，再试再撞：玩家实测"即使她有路径，依旧卡在缺口处，有一种未知的
     * 东西在阻止"——那东西就是这两根柱子。
     *
     * <p>手法与过柱同一套：把移动控制停在原地，直写一股小速度**朝角点**推，
     * 过了角点再交还普通走段。图上连了边不等于走得过去，这一课挤边跨越当
     * 年就上过一次。
     *
     * @return true 表示这一 tick 已接管
     */
    boolean threadTheCorner(BlockPos here, BlockPos node, int dx, int dz,
            int dy) {
        // 斜走只可能是一格。{@code dx}/{@code dz} 是节点的**原始差值**，远
        // 节点时能到三四格——拿它去取"正交邻格"会取到风马牛不相及的格子，
        // 于是这一支在不该接管的地方接管（实测：宽圈那条被它拖住，出了圈却
        // 走不完剩下的四格）。
        if (!mob.onGround() || Math.abs(dx) != 1 || Math.abs(dz) != 1) {
            return false;
        }
        // 正交两侧都被占才是"穿角"；两侧通的斜走照常走，别插手。
        if (!FootingRule.coversCenter(mob.level(), here.offset(dx, 0, 0))
                || !FootingRule.coversCenter(mob.level(),
                        here.offset(0, 0, dz))) {
            return false;
        }
        double cornerX = here.getX() + (dx > 0 ? 1.0D : 0.0D);
        double cornerZ = here.getZ() + (dz > 0 ? 1.0D : 0.0D);
        // 已经过了角点：剩下的交给普通走段，别把她按在缝里。
        if ((mob.getX() - cornerX) * dx > 0.12D
                && (mob.getZ() - cornerZ) * dz > 0.12D) {
            return false;
        }
        // 瞄点略微越过角点，免得贴着角停住。
        double aimX = cornerX + dx * 0.35D;
        double aimZ = cornerZ + dz * 0.35D;
        double toX = aimX - mob.getX();
        double toZ = aimZ - mob.getZ();
        double flat = Math.max(1.0E-4D, Math.hypot(toX, toZ));
        // 斜着**上**一格：贴到角点再起跳，滞空交给落点锁——不锁的话升过坎
        // 之前水平被撞成零，她原地起落（门槛跳栽过一次）。登阶段接不了这
        // 一支：它是贴脸撞跳，穿不过 0.075 格的角缝。
        if (dy > 0) {
            // 先蹭再跳，还是直接跳？看**她这一层有没有缝**。
            //
            // 柱子那种：栅栏只占格心四分之一，同层的对角就有 0.075 格的缝，
            // 所以先贴到角点再起跳最稳——瞄得准，滞空短。
            //
            // 坎那种：两侧在她脚下这一层是**实心的地形**，没有任何缝。她一
            // 往角点挪就撞上，速度当场归零，然后一直钉在那儿（玩家实测的
            // "栅栏围角缺口+高低差进不去"就是这个，读数带：t13 起停在
            // (3.7, 2.0, 3.7)，速度恒为 0，而路是好的、reach=y）。缝在**上
            // 一层**——那一层的两侧是栅栏——所以必须先跳上去再穿。
            boolean gapAtHerLevel = FootingRule.walkLineClear(
                    mob.level(), mob.getX(), mob.getZ(), aimX, aimZ,
                    here.getY() + FootingRule.STANDABLE_TOP + 0.05D);
            if (gapAtHerLevel && flat > 0.55D) {
                mob.getMoveControl().setWantedPosition(
                        mob.getX(), here.getY(), mob.getZ(), 0.0D);
                Vec3 creep = mob.getDeltaMovement();
                mob.setDeltaMovement(toX / flat * CORNER_STEP, creep.y,
                        toZ / flat * CORNER_STEP);
                return true;
            }
            Vec3 landing = new Vec3(node.getX() + 0.5D, node.getY(),
                    node.getZ() + 0.5D);
            double upX = landing.x - mob.getX();
            double upZ = landing.z - mob.getZ();
            double upFlat = Math.max(0.3D, Math.hypot(upX, upZ));
            mob.setDeltaMovement(upX / upFlat * CORNER_HOP,
                    Leaper.JUMP_RISE, upZ / upFlat * CORNER_HOP);
            flight.lock(landing, CORNER_HOP, upX / upFlat, upZ / upFlat);
            return true;
        }
        mob.getMoveControl().setWantedPosition(
                mob.getX(), here.getY(), mob.getZ(), 0.0D);
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(
                toX / flat * CORNER_STEP, motion.y, toZ / flat * CORNER_STEP);
        return true;
    }

    /** 斜着上一格的起跳前速：够她在滞空里挪过角缝，又不至于甩出去。 */
    private static final double CORNER_HOP = 0.16D;

    /** 穿角的步速：慢到不会撞飞，快到能把她挪过那 0.075 格。 */
    private static final double CORNER_STEP = 0.08D;

    /**
     * 登阶的**接近段**在窄条上的车道纪律：两侧全空时停灯、直写小步速度，
     * 方向死死沿台阶线。
     *
     * <p>登阶在派发里排在窄道保持之前——一接管，车道纪律就轮不到；而它的
     * 接近段从前走裸的 MoveControl 推进：沿她的**朝向**推，着陆后的转身是
     * 渐变的，那条弧在 0.6 宽的悬空门板上就出界。宿主 MoveControl 还有一
     * 记"撞面即跳"的辅助（离面一格内、台阶高不够就 jump），朝向未对齐时
     * 那一跳就是侧跳。玩家二轮实测点名："下半活板门会因为某种原因转身导致
     * 直接从侧边跳下去，即使路径是正确的"——两条路都从裸推上走，停灯直写
     * 一并封死（速度 0 的 wanted 让宿主那记跳跃辅助也够不着门槛）。
     *
     * <p>只管**相邻一格**的登阶（远的、斜的各有其主），只管两侧都没有立足
     * 的窄条——宽地面上那条弧无害，照旧裸推。
     *
     * @return true 表示这一 tick 已接管
     */
    boolean approachAlongTheLane(BlockPos here, BlockPos node) {
        if (!mob.onGround()) {
            return false;
        }
        int dx = node.getX() - here.getX();
        int dz = node.getZ() - here.getZ();
        if (Math.abs(dx) + Math.abs(dz) != 1) {
            return false;
        }
        // 交战中她的脚归战斗管（与窄道保持同一条理由）。
        if (mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                .isPresent()) {
            return false;
        }
        boolean alongX = dx != 0;
        int px = alongX ? 0 : 1;
        int pz = alongX ? 1 : 0;
        double floorLine = here.getY() - 0.6D;
        if (FootingRule.coveringTopAt(mob.level(), here.offset(px, -1, pz))
                        >= floorLine
                || FootingRule.coveringTopAt(
                        mob.level(), here.offset(-px, -1, -pz)) >= floorLine) {
            return false;
        }
        double lane = alongX ? here.getZ() + 0.5D : here.getX() + 0.5D;
        double herPerp = alongX ? mob.getZ() : mob.getX();
        double nudge = Math.max(-0.12D,
                Math.min(0.12D, (lane - herPerp) * 0.6D));
        double forward = Math.signum(alongX ? dx : dz) * 0.10D;
        mob.getMoveControl().setWantedPosition(
                mob.getX(), here.getY(), mob.getZ(), 0.0D);
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(
                alongX ? forward : nudge,
                motion.y,
                alongX ? nudge : forward
        );
        return true;
    }

    /**
     * 窄道保持：一格宽的梁上，别让转身的弧线把她带出侧沿。
     *
     * <p>移动控制是**朝着她的朝向**推的，而转身是渐变的——掉头那一下她沿
     * 着一条弧走，一格宽的梁上这条弧就出界了（读数带实测：西行掉头后 z 从
     * 2.5 一 tick 漂到 2.2，三 tick 后离地，唇沿盲跳接手，人从此失控上浮）。
     * 玩家报的"转身时因为跳跃方向而导致跳下去"就是它。
     *
     * <p>手法与过柱同一套：偏出车道就把移动控制停在原地、直写一股自阻尼的
     * 侧向修正加一点前进量。只在**两侧都没有立足**时管——宽地面上她爱怎么
     * 绕就怎么绕，那是活人的样子。
     *
     * @return true 表示这一 tick 已接管
     */
    boolean holdTheNarrowWalk(BlockPos here, BlockPos node, int dx, int dz) {
        if (!mob.onGround()) {
            return false;
        }
        // 交战中她的脚归战斗管：停灯直写速度会把射手的站位推歪，弩局在这
        // 条规矩上栽过一次（靶子站在地板外的孤块上，接近时两侧正好是空的，
        // 一轮之后再没打中过）。窄道保持是**走路**的纪律，不是打架的。
        if (mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                .isPresent()) {
            return false;
        }
        boolean alongX = Math.abs(dx) >= Math.abs(dz);
        int px = alongX ? 0 : 1;
        int pz = alongX ? 1 : 0;
        double floorLine = here.getY() - 0.6D;
        if (FootingRule.coveringTopAt(mob.level(), here.offset(px, -1, pz))
                        >= floorLine
                || FootingRule.coveringTopAt(
                        mob.level(), here.offset(-px, -1, -pz)) >= floorLine) {
            return false;
        }
        double lane = alongX ? node.getZ() + 0.5D : node.getX() + 0.5D;
        double herPerp = alongX ? mob.getZ() : mob.getX();
        double off = lane - herPerp;
        // 门槛按身位算：她半宽 0.3，梁半宽 0.5——偏出 0.15 时外侧的脚已经
        // 探到沿外，再宽就来不及了。
        if (Math.abs(off) < 0.15D) {
            return false;
        }
        mob.getMoveControl().setWantedPosition(
                mob.getX(), here.getY(), mob.getZ(), 0.0D);
        double nudge = Math.max(-0.12D, Math.min(0.12D, off * 0.6D));
        double forward = Math.signum(alongX ? dx : dz) * 0.10D;
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(
                alongX ? forward : nudge,
                motion.y,
                alongX ? nudge : forward
        );
        return true;
    }

    /**
     * 走廊里的挡格若**只有一个**、是柱类高障、且旁有顺轴车道——给出车道
     * 坐标；矮板挡线（走路的事）、多柱、无缝，都返回 null。与评估器的
     * 空中车道同一把尺。
     */
    Double corridorLane(BlockPos here, int dx, int dz,
            boolean alongX) {
        int steps = 8 * Math.max(Math.abs(dx), Math.abs(dz));
        int lastX = here.getX();
        int lastZ = here.getZ();
        Double lane = null;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            int cx = (int) Math.floor(here.getX() + 0.5D + dx * t);
            int cz = (int) Math.floor(here.getZ() + 0.5D + dz * t);
            if ((cx == here.getX() && cz == here.getZ())
                    || (cx == here.getX() + dx && cz == here.getZ() + dz)
                    || (cx == lastX && cz == lastZ)) {
                continue;
            }
            lastX = cx;
            lastZ = cz;
            BlockPos cell = new BlockPos(cx, here.getY(), cz);
            if (!FootingRule.coversCenter(mob.level(), cell)) {
                continue;
            }
            if (!FootingRule.tallAtCenter(mob.level(), cell)
                    || lane != null) {
                return null;
            }
            double found = FootingRule.squeezeLane(mob.level(), cell,
                    alongX, alongX ? mob.getZ() : mob.getX());
            if (Double.isNaN(found)) {
                return null;
            }
            lane = found;
        }
        return lane;
    }

}
