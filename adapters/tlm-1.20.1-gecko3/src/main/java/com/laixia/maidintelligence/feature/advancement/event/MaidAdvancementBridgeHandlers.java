package com.laixia.maidintelligence.feature.advancement.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAfterEatEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidBackpackChangeEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidBaubleChangeEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidEquipEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidFavorabilityLevelChangeEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidFishedEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidPickupEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import com.laixia.maidintelligence.feature.advancement.api.MaidCombatAdvancementTriggers;
import com.laixia.maidintelligence.feature.advancement.api.MaidProgressAdvancementTriggers;
import com.laixia.maidintelligence.feature.advancement.api.MaidWorldAdvancementTriggers;
import com.laixia.maidintelligence.feature.advancement.port.MaidLevelQueryPort;
import com.laixia.maidintelligence.feature.advancement.server.MaidBridgeMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * 把女仆的行为喂进原版触发器：物品栏、战斗、位置与维度、药水效果、进食、
 * 使用物品、钓鱼、坠落、合成与繁殖。
 * <p>
 * 除了事件驱动，还按 20 tick 做一次定时对账：TLM 的工作任务会直接改背包而不发事件，
 * 药水效果的 Forge 事件也可能早于实际生效，工具磨损与所处方块更是压根没有事件，
 * 靠指纹比对兜底。
 * <p>
 * 放方块与蜜块下滑没有可用事件，走 {@code EntityMaidPlaceBlockMixin} 与
 * {@code HoneyBlockSlideMixin}；村民交易女仆做不到，不桥接。
 */
public final class MaidAdvancementBridgeHandlers {
    private static final int SCAN_INTERVAL = 20;
    private static final int LOCATION_PHASE = 0;
    private static final int SNAPSHOT_PHASE = 10;
    private static final int STANDING_PHASE = 5;
    private static final int USING_ITEM_INTERVAL = 5;
    private static final float MIN_REPORTED_FALL = 3.0F;

    private final MaidBridgeMemory memory;
    private final MaidWorldAdvancementTriggers worldTriggers;
    private final MaidCombatAdvancementTriggers combatTriggers;
    private final MaidProgressAdvancementTriggers progressTriggers;
    private final MaidLevelQueryPort<EntityMaid> levelQuery;
    /** {@code LivingHurtEvent} 到 {@code LivingDamageEvent} 之间记一下减伤前的伤害值。 */
    private int pendingHurtVictim = -1;
    private float pendingHurtAmount;

    public MaidAdvancementBridgeHandlers(
            MaidBridgeMemory memory,
            MaidWorldAdvancementTriggers worldTriggers,
            MaidCombatAdvancementTriggers combatTriggers,
            MaidProgressAdvancementTriggers progressTriggers,
            MaidLevelQueryPort<EntityMaid> levelQuery
    ) {
        this.memory = memory;
        this.worldTriggers = worldTriggers;
        this.combatTriggers = combatTriggers;
        this.progressTriggers = progressTriggers;
        this.levelQuery = levelQuery;
    }

