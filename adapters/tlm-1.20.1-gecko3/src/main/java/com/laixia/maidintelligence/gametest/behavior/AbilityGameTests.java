package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.laixia.maidintelligence.feature.behavior.application.ability.DefaultMaidAbilityService;
import com.laixia.maidintelligence.feature.behavior.application.ability.MutableAbilityCatalog;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationSource;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityCatalog;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityDefinition;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityGrantSet;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityTemplate;
import com.laixia.maidintelligence.feature.behavior.domain.ability.CompanionAbilityIds;
import com.laixia.maidintelligence.feature.behavior.port.AbilityGrantPort;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.tlm.TlmMaidIntentActions;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class AbilityGameTests {
    private AbilityGameTests() {
    }

    @GameTest(batch = "ability", templateNamespace = "minecraft", template = "empty")
    public static void deployBoatConsumesOnceAfterSuccessfulSpawn(
            GameTestHelper helper
    ) {
        EntityMaid maid = helper.spawn(
                InitEntities.MAID.get(),
                new BlockPos(1, 2, 1)
        );
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.WATER);
        maid.getAvailableInv(false).setStackInSlot(
                0,
                new ItemStack(Items.OAK_BOAT)
        );
        Fixture fixture = fixture(maid, helper.getLevel().getGameTime());
        Map<String, String> parameters = Map.of(
                "ability_id",
                CompanionAbilityIds.DEPLOY_BOAT.toString()
        );
        ActionResult result = fixture.actions().execute(
                maid,
                CompanionIntentIds.DEPLOY_BOAT,
                parameters,
                helper.getLevel().getGameTime(),
                0
        );
        helper.assertTrue(result == ActionResult.SUCCEEDED,
                "Boat ability did not commit");
        helper.assertTrue(
                maid.getAvailableInv(false).getStackInSlot(0).isEmpty(),
                "Boat item was not consumed after entity creation"
        );
        helper.assertTrue(nearbyBoats(helper, maid) == 1,
                "Boat ability did not create exactly one boat");

        ActionResult duplicate = fixture.actions().execute(
                maid,
                CompanionIntentIds.DEPLOY_BOAT,
                parameters,
                helper.getLevel().getGameTime() + 1L,
                1
        );
        helper.assertTrue(
                duplicate == ActionResult.FAILED
                        && nearbyBoats(helper, maid) == 1,
                "Completed request created a duplicate boat"
        );
        helper.succeed();
    }

    @GameTest(batch = "ability", templateNamespace = "minecraft", template = "empty")
    public static void deployBoatReusesNearbyEmptyBoat(
            GameTestHelper helper
    ) {
        EntityMaid maid = helper.spawn(
                InitEntities.MAID.get(),
                new BlockPos(1, 2, 1)
        );
        maid.getAvailableInv(false).setStackInSlot(
                0,
                new ItemStack(Items.OAK_BOAT)
        );
        Boat existing = new Boat(
                helper.getLevel(),
                maid.getX() + 2.0D,
                maid.getY(),
                maid.getZ()
        );
        helper.getLevel().addFreshEntity(existing);
        Fixture fixture = fixture(maid, helper.getLevel().getGameTime());

        helper.assertTrue(fixture.actions().execute(
                        maid,
                        CompanionIntentIds.DEPLOY_BOAT,
                        Map.of(
                                "ability_id",
                                CompanionAbilityIds.DEPLOY_BOAT.toString()
                        ),
                        helper.getLevel().getGameTime(),
                        0
                ) == ActionResult.SUCCEEDED,
                "Nearby empty boat was not reused");
        helper.assertTrue(
                maid.getAvailableInv(false).getStackInSlot(0)
                        .getCount() == 1,
                "Reusing a boat consumed an inventory item"
        );
        helper.assertTrue(nearbyBoats(helper, maid) == 1,
                "Reusing an empty boat spawned another boat");
        helper.succeed();
    }

    private static Fixture fixture(EntityMaid maid, long gameTime) {
        MutableAbilityCatalog catalog = new MutableAbilityCatalog();
        catalog.publish(AbilityCatalog.compile(
                1L,
                List.of(definition())
        ));
        var abilities = new DefaultMaidAbilityService<>(
                catalog,
                new MemoryGrantPort(),
                (subject, signal, tick, ttl) -> true
        );
        if (!abilities.grant(
                maid,
                CompanionAbilityIds.DEPLOY_BOAT,
                gameTime,
                "gametest"
        ) || abilities.request(
                maid,
                CompanionAbilityIds.DEPLOY_BOAT,
                AbilityActivationSource.COMMAND,
                gameTime
        ).isEmpty()) {
            throw new AssertionError("Could not prepare ability request");
        }
        return new Fixture(
                new TlmMaidIntentActions(
                        ignored -> {
                        },
                        new MaidSnackCabinetMealSource(
                                new MaidMealAccess()
                        ),
                        abilities
                )
        );
    }

    private static AbilityDefinition definition() {
        return new AbilityDefinition(
                CompanionAbilityIds.DEPLOY_BOAT,
                AbilityTemplate.WORLD_ITEM_DEPLOY,
                CompanionIntentIds.DEPLOY_BOAT,
                Map.of(),
                40,
                100,
                200,
                900.0D,
                120.0D,
                500
        );
    }

    private static int nearbyBoats(
            GameTestHelper helper,
            EntityMaid maid
    ) {
        AABB bounds = maid.getBoundingBox().inflate(8.0D);
        return helper.getLevel().getEntitiesOfClass(
                Boat.class,
                bounds
        ).size();
    }

    private record Fixture(TlmMaidIntentActions actions) {
    }

    private static final class MemoryGrantPort
            implements AbilityGrantPort<EntityMaid> {
        private final Map<EntityMaid, AbilityGrantSet> values =
                new WeakHashMap<>();

        @Override
        public AbilityGrantSet load(EntityMaid subject) {
            return values.getOrDefault(subject, AbilityGrantSet.empty());
        }

        @Override
        public void save(EntityMaid subject, AbilityGrantSet grants) {
            values.put(subject, grants);
        }
    }
}
