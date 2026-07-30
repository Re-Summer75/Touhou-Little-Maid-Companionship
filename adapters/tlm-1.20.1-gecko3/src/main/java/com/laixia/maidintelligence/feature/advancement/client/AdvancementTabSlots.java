package com.laixia.maidintelligence.feature.advancement.client;

import java.util.function.IntPredicate;

/**
 * 女仆界面顶部 Tab 条的槽位换算。
 * <p>
 * 本体贴图 {@code maid_gui_side.png} 在 {@code u=107} 起按 25 像素间距留了 6 个槽位，
 * 界面上的暗色导轨（{@code (94,7)} 处 149×21）正好铺满这 6 格。本体自己用掉 0-2，
 * 第 3 格的图标（AI 气泡）也已经画在贴图里、由官方聊天附属使用，所以这里从最右侧倒着挑空位。
 * <p>
 * 纯坐标换算，不引用任何客户端类，便于单独验证。
 */
public final class AdvancementTabSlots {
    public static final int WIDTH = 24;
    public static final int HEIGHT = 26;
    public static final int Y = 5;
    public static final int NO_SLOT = -1;

    private static final int FIRST_X = 94;
    private static final int SPACING = 25;
    private static final int FIRST_TEXTURE_U = 107;
    /**
     * 挑选顺序：从右往左，先避开本体的 0-2 与官方 AI 聊天附属惯用的 3。
     */
    private static final int[] PREFERENCE = {5, 4, 3};

    private AdvancementTabSlots() {
    }

    public static int x(int slot) {
        return FIRST_X + SPACING * slot;
    }

    public static int textureU(int slot) {
        return FIRST_TEXTURE_U + SPACING * slot;
    }

    /**
     * 按偏好顺序返回第一个空槽位，全被占用时返回 {@link #NO_SLOT}。
     */
    public static int freeSlot(IntPredicate occupied) {
        for (int slot : PREFERENCE) {
            if (!occupied.test(slot)) {
                return slot;
            }
        }
        return NO_SLOT;
    }

    /**
     * 判断控件是否压在指定槽位上，坐标都是屏幕绝对坐标。
     */
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
