package com.laixia.maidintelligence.gametest.care;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.feature.level.api.MaidLevelApi;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LevelGameTests {
    private LevelGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void levelDataSurvivesEntitySaveAndLoad(GameTestHelper helper) {
        EntityMaid maid = helper.spawn(InitEntities.MAID.get(), new BlockPos(1, 2, 1));
        levelApi().setProgress(maid, 8, 37);

        CompoundTag saved = new CompoundTag();
        maid.saveWithoutId(saved);

        EntityMaid loaded = InitEntities.MAID.get().create(helper.getLevel());
        helper.assertTrue(loaded != null, "Failed to create maid for reload verification");
        loaded.load(saved);

        LevelProgress actual = levelApi().getProgress(loaded);
        helper.assertTrue(
                actual.equals(new LevelProgress(8, 37)),
                "Level progress did not survive entity NBT save/load: " + actual
        );
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static MaidLevelApi<EntityMaid> levelApi() {
        return (MaidLevelApi<EntityMaid>) AdapterRuntime.require(MaidLevelApi.class);
    }
}
