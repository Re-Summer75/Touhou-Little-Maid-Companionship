package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidAdvancementAccess;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidProgressAdvancementTriggers;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidWorldAdvancementTriggers;
import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementManager;
import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementSnapshot;
import com.laixia.maidintelligence.feature.advancement.server.MaidMirrorPlayer;
import com.laixia.maidintelligence.feature.advancement.server.MirrorPlayerCacheBridge;
import com.laixia.maidintelligence.feature.level.api.MaidLevelApi;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 女仆进度系统的端到端验证：原版进度真的会判定给女仆、自定义阈值走统计量、
 * 进度能跨存档往返、奖励经验落在女仆身上、完成只结算一次、本体自己那套进度不算女仆的。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdvancementGameTests {
    private static final ResourceLocation STORY_ROOT =
            ResourceLocation.fromNamespaceAndPath("minecraft", "story/root");
    private static final ResourceLocation MAID_ROOT = ModResources.id("maid/root");
    private static final ResourceLocation ACQUAINTED = ModResources.id("maid/acquainted");
    private static final ResourceLocation SWEET_TOOTH = ModResources.id("maid/sweet_tooth");
    private static final ResourceLocation REGULAR_MEALS = ModResources.id("maid/regular_meals");
    private static final ResourceLocation CRAFT_GOHEI =
            ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "base/craft_gohei");
    private static final ResourceLocation GOHEI_RECIPE =
            ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "hakurei_gohei");
    private static final int REGULAR_MEALS_GOAL = 32;
    private static final int SWEET_TOOTH_GOAL = 8;

    private AdvancementGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void maidEarnsVanillaAdvancement(GameTestHelper helper) {
        EntityMaid maid = ownedMaid(helper);
        ItemStack craftingTable = new ItemStack(Items.CRAFTING_TABLE);
        maid.getMaidInv().setStackInSlot(0, craftingTable);
        worldTriggers().inventoryChanged(maid, craftingTable);

        helper.assertTrue(
                isDone(helper, maid, STORY_ROOT),
                "A maid carrying a crafting table should earn minecraft:story/root"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void mirrorProjectionIsSideEffectFreeAndIsolated(GameTestHelper helper) {
        EntityMaid first = ownedMaid(helper);
        EntityMaid second = ownedMaid(helper);
        first.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200));

        MirrorIsolationProbe probe = new MirrorIsolationProbe(Set.of(
                first.getUUID(),
                second.getUUID()
        ));
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            advancementManager().fire(first, probe::captureFirst);
            first.removeEffect(MobEffects.REGENERATION);
            advancementManager().fire(first, probe::captureRepeated);
            advancementManager().fire(second, probe::captureSecond);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
        }

        helper.assertTrue(
                probe.mirrorEffectEvents == 0,
                "Projecting effects into a mirror must not publish Forge gameplay events"
        );
        helper.assertTrue(probe.copiedEffect, "The mirror should still expose copied effects");
        helper.assertTrue(probe.clearedEffect, "Removed maid effects must leave the mirror");
        helper.assertTrue(
                probe.firstMirror != null && probe.firstMirror == probe.repeatedMirror,
                "Repeated dispatches for one maid should reuse its mirror"
        );
        helper.assertTrue(
                probe.secondMirror != null && probe.firstMirror != probe.secondMirror,
                "Different maids must not share capability-bearing mirror players"
        );
        helper.assertTrue(
                !probe.firstMirror.getUUID().equals(probe.secondMirror.getUUID()),
                "Every maid mirror should have an isolated game profile"
        );
        helper.assertTrue(
                probe.networkAvailable && probe.packetFailure == null,
                "The mirror should safely discard listener and raw-connection packets"
        );
        helper.assertTrue(
                probe.cacheDetached,
                "Mirror profiles must not remain in vanilla player caches"
        );
        helper.assertTrue(
                probe.trackerInstalled,
                "The ServerPlayer field must reuse the maid advancement tracker"
        );
        helper.assertTrue(
                probe.firstMirror.boundMaid() == null
                        && probe.secondMirror.boundMaid() == null,
                "Dispatch sessions must restore mirror bindings"
        );

        // Third-party code may recreate the vanilla cache through PlayerList.
        // Release must remove that entry without stopping the maid tracker early.
        probe.firstMirror.server.getPlayerList()
                .getPlayerAdvancements(probe.firstMirror);
        probe.firstMirror.server.getPlayerList()
                .getPlayerStats(probe.firstMirror);
        helper.assertFalse(
                MirrorPlayerCacheBridge.isDetached(probe.firstMirror),
                "The defensive release fixture should recreate a vanilla cache entry"
        );
        advancementManager().release(first.getUUID());
        helper.assertTrue(
                MirrorPlayerCacheBridge.isDetached(probe.firstMirror),
                "Releasing a mirror must evict recreated vanilla cache entries"
        );
        advancementManager().release(second.getUUID());
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 200)
    public static void dimensionReplacementKeepsTrackersDetached(GameTestHelper helper) {
        EntityMaid maid = ownedMaid(helper);
        MaidMirrorPlayer[] mirrors = new MaidMirrorPlayer[2];
        advancementManager().fire(maid, mirror -> mirrors[0] = mirror);

        ServerLevel target = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (target == null) {
            throw new AssertionError("The GameTest server has no nether level");
        }
        EntityMaid moved = InitEntities.MAID.get().create(target);
        if (moved == null) {
            throw new AssertionError("The destination maid could not be created");
        }
        moved.setUUID(maid.getUUID());
        moved.setOwnerUUID(maid.getOwnerUUID());
        moved.moveTo(maid.getX(), maid.getY(), maid.getZ());
        advancementManager().fire(moved, mirror -> mirrors[1] = mirror);

        helper.assertTrue(
                mirrors[0] != null && mirrors[1] != null && mirrors[0] != mirrors[1],
                "Changing dimension must replace the level-bound mirror"
        );
        helper.assertTrue(
                mirrors[0].serverLevel() != mirrors[1].serverLevel(),
                "The replacement mirror must belong to the destination level"
        );
        helper.assertTrue(
                MirrorPlayerCacheBridge.isDetached(mirrors[0])
                        && MirrorPlayerCacheBridge.isDetached(mirrors[1]),
                "Neither side of a dimension replacement may remain cached"
        );
        helper.assertTrue(
                MirrorPlayerCacheBridge.usesAdvancements(
                        mirrors[1],
                        mirrors[1].getAdvancements()
                ),
                "The replacement mirror must reuse the reloaded maid tracker"
        );

        advancementManager().release(moved.getUUID());
        helper.assertTrue(
                MirrorPlayerCacheBridge.isDetached(mirrors[1]),
                "The destination mirror must stay detached after release"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void maidRootComesFromOwnLevel(GameTestHelper helper) {
        EntityMaid maid = ownedMaid(helper);
        progressTriggers().replaceStanding(
                maid,
                levelApi().getProgress(maid).level()
        );

        helper.assertTrue(
                isDone(helper, maid, MAID_ROOT),
                "Every maid should hold the maid root advancement"
        );
        // 界面只画服务端说可见的条目，一进世界就得有这一格，否则我们那一栏压根不会出现。
        List<ResourceLocation> visible = visibleIds(helper, maid);
        helper.assertTrue(
                visible.contains(MAID_ROOT),
                "A fresh maid should already see its own root advancement"
        );
        helper.assertTrue(
                visible.contains(ACQUAINTED),
                "Completing the root should reveal what comes next"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void statisticsDriveThresholdAdvancements(GameTestHelper helper) {
        EntityMaid maid = ownedMaid(helper);
        for (int index = 0; index < SWEET_TOOTH_GOAL - 1; index++) {
            progressTriggers().fed(maid, new ItemStack(Items.CAKE));
        }
        helper.assertFalse(
                isDone(helper, maid, SWEET_TOOTH),
                "Seven cakes must not be enough for the sweet tooth advancement"
        );

        progressTriggers().fed(maid, new ItemStack(Items.CAKE));
        helper.assertTrue(
                isDone(helper, maid, SWEET_TOOTH),
                "The eighth cake should complete the sweet tooth advancement"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void progressSurvivesUnloadAndReload(GameTestHelper helper) {
        EntityMaid maid = ownedMaid(helper);
        ItemStack craftingTable = new ItemStack(Items.CRAFTING_TABLE);
        maid.getMaidInv().setStackInSlot(0, craftingTable);
        worldTriggers().inventoryChanged(maid, craftingTable);

        // 卸载即落盘释放，再取一次就得走读档路径。
        advancements().release(maid.getUUID());
        helper.assertTrue(
                isDone(helper, maid, STORY_ROOT),
                "Advancement progress should survive unloading the maid"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void rewardExperienceGoesToTheMaidExactlyOnce(GameTestHelper helper) {
        EntityMaid maid = ownedMaid(helper);
        LevelProgress before = levelApi().getProgress(maid);
        for (int index = 0; index < REGULAR_MEALS_GOAL; index++) {
            progressTriggers().fed(maid, new ItemStack(Items.BREAD));
        }
        helper.assertTrue(
                isDone(helper, maid, REGULAR_MEALS),
                "Thirty-two meals should complete the regular meals advancement"
        );

        LevelProgress rewarded = levelApi().getProgress(maid);
        helper.assertFalse(
                rewarded.equals(before),
                "The advancement reward experience should land on the maid: " + rewarded
        );

        Date firstCompletion = requireProgressOf(helper, maid, REGULAR_MEALS).getFirstProgressDate();
        progressTriggers().fed(maid, new ItemStack(Items.BREAD));
        helper.assertTrue(
                levelApi().getProgress(maid).equals(rewarded),
                "A completed advancement must not pay out its reward again"
        );
        helper.assertTrue(
                firstCompletion.equals(requireProgressOf(helper, maid, REGULAR_MEALS).getFirstProgressDate()),
                "A completed advancement must not be awarded (and announced) twice"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void touhouLittleMaidAdvancementsStayWithThePlayer(GameTestHelper helper) {
        EntityMaid maid = ownedMaid(helper);
        LevelProgress before = levelApi().getProgress(maid);
        // 本体的「制作御币」用的是原版 recipe_crafted，还给 50 点经验，不排除就会真判给女仆。
        worldTriggers().recipeCrafted(
                maid,
                GOHEI_RECIPE,
                List.of(new ItemStack(Items.STICK))
        );

        helper.assertTrue(
                progressOf(helper, maid, CRAFT_GOHEI).isEmpty(),
                "The mod's own player advancements must not reach the maid advancement screen"
        );
        helper.assertTrue(
                levelApi().getProgress(maid).equals(before),
                "An excluded advancement must not pay its reward to the maid"
        );
        helper.succeed();
    }

    private static EntityMaid ownedMaid(GameTestHelper helper) {
        EntityMaid maid = helper.spawn(InitEntities.MAID.get(), new BlockPos(1, 2, 1));
        // 进度只跟着有主的女仆走，测试里给一个稳定的假主人。
        maid.setOwnerUUID(UUID.nameUUIDFromBytes("tlm_companionship:gametest_owner".getBytes()));
        return maid;
    }

    /** 一条都没沾边的进度压根不会出现在同步表里，那自然也就不算完成。 */
    private static boolean isDone(GameTestHelper helper, EntityMaid maid, ResourceLocation advancement) {
        return progressOf(helper, maid, advancement)
                .map(AdvancementProgress::isDone)
                .orElse(false);
    }

    private static AdvancementProgress requireProgressOf(
            GameTestHelper helper,
            EntityMaid maid,
            ResourceLocation advancement
    ) {
        return progressOf(helper, maid, advancement)
                .orElseThrow(() -> new AssertionError("No progress recorded for " + advancement));
    }

    private static Optional<AdvancementProgress> progressOf(
            GameTestHelper helper,
            EntityMaid maid,
            ResourceLocation advancement
    ) {
        return Optional.ofNullable(snapshotOf(helper, maid).progress().get(advancement));
    }

    private static List<ResourceLocation> visibleIds(GameTestHelper helper, EntityMaid maid) {
        return snapshotOf(helper, maid).visible().stream().map(Advancement::getId).toList();
    }

    private static MaidAdvancementSnapshot snapshotOf(GameTestHelper helper, EntityMaid maid) {
        MinecraftServer server = helper.getLevel().getServer();
        return advancements()
                .snapshot(maid, server.getAdvancements())
                .orElseThrow(() -> new AssertionError("The maid has no advancement tracker"));
    }

    private static MaidWorldAdvancementTriggers worldTriggers() {
        return AdapterRuntime.require(MaidWorldAdvancementTriggers.class);
    }

    private static MaidProgressAdvancementTriggers progressTriggers() {
        return AdapterRuntime.require(
                MaidProgressAdvancementTriggers.class
        );
    }

    private static MaidAdvancementAccess advancements() {
        return AdapterRuntime.require(MaidAdvancementAccess.class);
    }

    private static MaidAdvancementManager advancementManager() {
        MaidAdvancementAccess access = advancements();
        if (access instanceof MaidAdvancementManager manager) {
            return manager;
        }
        throw new IllegalStateException("Advancement access is not backed by its server manager");
    }

    @SuppressWarnings("unchecked")
    private static MaidLevelApi<EntityMaid> levelApi() {
        return (MaidLevelApi<EntityMaid>) AdapterRuntime.require(MaidLevelApi.class);
    }

    private static final class MirrorIsolationProbe {
        private final Set<UUID> maidIds;
        private int mirrorEffectEvents;
        private MaidMirrorPlayer firstMirror;
        private MaidMirrorPlayer repeatedMirror;
        private MaidMirrorPlayer secondMirror;
        private PlayerAdvancements firstAdvancements;
        private boolean copiedEffect;
        private boolean clearedEffect;
        private boolean cacheDetached;
        private boolean trackerInstalled;
        private boolean networkAvailable;
        private RuntimeException packetFailure;

        private MirrorIsolationProbe(Set<UUID> maidIds) {
            this.maidIds = maidIds;
        }

        @SubscribeEvent
        public void onMirrorEffect(MobEffectEvent event) {
            if (event.getEntity() instanceof MaidMirrorPlayer mirror
                    && mirror.boundMaid() != null
                    && maidIds.contains(mirror.boundMaid().getUUID())) {
                mirrorEffectEvents++;
            }
        }

        private void captureFirst(MaidMirrorPlayer mirror) {
            firstMirror = mirror;
            firstAdvancements = mirror.getAdvancements();
            copiedEffect = mirror.hasEffect(MobEffects.REGENERATION);
            cacheDetached = MirrorPlayerCacheBridge.isDetached(mirror);
            trackerInstalled = MirrorPlayerCacheBridge.usesAdvancements(
                    mirror,
                    firstAdvancements
            );
            networkAvailable = mirror.connection != null
                    && mirror.connection.connection != null;
            if (!networkAvailable) {
                return;
            }
            try {
                ClientboundSetHealthPacket packet =
                        new ClientboundSetHealthPacket(20.0F, 20, 5.0F);
                mirror.connection.send(packet);
                mirror.connection.connection.send(packet);
            } catch (RuntimeException exception) {
                packetFailure = exception;
            }
        }

        private void captureRepeated(MaidMirrorPlayer mirror) {
            repeatedMirror = mirror;
            clearedEffect = !mirror.hasEffect(MobEffects.REGENERATION);
            cacheDetached &= MirrorPlayerCacheBridge.isDetached(mirror);
            trackerInstalled &= mirror.getAdvancements() == firstAdvancements
                    && MirrorPlayerCacheBridge.usesAdvancements(
                    mirror,
                    firstAdvancements
            );
        }

        private void captureSecond(MaidMirrorPlayer mirror) {
            secondMirror = mirror;
            cacheDetached &= MirrorPlayerCacheBridge.isDetached(mirror);
            trackerInstalled &= MirrorPlayerCacheBridge.usesAdvancements(
                    mirror,
                    mirror.getAdvancements()
            );
        }
    }
}
