package com.laixia.maidintelligence.feature.advancement.client;

/**
 * 根进度图标条的换算。原版进度界面用的是 252 像素宽的 tab 条，女仆界面内容区只有 168，
 * 放不下那么多格，所以改成「左右箭头 + 一页若干图标」的翻页条。
 * <p>
 * 纯整数换算，不引用任何客户端类，便于单独验证。
 */
public final class AdvancementStripLayout {
    public static final int ICON_SIZE = 16;
    /** 每格宽度：图标 16 加 2 像素间距。 */
    public static final int SLOT_SIZE = 18;
    public static final int ARROW_WIDTH = 10;
    public static final int NO_SLOT = -1;

    private AdvancementStripLayout() {
    }

    /** 一页能放几个图标。两侧固定留出箭头宽度，翻页与否条目位置都不动。 */
    public static int perPage(int stripWidth) {
        return Math.max(1, (stripWidth - 2 * ARROW_WIDTH) / SLOT_SIZE);
    }

    public static int pageCount(int rootCount, int perPage) {
        if (rootCount <= 0) {
            return 1;
        }
        return (rootCount + perPage - 1) / perPage;
    }

    public static int pageOf(int rootIndex, int perPage) {
        return Math.max(0, rootIndex) / perPage;
    }

    /** 某一页第 slot 格对应的根进度序号，超出总数时返回 {@link #NO_SLOT}。 */
    public static int rootIndex(int page, int slot, int perPage, int rootCount) {
        int index = page * perPage + slot;
        return index < rootCount ? index : NO_SLOT;
    }

    public static int slotX(int stripLeft, int slot) {
        return stripLeft + ARROW_WIDTH + slot * SLOT_SIZE;
    }

    /**
     * 鼠标落在第几格图标上，不在图标上时返回 {@link #NO_SLOT}。
     */
    public static int slotAt(int stripLeft, int stripWidth, int mouseX) {
        int perPage = perPage(stripWidth);
        int offset = mouseX - stripLeft - ARROW_WIDTH;
        if (offset < 0) {
            return NO_SLOT;
        }
        int slot = offset / SLOT_SIZE;
        if (slot >= perPage || offset - slot * SLOT_SIZE >= ICON_SIZE) {
            return NO_SLOT;
        }
        return slot;
    }

    public static int previousArrowX(int stripLeft) {
        return stripLeft;
    }

    public static int nextArrowX(int stripLeft, int stripWidth) {
        return stripLeft + stripWidth - ARROW_WIDTH;
    }

    public static boolean inPreviousArrow(int stripLeft, int stripTop, int mouseX, int mouseY) {
        return inArrow(previousArrowX(stripLeft), stripTop, mouseX, mouseY);
    }

    public static boolean inNextArrow(int stripLeft, int stripWidth, int stripTop, int mouseX, int mouseY) {
        return inArrow(nextArrowX(stripLeft, stripWidth), stripTop, mouseX, mouseY);
    }

    private static boolean inArrow(int arrowX, int stripTop, int mouseX, int mouseY) {
        return mouseX >= arrowX
                && mouseX < arrowX + ARROW_WIDTH
                && mouseY >= stripTop
                && mouseY < stripTop + ICON_SIZE;
    }
}
