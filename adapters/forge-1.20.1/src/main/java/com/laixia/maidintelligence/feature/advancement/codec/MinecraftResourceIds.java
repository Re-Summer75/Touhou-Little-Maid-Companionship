package com.laixia.maidintelligence.feature.advancement.codec;

import com.laixia.maidintelligence.feature.advancement.domain.ItemId;
import com.laixia.maidintelligence.feature.advancement.domain.ResourceId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Objects;

/**
 * Keeps Minecraft registry identifiers outside the reusable advancement core.
 */
public final class MinecraftResourceIds {
    private MinecraftResourceIds() {
    }

    public static ResourceId toCore(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return new ResourceId(id.getNamespace(), id.getPath());
    }

    public static ItemId toCore(Item item) {
        return new ItemId(toCore(
                BuiltInRegistries.ITEM.getKey(
                        Objects.requireNonNull(item, "item")
                )
        ));
    }

    public static ResourceLocation toMinecraft(ResourceId id) {
        Objects.requireNonNull(id, "id");
        return ResourceLocation.fromNamespaceAndPath(
                id.namespace(),
                id.path()
        );
    }

    public static ResourceLocation toMinecraft(ItemId id) {
        return toMinecraft(
                Objects.requireNonNull(id, "id").resource()
        );
    }
}
