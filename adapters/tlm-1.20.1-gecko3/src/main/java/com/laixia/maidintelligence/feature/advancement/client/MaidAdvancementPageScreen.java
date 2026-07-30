package com.laixia.maidintelligence.feature.advancement.client;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.laixia.maidintelligence.feature.advancement.application.layout.AdvancementStripLayout;
import com.laixia.maidintelligence.feature.advancement.menu.MaidAdvancementContainer;
import com.laixia.maidintelligence.feature.advancement.network.AdvancementNetwork;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * 女仆进度页。作为女仆容器界面的一个 Tab 页存在，所以左侧状态区、顶部 Tab 条、任务列表
 * 这些外框都由本体绘制，这里只负责右侧内容区（本体贴图里预留的空面板）：
 * 一行标题与完成数、一行可翻页的根进度图标条，其余是可拖动平移的进度树。
 * <p>
 * 进度树取自客户端本地的完整进度表（含模组），完成情况由服务端按女仆下发。
 */
@OnlyIn(Dist.CLIENT)
public final class MaidAdvancementPageScreen extends AbstractMaidContainerGui<MaidAdvancementContainer> {
    /** 本体主贴图在 {@code (80,28)} 留了 176×137 的空面板，去掉边框后就是这块可用区域。 */
    private static final int CONTENT_X = 84;
    private static final int CONTENT_Y = 32;
    private static final int CONTENT_WIDTH = 168;
    private static final int CONTENT_HEIGHT = 129;
    private static final int TITLE_ROW_HEIGHT = 10;
    private static final int STRIP_ROW_HEIGHT = 17;
    private static final int TREE_TOP_OFFSET = TITLE_ROW_HEIGHT + STRIP_ROW_HEIGHT;
    private static final int SCROLL_STEP = 16;

    private static final int TITLE_COLOR = 0xFF404040;
    private static final int COUNTER_COLOR = 0xFF6E6E6E;
    private static final int SLOT_BACKGROUND = 0x30000000;
    private static final int SLOT_SELECTED_BACKGROUND = 0x60FFFFFF;
    private static final int SLOT_SELECTED_BORDER = 0xFFFFFFFF;
    private static final int ARROW_COLOR = 0xFF404040;
    private static final int ARROW_DISABLED_COLOR = 0xFFAAAAAA;
    private static final int VIEWPORT_BORDER_COLOR = 0xFF2E2E2E;

    @Nullable
    private static ResourceLocation lastSelectedRoot;

    private final List<MaidAdvancementTree> trees = new ArrayList<>();

    private int builtVersion = -1;
    private int selected;
    private int page;
    private boolean panning;

