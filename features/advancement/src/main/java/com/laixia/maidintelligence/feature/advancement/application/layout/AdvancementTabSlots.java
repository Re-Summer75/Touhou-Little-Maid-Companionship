package com.laixia.maidintelligence.feature.advancement.application.layout;

import java.util.function.IntPredicate;

/**
 * 女仆界面顶部 Tab 条的纯槽位换算。
 */
public final class AdvancementTabSlots {
    public static final int WIDTH = 24;
    public static final int HEIGHT = 26;
    public static final int Y = 5;
    public static final int NO_SLOT = -1;

    private static final int FIRST_X = 94;
    private static final int SPACING = 25;
    private static final int FIRST_TEXTURE_U = 107;
    /** 从右往左，先避开本体的 0-2 与官方 AI 聊天附属惯用的 3。 */
    private static final int[] PREFERENCE = {5, 4, 3};

    private AdvancementTabSlots() {
    }

    public static int x(int slot) {
        return FIRST_X + SPACING * slot;
    }

    public static int textureU(int slot) {
        return FIRST_TEXTURE_U + SPACING * slot;
    }

    /** 按偏好顺序返回第一个空槽位，全被占用时返回 {@link #NO_SLOT}。 */
    public static int freeSlot(IntPredicate occupied) {
        for (int slot : PREFERENCE) {
            if (!occupied.test(slot)) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    /** 判断控件是否压在指定槽位上，坐标都是屏幕绝对坐标。 */
    public static boolean overlaps(
            int slot,
            int leftPos,
            int topPos,
            int widgetX,
            int widgetY,
            int widgetWidth,
            int widgetHeight
    ) {
        int slotX = leftPos + x(slot);
        int slotY = topPos + Y;
        return widgetX < slotX + WIDTH
                && widgetX + widgetWidth > slotX
                && widgetY < slotY + HEIGHT
                && widgetY + widgetHeight > slotY;
    }
}
