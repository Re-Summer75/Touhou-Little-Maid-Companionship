package com.laixia.maidintelligence;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidProgressAdvancementTriggers;
import com.laixia.maidintelligence.feature.advancement.event.MaidAdvancementExperienceRewardedEvent;
import com.laixia.maidintelligence.feature.interaction.event.MaidFedEvent;
import com.laixia.maidintelligence.feature.level.api.ExperienceSource;
import com.laixia.maidintelligence.feature.level.api.MaidLevelApi;
import com.laixia.maidintelligence.feature.level.event.MaidLevelChangedEvent;
import com.laixia.maidintelligence.feature.level.network.LevelNetwork;
import com.laixia.maidintelligence.kernel.event.DomainEventBus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Subscribes the cross-feature reactions that features publish events for.
 *
 * <p>Lifted out of {@link MaidIntelligence} once that file reached the source
 * layout limit. It belongs outside anyway: the composition root's job is to
 * build services and hand them to Forge, while this is a fixed set of
 * feature-to-feature reactions that does not vary with how anything is wired.
 */
final class DomainEventWiring {
    private DomainEventWiring() {
    }

    static void wire(
            DomainEventBus events,
            MaidProgressAdvancementTriggers advancement,
            MaidLevelApi<EntityMaid> levels
    ) {
        events.subscribe(MaidLevelChangedEvent.class, event -> {
            if (!(event.subject() instanceof EntityMaid maid)) {
                throw new IllegalStateException(
                        "Level event subject is not a TLM maid: "
                                + event.subject()
                );
            }
            if (maid.getOwner() instanceof ServerPlayer owner) {
                LevelNetwork.sendLevelUp(
                        owner,
                        maid.getId(),
                        event.oldLevel(),
                        event.newLevel()
                );
            }
            advancement.level(maid, event.newLevel());
        });
        events.subscribe(
                maidFedEventType(),
                event -> advancement.fed(
                        event.subject(),
                        event.item()
                )
        );
        events.subscribe(
                advancementExperienceEventType(),
                event -> levels.awardExperience(
                        event.subject(),
                        event.points(),
                        ExperienceSource.ADVANCEMENT
                )
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<MaidFedEvent<EntityMaid, ItemStack>>
    maidFedEventType() {
        return (Class) MaidFedEvent.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<MaidAdvancementExperienceRewardedEvent<EntityMaid>>
    advancementExperienceEventType() {
        return (Class) MaidAdvancementExperienceRewardedEvent.class;
    }
}
