package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.MaidIntelligence;
import com.laixia.maidintelligence.feature.status.StatusFeedbackFeature;
import com.laixia.maidintelligence.feature.status.domain.DefaultToolDurabilityPolicy;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;
import com.laixia.maidintelligence.feature.status.service.ToolReplacementResult;
import com.laixia.maidintelligence.feature.status.service.ToolReplacementService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MaidIntelligence.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StatusFeedbackGameTests {
    private StatusFeedbackGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void statusDataSurvivesEntitySaveAndLoad(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        StatusFeedbackFeature.INSTANCE.api().setHunger(maid, 23);

        CompoundTag saved = new CompoundTag();
        maid.saveWithoutId(saved);

        EntityMaid loaded = InitEntities.MAID.get().create(helper.getLevel());
        helper.assertTrue(loaded != null, "Failed to create maid for status reload verification");
        loaded.load(saved);

        MaidStatusState actual = StatusFeedbackFeature.INSTANCE.api().getState(loaded);
        helper.assertTrue(
                actual.equals(new MaidStatusState(23)),
                "Status data did not survive entity NBT save/load: " + actual
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void lowDurabilityToolUsesHealthyBackup(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        ItemStack worn = lowDurabilityPickaxe();
        ItemStack backup = new ItemStack(Items.IRON_PICKAXE);
        maid.setItemInHand(InteractionHand.MAIN_HAND, worn);
        maid.getAvailableBackpackInv().setStackInSlot(0, backup);

        ToolReplacementResult result = toolService().inspectAndReplace(maid);

        helper.assertTrue(result.replaced(), "Low-durability tool was not replaced");
        helper.assertTrue(
                maid.getMainHandItem().is(Items.IRON_PICKAXE)
                        && maid.getMainHandItem().getDamageValue() == 0,
                "Healthy backup was not equipped"
        );
        ItemStack storedWornTool = maid.getAvailableBackpackInv().getStackInSlot(0);
        helper.assertTrue(
                storedWornTool.is(Items.IRON_PICKAXE)
                        && storedWornTool.getDamageValue() == worn.getDamageValue(),
                "Worn tool was not returned to the replacement slot"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void lowDurabilityToolIsKeptWithoutBackup(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        ItemStack worn = lowDurabilityPickaxe();
        int expectedDamage = worn.getDamageValue();
        maid.setItemInHand(InteractionHand.MAIN_HAND, worn);

        ToolReplacementResult result = toolService().inspectAndReplace(maid);

        helper.assertTrue(result.lowDurability(), "Low-durability tool was not detected");
        helper.assertTrue(!result.replaced(), "Tool was unexpectedly replaced without a backup");
        helper.assertTrue(
                maid.getMainHandItem().is(Items.IRON_PICKAXE)
                        && maid.getMainHandItem().getDamageValue() == expectedDamage,
                "Tool changed or disappeared when no backup was available"
        );
        helper.succeed();
    }

    private static EntityMaid spawnMaid(GameTestHelper helper) {
        return helper.spawn(InitEntities.MAID.get(), new BlockPos(1, 2, 1));
    }

    private static ItemStack lowDurabilityPickaxe() {
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        stack.setDamageValue(stack.getMaxDamage() - 5);
        return stack;
    }

    private static ToolReplacementService toolService() {
        return new ToolReplacementService(DefaultToolDurabilityPolicy.INSTANCE);
    }
}
