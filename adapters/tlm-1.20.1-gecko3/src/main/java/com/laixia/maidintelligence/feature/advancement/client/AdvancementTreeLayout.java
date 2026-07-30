package com.laixia.maidintelligence.feature.advancement.client;

/**
 * 进度树的平移换算，照搬原版 {@code AdvancementTab} 的做法，只是把写死的
 * 234×113 视口换成参数——女仆界面里的视口比原版窄得多。
 * <p>
 * 纯数值换算，不引用任何客户端类，便于单独验证。
 */
public final class AdvancementTreeLayout {
    private AdvancementTreeLayout() {
    }

    /** 首次显示时把内容摆到视口正中，对应原版的 {@code 117 - (maxX + minX) / 2}。 */
    public static double center(int viewportSize, int min, int max) {
        return viewportSize / 2 - (max + min) / 2;
    }

    /**
     * 内容比视口宽（高）才允许平移，边界也和原版一致：右（下）侧最多拉到内容末端贴边。
     */
    public static double clampScroll(double scroll, int min, int max, int viewportSize) {
        if (max - min <= viewportSize) {
            return scroll;
        }
        double lower = -(double) (max - viewportSize);
        return Math.max(lower, Math.min(scroll, 0.0D));
    }
}
