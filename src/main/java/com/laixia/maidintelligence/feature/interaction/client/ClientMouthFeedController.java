package com.laixia.maidintelligence.feature.interaction.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.platform.network.ModNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientMouthFeedController {
    private ClientMouthFeedController() {
    }

    public static void tryFeed(EntityMaid maid) {
        DynamicMaidFaceTracker.trace(maid)
                .ifPresent(hit -> ModNetwork.sendMouthFeed(
                        maid,
                        hit.u(),
                        hit.v(),
                        hit.worldCenter(),
                        hit.worldNormal()
                ));
    }
}
