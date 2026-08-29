package com.laixia.maidintelligence.feature.behavior.tlm.pathing.mesh;

import net.minecraft.world.phys.Vec3;

/**
 * 一块**可走多边形**：某个高度上的一片矩形地面，边界就是她能站的范围。
 *
 * <p>自有引擎此前把站位当作"点"（锚点），可走与否靠逐格的判据去逼近
 * ——走出台沿、挤不进柱缝、贴边锚变成死口袋，这些反复出现的毛病其实是
 * 同一个缺陷的不同外衣。多边形把问题换了个问法：**可走区域是有精确边
 * 界的面**，走路被约束在面里，"走出去"在构造上就不成立；柱旁那条
 * 0.375 宽的缝天生就是一块窄多边形，挤缝不再需要特例。
 *
 * <p>矩形而非任意多边形：方块世界的顶面本来就是轴对齐矩形，障碍占地也
 * 是——减出来的自由区域仍是矩形并集。够用，且判交、求portal都便宜。
 *
 * @param minX 西沿（含）
 * @param minZ 北沿（含）
 * @param maxX 东沿
 * @param maxZ 南沿
 * @param top  这块地面的高度（脚点 y）
 */
public record Surface(double minX, double minZ, double maxX, double maxZ,
        double top) {

    /** 短边：窄到放不下身位的面在建图时就该被丢掉。 */
    public double breadth() {
        return Math.min(maxX - minX, maxZ - minZ);
    }

    public double centerX() {
        return (minX + maxX) / 2.0D;
    }

    public double centerZ() {
        return (minZ + maxZ) / 2.0D;
    }

    public Vec3 center() {
        return new Vec3(centerX(), top, centerZ());
    }

    /** 这个点在不在面上（含边界）。 */
    public boolean holds(double x, double z) {
        return x >= minX - 1.0E-6D && x <= maxX + 1.0E-6D
                && z >= minZ - 1.0E-6D && z <= maxZ + 1.0E-6D;
    }

    /** 把点夹进面里：走路时的"别越界"就是这一句。 */
    public Vec3 clamp(double x, double z) {
        return new Vec3(
                Math.max(minX, Math.min(maxX, x)), top,
                Math.max(minZ, Math.min(maxZ, z)));
    }

    /** 点到这块面的水平距离（在面上即 0）。 */
    public double distance(double x, double z) {
        double dx = Math.max(0.0D, Math.max(minX - x, x - maxX));
        double dz = Math.max(0.0D, Math.max(minZ - z, z - maxZ));
        return Math.hypot(dx, dz);
    }

    /** 两块面之间的**门**：共享边界上真正走得过去的那一段（长度够身
     *  位宽才算）。返回 null 表示不相邻或缝太窄。 */
    public Portal portalTo(Surface other, double bodyWidth) {
        if (Math.abs(other.top - top) > 0.6D) {
            return null;
        }
        double loX = Math.max(minX, other.minX);
        double hiX = Math.min(maxX, other.maxX);
        double loZ = Math.max(minZ, other.minZ);
        double hiZ = Math.min(maxZ, other.maxZ);
        boolean touchX = Math.abs(other.minX - maxX) < 1.0E-6D
                || Math.abs(minX - other.maxX) < 1.0E-6D;
        boolean touchZ = Math.abs(other.minZ - maxZ) < 1.0E-6D
                || Math.abs(minZ - other.maxZ) < 1.0E-6D;
        if (touchX && hiZ - loZ >= bodyWidth - 1.0E-6D) {
            double x = Math.abs(other.minX - maxX) < 1.0E-6D ? maxX : minX;
            return new Portal(x, loZ, x, hiZ);
        }
        if (touchZ && hiX - loX >= bodyWidth - 1.0E-6D) {
            double z = Math.abs(other.minZ - maxZ) < 1.0E-6D ? maxZ : minZ;
            return new Portal(loX, z, hiX, z);
        }
        return null;
    }

    /** 两块面之间可通行的那条线段（漏斗算法的输入）。 */
    public record Portal(double leftX, double leftZ, double rightX,
            double rightZ) {

        public double midX() {
            return (leftX + rightX) / 2.0D;
        }

        public double midZ() {
            return (leftZ + rightZ) / 2.0D;
        }
    }
}
