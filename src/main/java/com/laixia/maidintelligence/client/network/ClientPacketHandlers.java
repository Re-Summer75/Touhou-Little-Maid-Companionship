package com.laixia.maidintelligence.client.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.interaction.client.MaidEatingParticleEffect;
import com.laixia.maidintelligence.platform.network.packet.ClientboundLevelUpPacket;
import com.laixia.maidintelligence.platform.network.packet.ClientboundMaidEatingParticlesPacket;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void handleLevelUp(ClientboundLevelUpPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(packet.maidEntityId());
        if (entity instanceof EntityMaid maid) {
            minecraft.player.displayClientMessage(Component.translatable(
                    ModResources.translationKey("message", "level_up"),
                    maid.getDisplayName(),
                    packet.oldLevel(),
                    packet.newLevel()
            ), false);
        }
    }

    public static void handleMaidEatingParticles(
            ClientboundMaidEatingParticlesPacket packet
    ) {
        if (packet.useTrackedFace()) {
            MaidEatingParticleEffect.spawnFromTrackedFace(
                    packet.maidEntityId(),
                    packet.food()
            );
            return;
        }
        MaidEatingParticleEffect.spawn(
                packet.food(),
                packet.worldCenter(),
                packet.worldNormal()
        );
    }
}
