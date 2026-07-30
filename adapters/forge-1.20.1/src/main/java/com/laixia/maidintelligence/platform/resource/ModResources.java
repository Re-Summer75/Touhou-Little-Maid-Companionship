package com.laixia.maidintelligence.platform.resource;

import net.minecraft.resources.ResourceLocation;

/**
 * 公共命名空间和翻译键工厂。具体资源仍由所属特性管理。
 */
public final class ModResources {
    public static final String MOD_ID = "tlm_companionship";
    public static final String LEGACY_MOD_ID = "maid_intelligence";

    private ModResources() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static ResourceLocation legacyId(String path) {
        return ResourceLocation.fromNamespaceAndPath(LEGACY_MOD_ID, path);
    }

    public static String translationKey(String category, String path) {
        return category + "." + MOD_ID + "." + path;
    }
}
