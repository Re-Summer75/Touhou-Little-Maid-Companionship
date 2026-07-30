package com.laixia.maidintelligence.feature.advancement.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent;
import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.platform.network.ModNetwork;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 把进度页入口挂到女仆界面顶部的 Tab 条上。
 * <p>
 * 本体没有注册第四个 Tab 的 API，Tab 条上的空槽位是先到先得，所以这里分两步定位：
 * 用最低优先级监听 {@code Init}，先按「从右往左挑空位」放一次；再在这一帧真正开始渲染前
 * （{@link ScreenEvent.Render.Pre}，此时其他附属的按钮都已加进界面）复核一次，
 * 撞上了就换槽位，六格都被占满才退化成角落的小图标按钮。
 */
@OnlyIn(Dist.CLIENT)
public final class AdvancementGuiHandler {
    private static final String BUTTON_NAME = "tlm_companionship:advancement_tab";

    private AbstractMaidContainerGui<?> pendingGui;
    private MaidAdvancementTabButton pendingButton;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onInit(MaidContainerGuiEvent.Init event) {
        AbstractMaidContainerGui<?> gui = event.getGui();
        EntityMaid maid = gui.getMaid();
        if (maid == null) {
            return;
        }

        MaidAdvancementTabButton button = new MaidAdvancementTabButton(
                gui instanceof MaidAdvancementPageScreen,
                ignored -> ModNetwork.sendOpenMaidAdvancementPage(maid.getId())
        );
        place(button, gui, event.getLeftPos(), event.getTopPos());
        event.addButton(BUTTON_NAME, button);

        this.pendingGui = gui;
        this.pendingButton = button;
    }

    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Pre event) {
        if (pendingButton == null) {
            return;
        }
        if (event.getScreen() != pendingGui) {
            forgetPending();
            return;
        }

        AbstractMaidContainerGui<?> gui = pendingGui;
        MaidAdvancementTabButton button = pendingButton;
        forgetPending();
        place(button, gui, gui.getGuiLeft(), gui.getGuiTop());
    }

    private void forgetPending() {
        this.pendingGui = null;
        this.pendingButton = null;
    }

    private void place(
            MaidAdvancementTabButton button,
            AbstractMaidContainerGui<?> gui,
            int leftPos,
            int topPos
    ) {
        int slot = AdvancementTabSlots.freeSlot(
                candidate -> isSlotTaken(gui, button, leftPos, topPos, candidate)
        );
        if (slot == AdvancementTabSlots.NO_SLOT) {
            button.placeInCorner(leftPos, topPos);
        } else {
            button.placeInTabRow(leftPos, topPos, slot);
        }
    }

    private boolean isSlotTaken(
            AbstractMaidContainerGui<?> gui,
            MaidAdvancementTabButton self,
            int leftPos,
            int topPos,
            int slot
    ) {
        for (GuiEventListener child : gui.children()) {
            if (child == self || !(child instanceof AbstractWidget widget) || !widget.visible) {
                continue;
            }
            if (AdvancementTabSlots.overlaps(
                    slot,
                    leftPos,
                    topPos,
                    widget.getX(),
                    widget.getY(),
                    widget.getWidth(),
                    widget.getHeight()
            )) {
                return true;
            }
        }
        return false;
    }
}