    public MaidAdvancementPageScreen(
            MaidAdvancementContainer container,
            Inventory inventory,
            Component title
    ) {
        super(container, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        if (maid != null) {
            AdvancementNetwork.requestSnapshot(maid.getId());
        }
    }

    @Override
    protected void renderAddition(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        buildTreesIfNeeded();

        MaidAdvancementTree tree = selectedTree();
        drawHeader(graphics, tree);
        drawStrip(graphics);
        if (tree == null) {
            graphics.drawString(
                    font,
                    Component.translatable(ModResources.translationKey("gui", "advancement.empty")),
                    CONTENT_X + leftPos,
                    treeTop() + 4,
                    COUNTER_COLOR,
                    false
            );
            return;
        }
        tree.drawContents(graphics, treeLeft(), treeTop(), CONTENT_WIDTH, treeHeight());
        graphics.renderOutline(
                treeLeft() - 1,
                treeTop() - 1,
                CONTENT_WIDTH + 2,
                treeHeight() + 2,
                VIEWPORT_BORDER_COLOR
        );
    }

    @Override
    protected void renderAdditionTransTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        MaidAdvancementTree tree = selectedTree();
        if (tree != null && inTreeViewport(mouseX, mouseY)) {
            tree.drawHover(
                    graphics,
                    treeLeft(),
                    treeTop(),
                    CONTENT_WIDTH,
                    treeHeight(),
                    mouseX,
                    mouseY,
                    this.width
            );
            return;
        }
        treeAtStrip(mouseX, mouseY).ifPresent(hovered -> graphics.renderTooltip(
                font,
                hovered.title(),
                mouseX,
                mouseY
        ));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX;
        int y = (int) mouseY;
        if (inStripRow(y)) {
            if (handleStripClick(x, y)) {
                return true;
            }
        }
        if (inTreeViewport(x, y)) {
            panning = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        MaidAdvancementTree tree = selectedTree();
        if (panning && tree != null) {
            tree.scroll(dragX, dragY, CONTENT_WIDTH, treeHeight());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        MaidAdvancementTree tree = selectedTree();
        if (tree != null && inTreeViewport((int) mouseX, (int) mouseY)) {
            tree.scroll(0.0D, delta * SCROLL_STEP, CONTENT_WIDTH, treeHeight());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphics graphics, @Nullable MaidAdvancementTree tree) {
        int headerY = topPos + CONTENT_Y + 1;
        int left = leftPos + CONTENT_X;
        if (tree == null) {
            return;
        }
        Component counter = Component.translatable(
                ModResources.translationKey("gui", "advancement.completion"),
                tree.completed(),
                tree.total()
        );
        int counterWidth = font.width(counter);
        int counterX = left + CONTENT_WIDTH - counterWidth;
        graphics.drawString(font, counter, counterX, headerY, COUNTER_COLOR, false);

        int titleRight = Math.max(left, counterX - 3);
        graphics.enableScissor(left, headerY, titleRight, headerY + font.lineHeight);
        graphics.drawString(font, tree.title(), left, headerY, TITLE_COLOR, false);
        graphics.disableScissor();
    }

    private void drawStrip(GuiGraphics graphics) {
        int stripLeft = leftPos + CONTENT_X;
        int stripTop = stripTop();
        int perPage = AdvancementStripLayout.perPage(CONTENT_WIDTH);
        int pages = AdvancementStripLayout.pageCount(trees.size(), perPage);
        page = Math.min(page, pages - 1);

        for (int slot = 0; slot < perPage; slot++) {
            int index = AdvancementStripLayout.rootIndex(page, slot, perPage, trees.size());
            if (index == AdvancementStripLayout.NO_SLOT) {
                break;
            }
            int iconX = AdvancementStripLayout.slotX(stripLeft, slot);
            if (index == selected) {
                graphics.fill(
                        iconX,
                        stripTop,
                        iconX + AdvancementStripLayout.ICON_SIZE,
                        stripTop + AdvancementStripLayout.ICON_SIZE,
                        SLOT_SELECTED_BACKGROUND
                );
                graphics.renderOutline(
                        iconX,
                        stripTop,
                        AdvancementStripLayout.ICON_SIZE,
                        AdvancementStripLayout.ICON_SIZE,
                        SLOT_SELECTED_BORDER
                );
            } else {
                graphics.fill(
                        iconX,
                        stripTop,
                        iconX + AdvancementStripLayout.ICON_SIZE,
                        stripTop + AdvancementStripLayout.ICON_SIZE,
                        SLOT_BACKGROUND
                );
            }
            graphics.renderFakeItem(trees.get(index).icon(), iconX, stripTop);
        }

        if (pages > 1) {
            drawArrow(graphics, AdvancementStripLayout.previousArrowX(stripLeft), stripTop, "<", page > 0);
            drawArrow(
                    graphics,
                    AdvancementStripLayout.nextArrowX(stripLeft, CONTENT_WIDTH),
                    stripTop,
                    ">",
                    page < pages - 1
            );
        }
    }

    private void drawArrow(GuiGraphics graphics, int arrowX, int stripTop, String glyph, boolean enabled) {
        int glyphX = arrowX + (AdvancementStripLayout.ARROW_WIDTH - font.width(glyph)) / 2;
        int glyphY = stripTop + (AdvancementStripLayout.ICON_SIZE - font.lineHeight) / 2;
        graphics.drawString(
                font,
                glyph,
                glyphX,
                glyphY,
                enabled ? ARROW_COLOR : ARROW_DISABLED_COLOR,
                false
        );
    }

    private boolean handleStripClick(int mouseX, int mouseY) {
        int stripLeft = leftPos + CONTENT_X;
        int perPage = AdvancementStripLayout.perPage(CONTENT_WIDTH);
        int pages = AdvancementStripLayout.pageCount(trees.size(), perPage);
        if (pages > 1) {
            if (AdvancementStripLayout.inPreviousArrow(stripLeft, stripTop(), mouseX, mouseY) && page > 0) {
                page--;
                return true;
            }
            if (AdvancementStripLayout.inNextArrow(stripLeft, CONTENT_WIDTH, stripTop(), mouseX, mouseY)
                    && page < pages - 1) {
                page++;
                return true;
            }
        }
        int slot = AdvancementStripLayout.slotAt(stripLeft, CONTENT_WIDTH, mouseX);
        if (slot == AdvancementStripLayout.NO_SLOT) {
            return false;
        }
        int index = AdvancementStripLayout.rootIndex(page, slot, perPage, trees.size());
        if (index == AdvancementStripLayout.NO_SLOT) {
            return false;
        }
        select(index);
        return true;
    }

    private Optional<MaidAdvancementTree> treeAtStrip(int mouseX, int mouseY) {
        if (!inStripRow(mouseY)) {
            return Optional.empty();
        }
        int perPage = AdvancementStripLayout.perPage(CONTENT_WIDTH);
        int slot = AdvancementStripLayout.slotAt(leftPos + CONTENT_X, CONTENT_WIDTH, mouseX);
        if (slot == AdvancementStripLayout.NO_SLOT) {
            return Optional.empty();
        }
        int index = AdvancementStripLayout.rootIndex(page, slot, perPage, trees.size());
        return index == AdvancementStripLayout.NO_SLOT
                ? Optional.empty()
                : Optional.of(trees.get(index));
    }

    private void select(int index) {
        selected = index;
        lastSelectedRoot = trees.get(index).root().getId();
    }

    /**
     * 建树。进度表是跟着推送整份换的（完成一条会连带露出周围原本不可见的条目），
     * 所以每次版本变化都重建，只把平移位置从旧树接过来，免得刚拿到进度视野就跳回中心。
     */
    private void buildTreesIfNeeded() {
        if (minecraft == null || maid == null || builtVersion == ClientMaidAdvancements.version()) {
            return;
        }
        builtVersion = ClientMaidAdvancements.version();
        List<Advancement> roots = new ArrayList<>();
        ClientMaidAdvancements.rootsFor(maid.getId()).forEach(root -> {
            if (root.getDisplay() != null) {
                roots.add(root);
            }
        });
        roots.sort(Comparator
                .comparingInt(MaidAdvancementPageScreen::rootOrder)
                .thenComparing(root -> root.getId().toString()));

        List<MaidAdvancementTree> previous = new ArrayList<>(trees);
        trees.clear();
        Map<ResourceLocation, AdvancementProgress> progress =
                ClientMaidAdvancements.progressFor(maid.getId());
        for (Advancement root : roots) {
            MaidAdvancementTree tree = MaidAdvancementTree.create(minecraft, root);
            if (tree == null) {
                continue;
            }
            previous.stream()
                    .filter(old -> old.root().getId().equals(root.getId()))
                    .findFirst()
                    .ifPresent(tree::adoptViewFrom);
            tree.applyProgress(progress);
            trees.add(tree);
        }
        restoreSelection();
    }

    /** 女仆专属的那棵排最前——这本来就是女仆自己的页面，然后原版，最后其它模组。 */
    private static int rootOrder(Advancement root) {
        String namespace = root.getId().getNamespace();
        if (ModResources.MOD_ID.equals(namespace)) {
            return 0;
        }
        return "minecraft".equals(namespace) ? 1 : 2;
    }

    private void restoreSelection() {
        selected = 0;
        if (lastSelectedRoot != null) {
            for (int index = 0; index < trees.size(); index++) {
                if (trees.get(index).root().getId().equals(lastSelectedRoot)) {
                    selected = index;
                    break;
                }
            }
        }
        page = AdvancementStripLayout.pageOf(selected, AdvancementStripLayout.perPage(CONTENT_WIDTH));
    }

    @Nullable
    private MaidAdvancementTree selectedTree() {
        if (trees.isEmpty()) {
            return null;
        }
        return trees.get(Math.min(selected, trees.size() - 1));
    }

    private int stripTop() {
        return topPos + CONTENT_Y + TITLE_ROW_HEIGHT;
    }

    private boolean inStripRow(int mouseY) {
        return mouseY >= stripTop() && mouseY < stripTop() + AdvancementStripLayout.ICON_SIZE;
    }

    private int treeLeft() {
        return leftPos + CONTENT_X;
    }

    private int treeTop() {
        return topPos + CONTENT_Y + TREE_TOP_OFFSET;
    }

    private int treeHeight() {
        return CONTENT_HEIGHT - TREE_TOP_OFFSET;
    }

    private boolean inTreeViewport(int mouseX, int mouseY) {
        return mouseX >= treeLeft()
                && mouseX < treeLeft() + CONTENT_WIDTH
                && mouseY >= treeTop()
                && mouseY < treeTop() + treeHeight();
    }
}
