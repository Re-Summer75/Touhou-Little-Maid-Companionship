package com.laixia.maidintelligence.feature.advancement.menu;

import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge registry for the maid advancement page container.
 */
public final class AdvancementMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(
                    ForgeRegistries.MENU_TYPES,
                    ModResources.MOD_ID
            );

    public static final RegistryObject<MenuType<AbstractContainerMenu>>
            ADVANCEMENT_PAGE = MENUS.register(
            "advancement_page",
            () -> IForgeMenuType.create(
                    (windowId, inventory, data) ->
                            AdapterRuntime.require(
                                    AdvancementMenuFactory.class
                            ).create(windowId, inventory, data)
            )
    );

    private AdvancementMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
