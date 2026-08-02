package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.domain.DefaultToolDurabilityPolicy;
import com.laixia.maidintelligence.feature.status.domain.MaidStatusState;
import com.laixia.maidintelligence.feature.status.service.ToolReplacementResult;
import com.laixia.maidintelligence.feature.status.service.ToolReplacementService;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class StatusFeedbackGameTests {
    private StatusFeedbackGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void statusDataSurvivesEntitySaveAndLoad(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        statusApi().setHunger(maid, 23);
        MaidStatusState expected = statusApi().getState(maid);

        CompoundTag saved = new CompoundTag();
        maid.saveWithoutId(saved);

        EntityMaid loaded = InitEntities.MAID.get().create(helper.getLevel());
        helper.assertTrue(loaded != null, "Failed to create maid for status reload verification");
        loaded.load(saved);

        MaidStatusState actual = statusApi().getState(loaded);
        helper.assertTrue(
                actual.equals(expected),
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

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void fullInventoryPickupShowsOneWarning(GameTestHelper helper) {
        EntityMaid maid = spawnMaid(helper);
        maid.setTame(true);
        maid.setPickup(true);
        var inventory = maid.getAvailableInv(false);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(
                    slot,
                    new ItemStack(
                            Items.COBBLESTONE,
                            inventory.getSlotLimit(slot)
                    )
            );
        }

        ItemEntity item = new ItemEntity(
                helper.getLevel(),
                maid.getX() + 1.0D,
                maid.getY(),
                maid.getZ(),
                new ItemStack(Items.DIAMOND)
        );
        item.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(item);

        helper.assertTrue(
                !maid.pickupItem(item, true),
                "Full inventory unexpectedly accepted the pickup simulation"
        );
        maid.pickupItem(item, true);
        helper.assertTrue(
                textBubbleCount(maid) == 1,
                "Full inventory warning was missing or bypassed its debounce"
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty", timeoutTicks = 80)
    public static void hungerRegenerationContinuesWhileEatingAndRestartsAfterNewDamage(
            GameTestHelper helper
    ) {
        EntityMaid maid = spawnMaid(helper);
        statusApi().setHunger(maid, 100);
        maid.setHealth(1.0F);
        maid.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
        maid.startUsingItem(InteractionHand.MAIN_HAND);

        helper.runAfterDelay(15, () -> {
            MaidStatusState firstCycle = statusApi().getState(maid);
            helper.assertTrue(
                    firstCycle.hunger() == 99
                            && Math.abs(firstCycle.saturation() - 20.0F) < 1.0E-4F,
                    "Parallel hunger and saturation regeneration failed while eating: "
                            + firstCycle
            );
            helper.assertTrue(maid.isUsingItem(), "Maid stopped eating before the concurrent check");
            helper.assertTrue(maid.getHealth() > 1.0F, "Regeneration did not heal while eating");

            maid.stopUsingItem();
            maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            maid.setHealth(maid.getMaxHealth());
            statusApi().setHunger(maid, 100);
        });

        helper.runAfterDelay(20, () -> maid.setHealth(1.0F));

        helper.runAfterDelay(35, () -> {
            MaidStatusState secondCycle = statusApi().getState(maid);
            helper.assertTrue(
                    secondCycle.hunger() == 99
                            && Math.abs(secondCycle.saturation() - 15.0F) < 1.0E-4F,
                    "Parallel regeneration did not restart after new damage: " + secondCycle
            );
            helper.assertTrue(maid.getHealth() > 1.0F, "Second regeneration cycle did not heal");
            helper.succeed();
        });
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

    private static int textBubbleCount(EntityMaid maid) {
        int count = 0;
        var iterator = maid.getChatBubbleManager()
                .getChatBubbleDataCollection()
                .iterator();
        while (iterator.hasNext()) {
            if (TextChatBubbleData.ID.equals(iterator.next().id())) {
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private static MaidStatusApi<EntityMaid> statusApi() {
        return (MaidStatusApi<EntityMaid>) AdapterRuntime.require(MaidStatusApi.class);
    }
}
