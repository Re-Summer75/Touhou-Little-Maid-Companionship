package com.laixia.maidintelligence.gametest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.MaidIntelligence;
import com.laixia.maidintelligence.feature.level.LevelFeature;
import com.laixia.maidintelligence.feature.level.domain.LevelProgress;
import com.laixia.maidintelligence.feature.level.tlm.LevelTaskData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MaidIntelligence.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LevelGameTests {
    private LevelGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void levelDataSurvivesEntitySaveAndLoad(GameTestHelper helper) {
        EntityMaid maid = helper.spawn(InitEntities.MAID.get(), new BlockPos(1, 2, 1));
        LevelFeature.INSTANCE.api().setProgress(maid, 8, 37);

        CompoundTag saved = new CompoundTag();
        maid.saveWithoutId(saved);

        EntityMaid loaded = InitEntities.MAID.get().create(helper.getLevel());
        helper.assertTrue(loaded != null, "Failed to create maid for reload verification");
        loaded.load(saved);

        LevelProgress actual = LevelFeature.INSTANCE.api().getProgress(loaded);
        helper.assertTrue(
                actual.equals(new LevelProgress(8, 37)),
                "Level progress did not survive entity NBT save/load: " + actual
        );
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "empty")
    public static void legacyLevelDataMigratesToNewModId(GameTestHelper helper) {
        EntityMaid maid = helper.spawn(InitEntities.MAID.get(), new BlockPos(1, 2, 1));
        LevelProgress expected = new LevelProgress(6, 23);
        maid.setData(LevelTaskData.legacyProgressKey(), expected);

        CompoundTag saved = new CompoundTag();
        maid.saveWithoutId(saved);

        EntityMaid loaded = InitEntities.MAID.get().create(helper.getLevel());
        helper.assertTrue(loaded != null, "Failed to create maid for legacy level migration");
        loaded.load(saved);

        LevelProgress actual = LevelFeature.INSTANCE.api().getProgress(loaded);
        helper.assertTrue(
                expected.equals(actual),
                "Legacy level progress was not read after the MOD ID migration: " + actual
        );
        helper.assertTrue(
                expected.equals(loaded.getData(LevelTaskData.progressKey())),
                "Legacy level progress was not copied to the new task data key"
        );
        helper.succeed();
    }
}
