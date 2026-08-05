package com.laixia.maidintelligence.feature.orchestration.insight;

import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import com.laixia.maidintelligence.platform.item.SoulLensItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.List;
import java.util.Objects;

/**
 * Pushes maid insight to whoever is holding a soul lens, and to nobody else.
 *
 * <p>Gated on the held item rather than on a toggle, so a server with no lens
 * in anyone's hand does no work at all for this feature. That matters more than
 * it looks: building an insight walks a maid's whole candidate list, and doing
 * it for every maid on a busy server every tick would be a real cost for a
 * diagnostic almost nobody is looking at.
 */
public final class InsightForgeInstaller implements ForgeFeatureInstaller {
    /**
     * Ticks between pushes. Twenty is slow enough to be cheap and fast enough
     * that a panel still feels attached to what the maid is doing; the client
     * cache outlives it by a comfortable margin so nothing flickers between
     * updates.
     */
    private static final int INTERVAL_TICKS = 20;

    private static final double RADIUS = 24.0D;

    /**
     * Cap per player per push. A player standing in a maid farm should not be
     * able to turn one held item into an unbounded packet burst.
     */
    private static final int MAX_MAIDS = 12;

    private final MaidInsightSource source;
    private boolean installed;
    private int tick;

    public InsightForgeInstaller(MaidInsightSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public void install(ForgeLifecycle lifecycle) {
        if (installed) {
            return;
        }
        installed = true;
        lifecycle.gameEventBus().addListener(this::onServerTick);
        lifecycle.gameEventBus().addListener(this::onServerStopping);
        // The panel only exists on a client, and RenderLivingEvent is not a
        // class a dedicated server may load at all.
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> lifecycle.gameEventBus().addListener(
                        MaidInsightRenderer::onRenderLiving
                )
        );
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tick < INTERVAL_TICKS) {
            return;
        }
        tick = 0;
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SoulLensItem.isHeldBy(player)) {
                continue;
            }
            broadcastTo(player);
        }
    }

    private void broadcastTo(ServerPlayer player) {
        List<Entity> maids = source.maidsNear(player, RADIUS);
        int sent = 0;
        for (Entity maid : maids) {
            if (sent++ >= MAX_MAIDS) {
                break;
            }
            InsightNetwork.sendInsight(
                    player,
                    maid.getId(),
                    source.insightFor(maid)
            );
        }
    }

    /**
     * A single-player world stops without a disconnect, so the counter is reset
     * here rather than left to carry into the next world.
     */
    private void onServerStopping(ServerStoppingEvent event) {
        tick = 0;
    }
}
