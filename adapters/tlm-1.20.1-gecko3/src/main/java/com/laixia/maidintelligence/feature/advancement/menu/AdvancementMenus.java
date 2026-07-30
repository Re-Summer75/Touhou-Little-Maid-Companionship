package com.laixia.maidintelligence.feature.advancement.menu;

import com.laixia.maidintelligence.MaidIntelligence;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 进度页容器的注册表。
 */
public final class AdvancementMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MaidIntelligence.MOD_ID);

    public static final RegistryObject<MenuType<MaidAdvancementContainer>> ADVANCEMENT_PAGE = MENUS.register(
            "advancement_page",
            () -> IForgeMenuType.create(
                    (windowId, inventory, data) -> new MaidAdvancementContainer(windowId, inventory, data.readInt())
            )
    );

    private AdvancementMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
