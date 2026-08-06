package com.laixia.maidintelligence.platform.resource;

import net.minecraft.resources.ResourceLocation;

/**
 * 公共命名空间和翻译键工厂。具体资源仍由所属特性管理。
 */
public final class ModResources {
    public static final String MOD_ID = "tlm_companionship";

    private ModResources() {
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static String translationKey(String category, String path) {
        return category + "." + MOD_ID + "." + path;
    }

    /**
     * 把配置文件收进以 MOD ID 命名的子目录。
     *
     * <p>Forge 按此相对路径解析，并在首次写入时创建父目录；因此各配置只需要
     * 声明自己的文件名，目录归属由这里统一决定。
     */
    public static String configPath(String fileName) {
        return MOD_ID + "/" + fileName;
    }
}
