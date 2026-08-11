package com.laixia.maidintelligence.feature.advancement.handler;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.bridge.MaidFeatAdvancementTriggers;
import com.laixia.maidintelligence.feature.advancement.server.MaidBridgeMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 把「原版对任何实体都执行、却只给玩家记账」的那些事喂进判定。
 * <p>
 * 与 {@link MaidAdvancementBridgeHandlers} 分开，是因为它们的共同点不是主题而是形状：
 * 下面每一件事的机制本身都写在 {@code Entity} 或 {@code LivingEntity} 上、对她本来就
 * 生效——她真的在浮空、真的被雷劈、箭真的打中了标靶——被 {@code instanceof ServerPlayer}
 * 挡住的只有记账那一行。补的不是新能力，是她早就挣到的战绩。
 * <p>
 * 一律走 `manager.fire`（由 criteria 实现负责），事件全部挂在**真女仆**身上，
 * 不替镜像发布任何 Forge 事件——这三条是 0.0.3 那次镜像崩溃留下的红线。
 */
public final class MaidFeatBridgeHandlers {
    /** 雷劈判定要报周围有谁，原版对玩家取的也是这个半径。 */
    private static final double LIGHTNING_NEARBY = 8.0D;

    /** 幽匿催发体的感应半径，与原版 `SculkCatalystBlock` 的搜索一致。 */
    private static final int CATALYST_RADIUS = 8;

    private final MaidBridgeMemory memory;
    private final MaidFeatAdvancementTriggers triggers;

    public MaidFeatBridgeHandlers(
            MaidBridgeMemory memory,
            MaidFeatAdvancementTriggers triggers
    ) {
        this.memory = memory;
        this.triggers = triggers;
    }

