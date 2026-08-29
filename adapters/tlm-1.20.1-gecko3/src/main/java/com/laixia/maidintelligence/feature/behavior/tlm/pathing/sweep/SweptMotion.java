package com.laixia.maidintelligence.feature.behavior.tlm.pathing.sweep;

import com.laixia.maidintelligence.feature.behavior.domain.motion.BallisticArc;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.FootingRule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫掠仿真：把她的身位箱沿真实弧线逐 tick 推过世界，撞不撞、落在哪、落
 * 得稳不稳，让碰撞自己回答——边族验收的唯一内核（分阶段换血中）。
 *
 * <p>手工枚举的判据在本质上追不上方块形状的组合爆炸：门板三缺口、石锥、
 * 末地烛，每个新形状一个 if，补丁之间还互相踩。这里只有一条规则：**数学
 * 芯（{@code BallisticArc}）给轨迹，真实 {@code VoxelShape} 给碰撞**，两
 * 者之外不引入任何"这格是什么"的词汇。
 *
 * <p>解算次序照抄原版 {@code Entity.move} 的分轴：每 tick 先竖直 clip
 * （落地/撞头），再水平逐轴 clip（撞面即该轴速度归零、不再回弹）。落地
 * 那一下顺带验收**落得稳**：接住她的那块碰撞所在的格子若是细高柱
 * （{@code FootingRule.slimPillar}），那是栖在尖上，不算落点。
 */
public final class SweptMotion {
    /** 一条弧最多仿真多少 tick：满跳全弧十六 tick 上下，翻倍兜底。 */
    public static final int LONGEST_FLIGHT = 32;

    /** 碰撞探测的皮厚：贴着算碰上，防浮点缝。 */
    private static final double SKIN = 1.0E-7D;

    private SweptMotion() {
    }

    /** 弧的结局。 */
    public enum Outcome {
        /** 落在能站住人的支撑上。 */
        LANDED,
        /** 物理上触了地，接住她的却是细高柱——栖在尖上，不算落点。 */
        PERCHED,
        /** 到仿真上限还没落地（往虚空里飞）。 */
        AIRBORNE
    }

    /**
     * 一次仿真的完整答卷。
     *
     * @param end     终点（落地时是脚点；未落地时是最后位置）
     * @param ticks   飞了多少 tick
     * @param outcome 结局
     */
    public record Flight(Vec3 end, int ticks, Outcome outcome) {
    }

    /**
     * 从 {@code start}（脚点）以 {@code velocity} 起飞，逐 tick 推进到落地
     * 或超时。裸物理版：起跳 tick 吃地面摩擦（黄金对表的真值形态）。
     *
     * <p>纯函数：只读世界，不碰实体。执行侧照同一初速起跳、飞行锁照同一
     * 条弧校准，图与执行从此物理同源。
     */
    public static Flight fly(
            BlockGetter level,
            Vec3 start,
            Vec3 velocity,
            double width,
            double height
    ) {
        return fly(level, start, velocity, width, height, false);
    }

