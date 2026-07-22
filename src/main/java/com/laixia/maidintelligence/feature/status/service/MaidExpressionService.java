package com.laixia.maidintelligence.feature.status.service;

import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleDataCollection;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManager;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.ProgressChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.WaitingChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.WeakHashMap;

public final class MaidExpressionService {
    private static final int STATUS_PRIORITY = -1;
    private static final int STATUS_EXIST_TICKS = 15 * 20;
    private static final int WARNING_COOLDOWN_TICKS = 60 * 20;
    private static final int TOOL_BAR_BACKGROUND = 0xFF333333;
    private static final int TOOL_BAR_FOREGROUND = 0xFFFF5555;

    private final Map<EntityMaid, BubbleState> states = new WeakHashMap<>();

    public boolean showHungerWarning(EntityMaid maid) {
        BubbleState state = stateFor(maid);
        if (!canShowWarning(maid, state.hungerBubbleKey, state.lastHungerWarningTick)) {
            return false;
        }

        long key = addStatusBubble(maid, TextChatBubbleData.create(
                STATUS_EXIST_TICKS,
                Component.translatable(statusBubble("needs_food")),
                IChatBubbleData.TYPE_2,
                STATUS_PRIORITY
        ));
        if (key < 0) {
            return false;
        }
        state.hungerBubbleKey = key;
        state.lastHungerWarningTick = maid.level().getGameTime();
        return true;
    }

    public boolean showToolWarning(EntityMaid maid, ToolReplacementResult tool) {
        BubbleState state = stateFor(maid);
        if (!canShowWarning(maid, state.toolBubbleKey, state.lastToolWarningTick)) {
            return false;
        }

        long key = addStatusBubble(maid, ProgressChatBubbleData.create(
                STATUS_EXIST_TICKS,
                IChatBubbleData.TYPE_2,
                STATUS_PRIORITY,
                Component.translatable(
                        statusBubble("tool_low"),
                        tool.remainingDurability(),
                        tool.maximumDurability()
                ),
                TOOL_BAR_BACKGROUND,
                TOOL_BAR_FOREGROUND,
                tool.remainingRatio(),
                true
        ));
        if (key < 0) {
            return false;
        }
        state.toolBubbleKey = key;
        state.lastToolWarningTick = maid.level().getGameTime();
        return true;
    }

    public void showToolReplaced(EntityMaid maid) {
        clearToolWarning(maid);
        if (hasWaitingBubble(maid)) {
            return;
        }
        addStatusBubble(maid, TextChatBubbleData.create(
                STATUS_EXIST_TICKS,
                Component.translatable(statusBubble("tool_replaced")),
                IChatBubbleData.TYPE_2,
                STATUS_PRIORITY
        ));
    }

    public void clearHungerWarning(EntityMaid maid) {
        BubbleState state = states.get(maid);
        if (state != null) {
            removeOwnedBubble(maid, state.hungerBubbleKey);
            state.hungerBubbleKey = -1L;
        }
    }

    public void clearToolWarning(EntityMaid maid) {
        BubbleState state = states.get(maid);
        if (state != null) {
            removeOwnedBubble(maid, state.toolBubbleKey);
            state.toolBubbleKey = -1L;
        }
    }

    private boolean canShowWarning(EntityMaid maid, long bubbleKey, long lastWarningTick) {
        if (!(maid.level() instanceof ServerLevel) || hasWaitingBubble(maid)) {
            return false;
        }
        ChatBubbleDataCollection collection = maid.getChatBubbleManager().getChatBubbleDataCollection();
        if (bubbleKey >= 0 && collection.containsKey(bubbleKey)) {
            return false;
        }
        return lastWarningTick == Long.MIN_VALUE
                || maid.level().getGameTime() - lastWarningTick >= WARNING_COOLDOWN_TICKS;
    }

    private boolean hasWaitingBubble(EntityMaid maid) {
        var iterator = maid.getChatBubbleManager().getChatBubbleDataCollection().iterator();
        while (iterator.hasNext()) {
            if (WaitingChatBubbleData.ID.equals(iterator.next().id())) {
                return true;
            }
        }
        return false;
    }

    private long addStatusBubble(EntityMaid maid, IChatBubbleData bubble) {
        return maid.getChatBubbleManager().addChatBubble(bubble);
    }

    private void removeOwnedBubble(EntityMaid maid, long bubbleKey) {
        if (bubbleKey < 0) {
            return;
        }
        ChatBubbleManager manager = maid.getChatBubbleManager();
        if (manager.getChatBubbleDataCollection().containsKey(bubbleKey)) {
            manager.removeChatBubble(bubbleKey);
        }
    }

    private BubbleState stateFor(EntityMaid maid) {
        return states.computeIfAbsent(maid, ignored -> new BubbleState());
    }

    private static String statusBubble(String path) {
        return ModResources.translationKey("chat_bubble", "status." + path);
    }

    private static final class BubbleState {
        private long hungerBubbleKey = -1L;
        private long toolBubbleKey = -1L;
        private long lastHungerWarningTick = Long.MIN_VALUE;
        private long lastToolWarningTick = Long.MIN_VALUE;
    }
}
