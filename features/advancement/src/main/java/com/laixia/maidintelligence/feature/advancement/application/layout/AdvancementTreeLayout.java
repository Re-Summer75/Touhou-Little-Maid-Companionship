package com.laixia.maidintelligence.feature.advancement.application.layout;

/**
 * 进度树的平移换算，照搬原版 AdvancementTab 的做法，只是把写死的
 * 234×113 视口换成参数——女仆界面里的视口比原版窄得多。
 */
public final class AdvancementTreeLayout {
    private AdvancementTreeLayout() {
    }

    /** 首次显示时把内容摆到视口正中。 */
    public static double center(int viewportSize, int min, int max) {
        return viewportSize / 2 - (max + min) / 2;
    }

    /**
     * 内容比视口宽（高）才允许平移，右（下）侧最多拉到内容末端贴边。
     */
    public static double clampScroll(
            double scroll,
            int min,
            int max,
            int viewportSize
    ) {
        if (max - min <= viewportSize) {
            return scroll;
        }
        double lower = -(double) (max - viewportSize);
        return Math.max(lower, Math.min(scroll, 0.0D));
    }
}