    /**
     * {@code levitation}：她被弹上去，又落回来。
     *
     * <p>原版对玩家是在 tick 里比对「效果开始时的位置」和现在的位置，这里照做：
     * 效果出现时记下起点，效果消失那一刻把这段位移报上去。潜影贝的弹射对她本来
     * 就生效，缺的只是这一笔。
     */
    @SubscribeEvent
    public void onMaidTick(MaidTickEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide()) {
            return;
        }
        reportLavaRide(maid);
        if (maid.getEffect(MobEffects.LEVITATION) == null) {
            memory.endLevitation(maid.getUUID());
            return;
        }
        MaidBridgeMemory.Levitation flight = memory.beginLevitation(maid);
        triggers.levitated(
                maid, flight.from(), maid.tickCount - flight.startTick()
        );
    }

    /**
     * { ride_entity_in_lava}：她骑着什么在岩浆里走。
     *
     * <p>与浮空同一形状——判定是 { DistanceTrigger}，比的是起点到当前位置，
     * 所以骑行期间每 tick 重报一次，下来或离开岩浆就把这一趟丢掉。
     */
    private void reportLavaRide(EntityMaid maid) {
        Entity vehicle = maid.getVehicle();
        if (vehicle == null || !vehicle.isInLava()) {
            memory.endLavaRide(maid.getUUID());
            return;
        }
        triggers.rodeInLava(maid, memory.beginLavaRide(maid));
    }

    /** {@code lightning_strike}：雷落在她旁边。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onStruckByLightning(EntityStruckByLightningEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)
                || maid.level().isClientSide()
                || event.isCanceled()) {
            return;
        }
        LightningBolt bolt = event.getLightning();
        List<Entity> nearby = new ArrayList<>(maid.level().getEntities(
                bolt,
                bolt.getBoundingBox().inflate(LIGHTNING_NEARBY),
                other -> other != maid
        ));
        triggers.struckByLightning(maid, bolt, nearby);
    }

    /**
     * {@code target_hit}：她的弹射物打中了标靶。
     *
     * <p>不用 mixin：{@link ProjectileImpactEvent} 在原版处理命中之前就发了出来，
     * 而标靶那套判定（红石信号强度按距靶心多近算）全在方块里，原版只在最后一步
     * 把非玩家的射手挡掉。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide()
                || !(projectile.getOwner() instanceof EntityMaid maid)) {
            return;
        }
        if (event.getRayTraceResult() instanceof EntityHitResult struck) {
            reportChannelling(maid, projectile, struck.getEntity());
            return;
        }
        if (!(event.getRayTraceResult() instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos at = hit.getBlockPos();
        BlockState state = projectile.level().getBlockState(at);
        if (!state.is(Blocks.TARGET)) {
            return;
        }
        triggers.hitTarget(maid, projectile, hit.getLocation(), signalOf(hit));
    }

    /**
     * {@code channeled_lightning}：她的引雷三叉戟把雷叫到了目标头上。
     *
     * <p>报在命中的这一刻，而不是等雷真的落下来——原版把雷叫下来时会
     * {@code setCause(entity instanceof ServerPlayer ? ... : null)}，也就是说女仆
     * 扔的三叉戟召出来的雷**根本不带来源**，落地之后再想认领已经无从对应。所以
     * 认领必须发生在还知道是谁扔的时候。
     *
     * <p>条件与原版召雷一致：附了引雷、正在雷暴、目标能看见天空。三者齐了雷必落，
     * 所以这一刻报账和雷落之后报账是同一件事。
     *
     * <p>「附了引雷」是在**投掷那一刻**记下的，不是从飞行中的三叉戟上读的：
     * {@code ThrownTrident#getPickupItem} 是 protected，为读一个附魔去开 mixin
     * 不划算，而她松手的那一刻手上还拿着它。
     */
    private void reportChannelling(
            EntityMaid maid,
            Projectile projectile,
            Entity struck
    ) {
        if (!(projectile instanceof ThrownTrident)
                || !memory.takeChannellingThrow(maid.getUUID())) {
            return;
        }
        Level level = struck.level();
        if (!level.isThundering()
                || !level.canSeeSky(struck.blockPosition())) {
            return;
        }
        triggers.channeledLightning(maid, List.of(struck));
    }

    /** 她松手扔出三叉戟：记下这一支带不带引雷。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemUseStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof EntityMaid maid)
                || maid.level().isClientSide()
                || !(event.getItem().getItem() instanceof TridentItem)) {
            return;
        }
        memory.rememberChannellingThrow(maid, EnchantmentHelper
                .getItemEnchantmentLevel(
                        Enchantments.CHANNELING, event.getItem()) > 0);
    }

    /**
     * 红石信号强度按命中点离靶心多近算，与原版 {@code TargetBlock#getRedstoneStrength}
     * 同一算法：靶面上离中心越近越强，最强 15。
     */
    private static int signalOf(BlockHitResult hit) {
        Direction.Axis axis = hit.getDirection().getAxis();
        Vec3 point = hit.getLocation();
        BlockPos at = hit.getBlockPos();
        double across = axis == Direction.Axis.Y
                ? Math.abs(point.z - at.getZ() - 0.5D)
                : Math.abs(point.y - at.getY() - 0.5D);
        double along = axis == Direction.Axis.X
                ? Math.abs(point.z - at.getZ() - 0.5D)
                : Math.abs(point.x - at.getX() - 0.5D);
        double off = Math.max(across, along);
        return Math.max(1, Mth.ceil(15.0D - off * 30.0D));
    }

    /** {@code kill_mob_near_sculk_catalyst}：她在催发体眼皮底下杀了东西。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        Entity victim = event.getEntity();
        if (victim.level().isClientSide()
                || !(event.getSource().getEntity() instanceof EntityMaid maid)
                || maid == victim
                || !nearCatalyst(victim)) {
            return;
        }
        triggers.killedNearSculkCatalyst(maid, victim, event.getSource());
    }

    private static boolean nearCatalyst(Entity victim) {
        BlockPos centre = victim.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                centre.offset(-CATALYST_RADIUS, -CATALYST_RADIUS, -CATALYST_RADIUS),
                centre.offset(CATALYST_RADIUS, CATALYST_RADIUS, CATALYST_RADIUS)
        )) {
            if (victim.level().getBlockState(pos).is(Blocks.SCULK_CATALYST)) {
                return true;
            }
        }
        return false;
    }

    /** {@code started_riding}：她坐上了什么。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMount(EntityMountEvent event) {
        if (event.isMounting()
                && !event.getLevel().isClientSide()
                && event.getEntityMounting() instanceof EntityMaid maid) {
            triggers.startedRiding(maid);
        }
    }

}