    /**
     * {@code contractHeld}：弧线合同在场的跳（执行侧上了落点锁）里，起跳
     * tick 被地面摩擦吃掉的水平速度会被合同每 tick 补回（{@code
     * LeapFlight.restoreTheArc} 只补不削），等效全程空气阻力——图侧验收
     * 边时传 true，仿真的才是她真会飞出来的那条弧。
     */
    public static Flight fly(
            BlockGetter level,
            Vec3 start,
            Vec3 velocity,
            double width,
            double height,
            boolean contractHeld
    ) {
        double half = width / 2.0D;
        Vec3 at = start;
        Vec3 v = velocity;
        // 起跳那一 tick 的水平衰减是**地面摩擦**，不是空气阻力：原版
        // travel 在 move 之前取 onGround 定系数，起跳当刻她还贴着地——
        // 石头上是 0.6×0.91=0.546。黄金对表实测：全程按 0.91 算会飞远
        // 0.66 格，恰等于首 tick 差值 0.3×(0.91−0.546) 沿弧放大的和。
        BlockPos launchPad = BlockPos.containing(
                start.x, start.y - 0.05D, start.z);
        double groundDrag = level.getBlockState(launchPad).getBlock()
                .getFriction() * BallisticArc.HORIZONTAL_DRAG;
        for (int tick = 1; tick <= LONGEST_FLIGHT; tick++) {
            AABB body = new AABB(
                    at.x - half, at.y, at.z - half,
                    at.x + half, at.y + height, at.z + half);
            // 竖直先行。落地=下行被截；撞头=上行被截，竖直速度归零。
            double dy = clip(level, body, Axis.Y, v.y);
            boolean grounded = v.y < 0.0D && dy > v.y + SKIN;
            if (v.y > 0.0D && dy < v.y - SKIN) {
                v = new Vec3(v.x, 0.0D, v.z);
            }
            body = body.move(0.0D, dy, 0.0D);
            // 水平逐轴。撞面即该轴归零——原版就是这么吃掉冲量的。**合同
            // 在场时不归零**：弧线合同每 tick 把被撞掉的水平速度按 0.91
            // 几何列写回（LeapFlight.restoreTheArc 只补不削），位移截停、
            // 冲量不灭——贴着立面等弧升过沿口再继续推，正是她跳上门板檐
            // 的真实机制（贴立面截停、两 tick 后从上方落上檐顶，实测）。
            double dx = clip(level, body, Axis.X, v.x);
            if (Math.abs(dx - v.x) > SKIN && !contractHeld) {
                v = new Vec3(0.0D, v.y, v.z);
            }
            body = body.move(dx, 0.0D, 0.0D);
            double dz = clip(level, body, Axis.Z, v.z);
            if (Math.abs(dz - v.z) > SKIN && !contractHeld) {
                v = new Vec3(v.x, v.y, 0.0D);
            }
            body = body.move(0.0D, 0.0D, dz);
            at = new Vec3(
                    (body.minX + body.maxX) / 2.0D,
                    body.minY,
                    (body.minZ + body.maxZ) / 2.0D);
            if (grounded) {
                return new Flight(at, tick, steady(level, at) ? Outcome.LANDED
                        : Outcome.PERCHED);
            }
            double drag = tick == 1 && !contractHeld
                    ? groundDrag
                    : BallisticArc.HORIZONTAL_DRAG;
            v = new Vec3(
                    v.x * drag,
                    (v.y - BallisticArc.GRAVITY) * BallisticArc.VERTICAL_DRAG,
                    v.z * drag);
        }
        return new Flight(at, LONGEST_FLIGHT, Outcome.AIRBORNE);
    }

    /**
     * **走路的预演**：按这股水平速度推 {@code ticks} tick，返回她一共掉
     * 了多少（脚点的净下落）。
     *
     * <p>图侧的每条跳边都在这个内核里真飞过一遍才敢连；走路却长期靠边
     * 距、步速这些经验值兜着，一个新形状就得加一条常数（玩家点破："总
     * 是在调参，这样很难说适应不明场景，没有路线预测吗"）。同一个内核
     * 接到走路上，执行侧就能自己问："我这么走，会不会真的掉下去。"
     *
     * <p>速度按每 tick 重写（执行侧就是这么驱动的），所以水平分量恒定，
     * 只有重力在积累——预演与实机同一套物理。
     */
    /** 一段走路预演的答卷：推完之后人在哪、一共掉了多少。 */
    public record Trot(Vec3 end, double drop) {
    }

    /** 分段预演：返回终点与净下落，供调用方**接着往下一段推**。 */
    public static Trot trot(
            BlockGetter level,
            Vec3 feet,
            double vx,
            double vz,
            double width,
            double height,
            int ticks
    ) {
        double drop = dropAhead(level, feet, vx, vz, width, height, ticks);
        return new Trot(new Vec3(
                feet.x + vx * ticks, feet.y - drop, feet.z + vz * ticks),
                drop);
    }

