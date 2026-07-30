package com.laixia.maidintelligence.feature.interaction.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.interaction.network.InteractionNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientMouthFeedController {
    private ClientMouthFeedController() {
    }

    public static void tryFeed(EntityMaid maid) {
        DynamicMaidFaceTracker.trace(maid)
                .ifPresent(hit -> InteractionNetwork.sendMouthFeed(
                        maid.getId(),
                        hit.u(),
                        hit.v(),
                        hit.worldCenter(),
                        hit.worldNormal()
                ));
    }
}
