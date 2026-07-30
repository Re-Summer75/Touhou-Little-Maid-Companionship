package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.MaidIntelligence;
import com.laixia.maidintelligence.feature.advancement.MaidAdvancementFeature;
import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementSnapshot;
import com.laixia.maidintelligence.feature.advancement.server.MaidAdvancementTracker;
import com.laixia.maidintelligence.feature.advancement.server.MaidCriteria;
import com.laixia.maidintelligence.feature.level.LevelFeature;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 女仆进度系统的端到端验证：原版进度真的会判定给女仆、自定义阈值走统计量、
 * 进度能跨存档往返、奖励经验落在女仆身上、完成只结算一次、本体自己那套进度不算女仆的。
 */
@GameTestHolder(MaidIntelligence.MOD_ID)
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
        MaidCriteria.inventoryChanged(maid, craftingTable);

        helper.assertTrue(
                isDone(helper, maid, STORY_ROOT),
                "A maid carrying a crafting table should earn minecraft:story/root"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void maidRootComesFromOwnLevel(GameTestHelper helper) {
        EntityMaid maid = ownedMaid(helper);
        MaidCriteria.replaceStanding(maid, LevelFeature.INSTANCE.api().getProgress(maid).level());

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
            MaidCriteria.fed(maid, new ItemStack(Items.CAKE));
        }
        helper.assertFalse(
                isDone(helper, maid, SWEET_TOOTH),
                "Seven cakes must not be enough for the sweet tooth advancement"
        );

        MaidCriteria.fed(maid, new ItemStack(Items.CAKE));
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
        MaidCriteria.inventoryChanged(maid, craftingTable);

        // 卸载即落盘释放，再取一次就得走读档路径。
        MaidAdvancementFeature.INSTANCE.manager().release(maid.getUUID());
        helper.assertTrue(
                isDone(helper, maid, STORY_ROOT),
                "Advancement progress should survive unloading the maid"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void rewardExperienceGoesToTheMaidExactlyOnce(GameTestHelper helper) {
        EntityMaid maid = ownedMaid(helper);
        LevelProgress before = LevelFeature.INSTANCE.api().getProgress(maid);
        for (int index = 0; index < REGULAR_MEALS_GOAL; index++) {
            MaidCriteria.fed(maid, new ItemStack(Items.BREAD));
        }
        helper.assertTrue(
                isDone(helper, maid, REGULAR_MEALS),
                "Thirty-two meals should complete the regular meals advancement"
        );

        LevelProgress rewarded = LevelFeature.INSTANCE.api().getProgress(maid);
        helper.assertFalse(
                rewarded.equals(before),
                "The advancement reward experience should land on the maid: " + rewarded
        );

        Date firstCompletion = requireProgressOf(helper, maid, REGULAR_MEALS).getFirstProgressDate();
        MaidCriteria.fed(maid, new ItemStack(Items.BREAD));
        helper.assertTrue(
                LevelFeature.INSTANCE.api().getProgress(maid).equals(rewarded),
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
        LevelProgress before = LevelFeature.INSTANCE.api().getProgress(maid);
        // 本体的「制作御币」用的是原版 recipe_crafted，还给 50 点经验，不排除就会真判给女仆。
        MaidCriteria.recipeCrafted(maid, GOHEI_RECIPE, List.of(new ItemStack(Items.STICK)));

        helper.assertTrue(
                progressOf(helper, maid, CRAFT_GOHEI).isEmpty(),
                "The mod's own player advancements must not reach the maid advancement screen"
        );
        helper.assertTrue(
                LevelFeature.INSTANCE.api().getProgress(maid).equals(before),
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
        MaidAdvancementTracker tracker = MaidAdvancementFeature.INSTANCE.manager()
                .tracker(maid)
                .orElseThrow(() -> new AssertionError("The maid has no advancement tracker"));
        return tracker.snapshot(server.getAdvancements());
    }
}