    public static double dropAhead(
            BlockGetter level,
            Vec3 feet,
            double vx,
            double vz,
            double width,
            double height,
            int ticks
    ) {
        double half = width / 2.0D;
        Vec3 at = feet;
        double vy = 0.0D;
        for (int t = 1; t <= ticks; t++) {
            AABB body = new AABB(
                    at.x - half, at.y, at.z - half,
                    at.x + half, at.y + height, at.z + half);
            vy = (vy - BallisticArc.GRAVITY) * BallisticArc.VERTICAL_DRAG;
            double dy = clip(level, body, Axis.Y, vy);
            if (vy < 0.0D && dy > vy + SKIN) {
                vy = 0.0D;
            }
            body = body.move(0.0D, dy, 0.0D);
            double dx = clip(level, body, Axis.X, vx);
            body = body.move(dx, 0.0D, 0.0D);
            double dz = clip(level, body, Axis.Z, vz);
            body = body.move(0.0D, 0.0D, dz);
            at = new Vec3(
                    (body.minX + body.maxX) / 2.0D,
                    body.minY,
                    (body.minZ + body.maxZ) / 2.0D);
        }
        return feet.y - at.y;
    }

    /**
     * 这个脚点放不放得下她：身位箱与任何真实碰撞相交就算放不下。
     *
     * <p>起跳前必须问一句。仿真的裁剪只回答"前方有没有墙"，**起点已经
     * 嵌在墙里时它答不了**——间隙判据看的是"墙的近沿在身位之外"，而嵌
     * 在里面时这条不成立，于是它照原速放行，整条弧穿柱而过。栅栏圈的
     * 假边就是这么来的：贴沿起跳把起点前探到柱心（锚心 8.5＋0.4），图
     * 于是连出"从墙里跳到墙外"的边，她照着跳、撞柱弹回，每两三秒一轮
     * （玩家最早报的"贴着栅栏无限重试"，追到这里才算见底）。
     */
    public static boolean bodyClear(BlockGetter level, Vec3 feet,
            double width, double height) {
        double half = width / 2.0D;
        AABB body = new AABB(
                feet.x - half, feet.y + 0.02D, feet.z - half,
                feet.x + half, feet.y + height, feet.z + half);
        for (BlockPos at : BlockPos.betweenClosed(
                BlockPos.containing(body.minX, body.minY, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            VoxelShape shape = level.getBlockState(at)
                    .getCollisionShape(level, at);
            if (shape.isEmpty()) {
                continue;
            }
            if (!shape.bounds().move(at.getX(), at.getY(), at.getZ())
                    .intersects(body)) {
                continue;
            }
            BlockPos frozen = at.immutable();
            for (AABB box : shape.toAabbs()) {
                if (box.move(frozen.getX(), frozen.getY(), frozen.getZ())
                        .intersects(body)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 接住她的支撑站不站得住人：脚下贴皮那一格不是细高柱、且顶面真在脚
     * 底（不是悬空归零的假落地）。
     */
    private static boolean steady(BlockGetter level, Vec3 feet) {
        BlockPos underfoot = BlockPos.containing(
                feet.x, feet.y - 0.05D, feet.z);
        VoxelShape holder = level.getBlockState(underfoot)
                .getCollisionShape(level, underfoot);
        if (!holder.isEmpty()) {
            return !FootingRule.slimPillar(holder);
        }
        // 贴皮格自己没碰撞：支撑在整格边界正下方（脚点恰是整数高）。
        BlockPos below = underfoot.below();
        VoxelShape floor = level.getBlockState(below)
                .getCollisionShape(level, below);
        return !floor.isEmpty() && !FootingRule.slimPillar(floor);
    }

    /** 分轴位移裁剪：这一步沿该轴最多能挪多少。 */
    private static double clip(
            BlockGetter level,
            AABB body,
            Axis axis,
            double wanted
    ) {
        if (wanted == 0.0D) {
            return 0.0D;
        }
        double allowed = wanted;
        for (AABB wall : nearbySolids(level, body, axis, wanted)) {
            allowed = axis.clip(body, wall, allowed);
            if (Math.abs(allowed) < SKIN) {
                return 0.0D;
            }
        }
        return allowed;
    }

    /** 这一步扫过的空间里所有真实碰撞盒（VoxelShape 拆成盒）。 */
    private static List<AABB> nearbySolids(
            BlockGetter level,
            AABB body,
            Axis axis,
            double wanted
    ) {
        AABB sweep = body.expandTowards(
                axis == Axis.X ? wanted : 0.0D,
                axis == Axis.Y ? wanted : 0.0D,
                axis == Axis.Z ? wanted : 0.0D
        ).inflate(SKIN);
        List<AABB> solids = new ArrayList<>();
        // 遍历窗**向下扩一格**：方块的碰撞盒只会往上长出自己的格（栏
        // 杆与墙一格半高），身体升过一格线后盒还在挡路、宿主格却落在按
        // 角点取的窗外，不扩它就**隐形**（栅栏圈实测：弧线笔直穿墙，图
        // 连出 192 连跳的假边——玩家追的"无限重试"之根）。
        //
        // 三轴齐扩试过一轮：27 格对 12 格，而这段代码在跳边验收里每
        // tick 三轴各跑一遍，整轮七分钟只跑完一条测试（线程转储实锤 CPU
        // 全烧在这儿）。横向的盒从不越界，扩它只是白扩。
        BlockPos.betweenClosed(
                BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ)
                        .below(),
                BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ)
        ).forEach(pos -> {
            VoxelShape shape = level.getBlockState(pos)
                    .getCollisionShape(level, pos);
            if (shape.isEmpty()) {
                return;
            }
            // 外包盒粗筛：扫掠每 tick 三轴各问一遍邻域，细拆的开销按
            // 二十七格乘三轴乘八 tick 放大，粗筛先挡掉九成。
            if (!shape.bounds().move(pos.getX(), pos.getY(), pos.getZ())
                    .intersects(sweep)) {
                return;
            }
            BlockPos frozen = pos.immutable();
            for (AABB box : shape.toAabbs()) {
                AABB placed = box.move(
                        frozen.getX(), frozen.getY(), frozen.getZ());
                if (placed.intersects(sweep)) {
                    solids.add(placed);
                }
            }
        });
        return solids;
    }

    /** 单轴裁剪的三份同构代码收在一处。 */
    private enum Axis {
        X {
            double clip(AABB body, AABB wall, double wanted) {
                if (wall.maxY <= body.minY + SKIN
                        || wall.minY >= body.maxY - SKIN
                        || wall.maxZ <= body.minZ + SKIN
                        || wall.minZ >= body.maxZ - SKIN) {
                    return wanted;
                }
                return squeeze(body.minX, body.maxX,
                        wall.minX, wall.maxX, wanted);
            }
        },
        Y {
            double clip(AABB body, AABB wall, double wanted) {
                if (wall.maxX <= body.minX + SKIN
                        || wall.minX >= body.maxX - SKIN
                        || wall.maxZ <= body.minZ + SKIN
                        || wall.minZ >= body.maxZ - SKIN) {
                    return wanted;
                }
                return squeeze(body.minY, body.maxY,
                        wall.minY, wall.maxY, wanted);
            }
        },
        Z {
            double clip(AABB body, AABB wall, double wanted) {
                if (wall.maxX <= body.minX + SKIN
                        || wall.minX >= body.maxX - SKIN
                        || wall.maxY <= body.minY + SKIN
                        || wall.minY >= body.maxY - SKIN) {
                    return wanted;
                }
                return squeeze(body.minZ, body.maxZ,
                        wall.minZ, wall.maxZ, wanted);
            }
        };

        abstract double clip(AABB body, AABB wall, double wanted);

        /** 一维间隙：正向顶到墙的近沿、负向顶到墙的远沿。 */
        static double squeeze(
                double bodyMin, double bodyMax,
                double wallMin, double wallMax,
                double wanted
        ) {
            if (wanted > 0.0D && wallMin >= bodyMax - SKIN) {
                return Math.min(wanted, wallMin - bodyMax);
            }
            if (wanted < 0.0D && wallMax <= bodyMin + SKIN) {
                return Math.max(wanted, wallMax - bodyMin);
            }
            return wanted;
        }
    }
}
