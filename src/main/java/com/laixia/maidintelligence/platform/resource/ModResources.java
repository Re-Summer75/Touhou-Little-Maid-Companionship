package com.laixia.maidintelligence.platform.resource;

import com.laixia.maidintelligence.MaidIntelligence;
import net.minecraft.resources.ResourceLocation;

/**
 * 公共命名空间和翻译键工厂。具体资源仍由所属特性管理。
 */
public final class ModResources {
    private ModResources() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MaidIntelligence.MOD_ID, path);
    }

    public static String translationKey(String category, String path) {
        return category + "." + MaidIntelligence.MOD_ID + "." + path;
    }
}
