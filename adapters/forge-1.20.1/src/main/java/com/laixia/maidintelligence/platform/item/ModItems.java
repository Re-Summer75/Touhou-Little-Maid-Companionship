package com.laixia.maidintelligence.platform.item;

import com.laixia.maidintelligence.feature.physics.client.PhysicsDebugStick;
import com.laixia.maidintelligence.platform.forge.ForgeFeatureInstaller;
import com.laixia.maidintelligence.platform.forge.ForgeLifecycle;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's items and the creative tab that holds them.
 *
 * <p>Only the soul lens is a registered item. The skeleton debug stick stays a
 * tagged vanilla stick — a creative tab accepts any stack, not just registered
 * items, so it can sit on the shelf beside the lens without gaining a model, a
 * texture, or an entry in the item registry it never needed.
 */
public final class ModItems implements ForgeFeatureInstaller {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(
                    ForgeRegistries.ITEMS,
                    ModResources.MOD_ID
            );

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(
                    Registries.CREATIVE_MODE_TAB,
                    ModResources.MOD_ID
            );

    public static final RegistryObject<Item> SOUL_LENS =
            ITEMS.register("soul_lens", SoulLensItem::new);

    private static final RegistryObject<CreativeModeTab> TAB = TABS.register(
            "companionship",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable(
                            ModResources.translationKey(
                                    "itemGroup",
                                    "companionship"
                            )
                    ))
                    .icon(() -> new ItemStack(SOUL_LENS.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(new ItemStack(SOUL_LENS.get()));
                        output.accept(PhysicsDebugStick.create());
                    })
                    .build()
    );

    @Override
    public void install(ForgeLifecycle lifecycle) {
        ITEMS.register(lifecycle.modEventBus());
        TABS.register(lifecycle.modEventBus());
    }

    /**
     * Referenced so the tab holder is initialized with the rest of the class;
     * a deferred registry entry that nothing reads would never be created.
     */
    static RegistryObject<CreativeModeTab> tab() {
        return TAB;
    }
}
