package com.laixia.maidintelligence.feature.level.client;

import com.github.tartaricacid.touhoulittlemaid.client.gui.entity.maid.AbstractMaidContainerGui;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Either;
import com.laixia.maidintelligence.feature.level.api.MaidLevelApi;
import com.laixia.maidintelligence.feature.level.domain.DefaultLevelCurve;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public final class LevelGuiHandler {
    private final MaidLevelApi<EntityMaid> levelApi;

    public LevelGuiHandler(MaidLevelApi<EntityMaid> levelApi) {
        this.levelApi = levelApi;
    }

    @SubscribeEvent
    public void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        if (!(Minecraft.getInstance().screen instanceof AbstractMaidContainerGui<?> gui)
                || !isMaidDetailsTooltip(event)) {
            return;
        }

        EntityMaid maid = gui.getMaid();
        LevelProgress progress = levelApi.getProgress(maid);
        int required = DefaultLevelCurve.INSTANCE.experienceRequiredForNextLevel(progress.level());
        int insertIndex = Math.min(1, event.getTooltipElements().size());

        event.getTooltipElements().add(insertIndex++, Either.left(infoLine(
                levelTooltip("level"),
                Component.literal(String.valueOf(progress.level()))
        )));
        event.getTooltipElements().add(insertIndex++, Either.left(infoLine(
                levelTooltip("experience"),
                required > 0
                        ? Component.literal(progress.experience() + "/" + required)
                        : Component.translatable(levelTooltip("max_level"))
        )));
        event.getTooltipElements().add(insertIndex, Either.left(infoLine(
                levelTooltip("favorability_level"),
                Component.literal(String.valueOf(maid.getFavorabilityManager().getLevel()))
        )));
    }

    private static boolean isMaidDetailsTooltip(RenderTooltipEvent.GatherComponents event) {
        String expectedTitle = Component.translatable("tooltips.touhou_little_maid.info.title").getString();
        return event.getTooltipElements().stream()
                .map(element -> element.left())
                .flatMap(java.util.Optional::stream)
                .anyMatch(text -> text.getString().contains(expectedTitle));
    }

    private static MutableComponent infoLine(String labelKey, Component value) {
        return Component.literal("█ ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.translatable(labelKey).withStyle(ChatFormatting.WHITE))
                .append(": ")
                .append(value.copy().withStyle(ChatFormatting.AQUA));
    }

    private static String levelTooltip(String path) {
        return ModResources.translationKey("tooltips", "info." + path);
    }
}
