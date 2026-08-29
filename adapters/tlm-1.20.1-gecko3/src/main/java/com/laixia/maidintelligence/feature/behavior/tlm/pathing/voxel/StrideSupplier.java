package com.laixia.maidintelligence.feature.behavior.tlm.pathing.voxel;

import java.util.List;


/**
 * 邻接供给：从一个锚点出发，这一步能到哪些锚点、用什么合同、花多少代价。
 *
 * <p>搜索核（{@link VoxelAstar}）只认这一个口子——边怎么连、拿什么验收
 * （扫掠仿真、贴边解析）全在供给侧，与搜索解耦。这也是迁移的缝：第一阶
 * 段供给侧桥接旧图的邻居，后续把边族逐类搬进来，搜索核一行不动。
 */
public interface StrideSupplier {

    /** 一条出边。 */
    record Out(Anchor to, Stride stride, double cost) {
    }

    /** {@code from} 出发的所有边。 */
    List<Out> from(Anchor from);

    /**
     * 两锚之间**一步直走**通不通：身位沿线无碰撞、脚下支撑连续。任意
     * 角拉直（Theta* 的视线检查）用它——只对走有意义，跳与降的合同锚
     * 定在离散动作上，不参与拉直。默认不通（供给侧没实现就没有拉直）。
     */
    default boolean lineWalkable(Anchor from, Anchor to) {
        return false;
    }
}
