package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 锚点：一片**真实可站的支撑面**与它的代表点——自有寻路的一等公民。
 *
 * <p>格粒度的图把"站在哪"抹成了格心，亚格立足（横烛杆顶的中线、柱旁的
 * 贴边带、缝里的窄位）只能靠执行侧一柜子机构事后缝合——玩家点破的"缝缝
 * 补补"。第一次重铸把位置升为原生词汇（点锚）；这一次把**面**升为原生词
 * 汇：锚点携带支撑矩形（碰撞盒的顶面，机器人学的支撑多边形），判到、回
 * 锚、起跳全部对面收口——"她物理上站不到锚点"这一整族病（柱尖平衡位、
 * 碗壁顶着、贴边台面）从口径上消失。格子只剩空间索引的职能。
 *
 * @param at      代表点（支撑面中心），连续坐标
 * @param breadth 支撑面的水平短边（杆 0.25、门板与满块 1.0）——判到与
 *                死区的口径直接由它给
 * @param kind    支撑的性质，执行侧按它选步态
 * @param stand   支撑矩形：碰撞盒顶面的水平范围（y 即立足高）。站进这片
 *                面的任意点都算站上锚。
 * @param home    索引格。常态就是代表点所在的格；**车道站位除外**——它
 *                的代表点侧移出去可能跨进邻格，可它记的仍是原来那一格的
 *                账。失败账本按格记边（{@code veto.strike/absolve}），索
 *                引格一飘，实机走通的边就销不掉自己的前科，下次重铺又被
 *                账本拒之门外。
 */
public record Anchor(Vec3 at, double breadth, Kind kind, AABB stand,
        BlockPos home) {

    /** 常态：索引格就是代表点脚下那一格。 */
    public Anchor(Vec3 at, double breadth, Kind kind, AABB stand) {
        this(at, breadth, kind, stand,
                BlockPos.containing(at.x, at.y + 0.05D, at.z));
    }

    /** 支撑的性质。 */
    public enum Kind {
        /** 常规面：满块、台阶、门板——放开走。 */
        FLOOR,
        /** 窄面：杆顶、柱顶——踩点走，转弯在锚点上完成。 */
        NARROW,
        /** 贴边位：格心被高障占着，身位挂在边带上。 */
        EDGE,
        /** 水面：交给浮力，寻路只管到得了。 */
        WATER
    }

    /** 锚点的索引格。 */
    public BlockPos cell() {
        return home;
    }

    /** 与另一锚点的水平距离（代表点之间）。 */
    public double flatTo(Anchor other) {
        return Math.hypot(other.at.x - at.x, other.at.z - at.z);
    }

    /**
     * 她离这片支撑面的**边**还有多远（站在面外为负）。
     *
     * <p>与 {@link #standDist} 相反：那个问"离面多远"（面内恒零），这个
     * 问"面内还剩多少余地"。走路预演拿它当免检凭据——八 tick 走不出这
     * 片面，就不可能掉下去。
     */
    public double rimDist(double x, double z) {
        return Math.min(Math.min(x - stand.minX, stand.maxX - x),
                Math.min(z - stand.minZ, stand.maxZ - z));
    }

    /** 水平点到支撑面的距离：脚心悬在面上方即 0。 */
    public double standDist(double x, double z) {
        double dx = Math.max(0.0D,
                Math.max(stand.minX - x, x - stand.maxX));
        double dz = Math.max(0.0D,
                Math.max(stand.minZ - z, z - stand.maxZ));
        return Math.hypot(dx, dz);
    }
}