    @SubscribeEvent
    public void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) {
            return;
        }
        // 按实体错开相位，几十只女仆时不会挤在同一 tick 全量扫描。
        int phase = Math.floorMod(maid.tickCount + maid.getId(), SCAN_INTERVAL);
        if (phase == LOCATION_PHASE) {
            worldTriggers.location(maid);
            return;
        }
        if (phase == STANDING_PHASE) {
            // 女仆自己那条根进度也挂在等级触发器上，所以定时重放同时兼作「我是女仆」的判定。
            progressTriggers.replaceStanding(
                    maid,
                    levelQuery.currentLevel(maid)
            );
            return;
        }
        if (phase != SNAPSHOT_PHASE) {
            return;
        }
        if (memory.inventoryChanged(maid)) {
            worldTriggers.inventoryChanged(maid);
        }
        if (memory.effectsChanged(maid)) {
            worldTriggers.effectsChanged(maid, null);
        }
        memory.toolWearChanged(maid).ifPresent(damage ->
                worldTriggers.toolDurabilityChanged(
                        maid,
                        maid.getMainHandItem(),
                        damage
                ));
        reportOccupiedBlock(maid);
    }

    /**
     * 所处方块。原版对玩家是每 tick 扫整个碰撞箱的，女仆这边只看脚下与视线两格、
     * 跟着定时对账走，方块没换就不重复上报。
     */
    private void reportOccupiedBlock(EntityMaid maid) {
        BlockState feet = maid.level().getBlockState(maid.blockPosition());
        BlockState eyes = maid.level().getBlockState(BlockPos.containing(maid.getEyePosition()));
        if (!memory.enteredNewBlock(maid, blockPrint(feet, eyes))) {
            return;
        }
        if (!feet.isAir()) {
            worldTriggers.enteredBlock(maid, feet);
        }
        if (!eyes.isAir() && !eyes.equals(feet)) {
            worldTriggers.enteredBlock(maid, eyes);
        }
    }

    private static int blockPrint(BlockState feet, BlockState eyes) {
        // 方块状态是单例，用身份哈希就够区分「换了一种方块」。
        return 31 * System.identityHashCode(feet) + System.identityHashCode(eyes);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPickup(MaidPickupEvent.ItemResultPost event) {
        inventoryChanged(event.getMaid());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEquip(MaidEquipEvent event) {
        inventoryChanged(event.getMaid(), event.getStack());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBackpackPutOn(MaidBackpackChangeEvent.PutOn event) {
        inventoryChanged(event.getMaid(), event.getItemStack());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBackpackTakeOff(MaidBackpackChangeEvent.TakeOff event) {
        inventoryChanged(event.getMaid(), event.getItemStack());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBaublePutOn(MaidBaubleChangeEvent.PutOn event) {
        inventoryChanged(event.getMaid(), event.getBaubleItem());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBaubleTakeOff(MaidBaubleChangeEvent.TakeOff event) {
        inventoryChanged(event.getMaid(), event.getBaubleItem());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurt(LivingHurtEvent event) {
        pendingHurtVictim = event.getEntity().getId();
        pendingHurtAmount = event.getAmount();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        float taken = event.getAmount();
        float dealt = pendingHurtVictim == event.getEntity().getId() ? pendingHurtAmount : taken;
        pendingHurtVictim = -1;
        DamageSource source = event.getSource();

        if (event.getEntity() instanceof EntityMaid victim) {
            combatTriggers.hurtByEntity(
                    victim,
                    source,
                    dealt,
                    taken,
                    victim.isBlocking()
            );
        }
        if (source.getEntity() instanceof EntityMaid attacker && attacker != event.getEntity()) {
            combatTriggers.hurtEntity(
                    attacker,
                    event.getEntity(),
                    source,
                    dealt,
                    taken,
                    event.getEntity().isBlocking()
            );
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        Entity killer = source.getEntity();
        if (event.getEntity() instanceof EntityMaid victim && killer != null) {
            combatTriggers.killedByEntity(victim, killer, source);
        }
        if (killer instanceof EntityMaid attacker && attacker != event.getEntity()) {
            combatTriggers.killedEntity(
                    attacker,
                    event.getEntity(),
                    source
            );
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            worldTriggers.effectsChanged(maid, event.getEffectSource());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEffectRemoved(MobEffectEvent.Remove event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            worldTriggers.effectsChanged(maid, null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEffectExpired(MobEffectEvent.Expired event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            worldTriggers.effectsChanged(maid, null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            worldTriggers.consumedItem(maid, event.getItem());
        }
    }

    /**
     * {@code using_item} 原版是每 tick 判定的，这里降到每 5 tick：
     * 判定只看手里拿着什么，慢一点不会漏。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide()) {
            return;
        }
        if (event.getDuration() % USING_ITEM_INTERVAL == 0) {
            worldTriggers.usingItem(maid, event.getItem());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFished(MaidFishedEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid == null || maid.level().isClientSide()) {
            return;
        }
        ItemStack rod = maid.getMainHandItem();
        if (!rod.is(Items.FISHING_ROD)) {
            rod = maid.getOffhandItem();
        }
        worldTriggers.fishingRodHooked(
                maid,
                rod,
                event.getHook().position(),
                event.getDrops()
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFall(LivingFallEvent event) {
        // 下个台阶也算一次坠落，判定用的又都是几十上百格的落差，所以先滤掉碎步。
        if (!(event.getEntity() instanceof EntityMaid maid)
                || maid.level().isClientSide()
                || event.getDistance() < MIN_REPORTED_FALL) {
            return;
        }
        // 原版记的是起跳点，女仆这边没人记，按落点加落差还原一个等效高度。
        Vec3 start = maid.position().add(0.0D, event.getDistance(), 0.0D);
        worldTriggers.fellFromHeight(maid, start);
    }

    /**
     * TLM 的女仆不会自己合成，唯一的女仆合成路径是工作台背包——那是女仆的工作台、
     * 用的是女仆背包里的材料，所以把这一次合成也记到女仆名下。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof AbstractMaidContainer container)
                || !(event.getInventory() instanceof CraftingContainer grid)) {
            return;
        }
        EntityMaid maid = container.getMaid();
        if (maid == null) {
            return;
        }
        // 这个事件在扣材料之前发，配方还能从格子里反查出来。
        player.level().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, grid, player.level())
                .ifPresent(recipe -> worldTriggers.recipeCrafted(
                        maid,
                        recipe.getId(),
                        grid.getItems()
                ));
    }

    /**
     * 幼崽出生才算繁殖成功。撮合是哪只女仆做的由 mixin 记在
     * {@link MaidBridgeMemory} 里，这里认领。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBabySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getParentA() instanceof Animal parent) || !(event.getParentB() instanceof Animal partner)) {
            return;
        }
        UUID matchmaker = memory.takeCourtedAnimal(parent.getId())
                .or(() -> memory.takeCourtedAnimal(partner.getId()))
                .orElse(null);
        if (matchmaker == null || !(parent.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getEntity(matchmaker) instanceof EntityMaid maid) {
            worldTriggers.bredAnimals(
                    maid,
                    parent,
                    partner,
                    event.getChild()
            );
        }
    }

    /**
     * 走 TLM 自动进食路径时没有 use item 事件，只能拿吃完剩下的东西反推。
     * 只在剩下的还是食物时用（例如一叠蛋糕吃掉一个），碗、瓶这类容器直接忽略。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAfterEat(MaidAfterEatEvent event) {
        ItemStack rest = event.getFoodAfterEat();
        if (!rest.isEmpty() && rest.isEdible()) {
            worldTriggers.consumedItem(event.getMaid(), rest);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFavorabilityLevelChange(MaidFavorabilityLevelChangeEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide() || event.getNewLevel() <= 0) {
            return;
        }
        progressTriggers.favorabilityLevel(maid, event.getNewLevel());
    }

    /**
     * 与 {@code LevelExperienceHandler} 使用同一组过滤条件，累计经验才和实际入账一致。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExperienceOrbPickup(MaidPickupEvent.ExperienceResult event) {
        EntityMaid maid = event.getMaid();
        ExperienceOrb orb = event.getExperienceOrb();
        if (maid.level().isClientSide() || !orb.isAlive() || orb.tickCount <= 2 || orb.value <= 0) {
            return;
        }
        progressTriggers.experienceGained(maid, orb.value);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid
                && !event.getLevel().isClientSide()
                && event.getLevel().dimension().equals(Level.NETHER)) {
            memory.rememberNetherEntry(maid);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide()) {
            return;
        }
        ResourceKey<Level> from = maid.level().dimension();
        ResourceKey<Level> to = event.getDimension();
        worldTriggers.changedDimension(maid, from, to);
        if (from.equals(Level.NETHER) && to.equals(Level.OVERWORLD)) {
            memory.takeNetherEntry(maid.getUUID())
                    .ifPresent(entry ->
                            worldTriggers.netherTravel(maid, entry));
        }
    }

    private void inventoryChanged(EntityMaid maid, ItemStack changed) {
        if (maid.level().isClientSide()) {
            return;
        }
        worldTriggers.inventoryChanged(maid, changed);
        memory.refreshInventory(maid);
    }

    private void inventoryChanged(EntityMaid maid) {
        if (maid.level().isClientSide()) {
            return;
        }
        worldTriggers.inventoryChanged(maid);
        memory.refreshInventory(maid);
    }
}
