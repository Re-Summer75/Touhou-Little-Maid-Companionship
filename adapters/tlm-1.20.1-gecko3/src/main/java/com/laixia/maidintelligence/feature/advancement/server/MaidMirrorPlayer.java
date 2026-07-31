package com.laixia.maidintelligence.feature.advancement.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.advancement.port.MaidExperienceRewardPort;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 女仆的镜像玩家：原版所有 criteria 触发器都只接受 {@link ServerPlayer}，且分发是按
 * {@code player.getAdvancements()} 找监听者的，所以要让女仆用上原版进度系统，就得有一个
 * 状态与女仆同步的玩家替身。
 * <p>
 * 它与女仆一一对应，不加入世界、不进玩家列表、不 tick，只在触发器调用期间绑定女仆：
 * <ul>
 *     <li>{@link #getAdvancements()} 返回当前女仆自己的进度，原版分发因此落到该女仆名下；</li>
 *     <li>{@link #getDisplayName()} 指向女仆，原版 {@code PlayerAdvancements#award} 的聊天广播
 *     就自然变成女仆的名字；</li>
 *     <li>经验与战利品奖励转交女仆，配方奖励直接丢弃。</li>
 * </ul>
 * 注意不能继承 Forge 的 {@code FakePlayer}：Forge 在 {@code PlayerAdvancements#award} 开头
 * 对假玩家直接返回，会把所有进度静默吞掉。
 */
public final class MaidMirrorPlayer extends ServerPlayer {
    private static final String PROFILE_NAME = "MaidAdvancementMirror";
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };
    private static final int MAIN_SLOTS = 36;

    private EntityMaid maid;
    private PlayerAdvancements maidAdvancements;
    @Nullable
    private PlayerAdvancements bootstrapAdvancements;
    private final MaidExperienceRewardPort<EntityMaid> experienceRewards;

    private MaidMirrorPlayer(
            MinecraftServer server,
            ServerLevel level,
            GameProfile profile,
            MaidExperienceRewardPort<EntityMaid> experienceRewards
    ) {
        super(server, level, profile);
        this.experienceRewards = experienceRewards;
        this.connection = new DiscardingPacketListener(server, this);
        this.bootstrapAdvancements =
                MirrorPlayerCacheBridge.detachBootstrap(this);
    }

    static MaidMirrorPlayer create(
            ServerLevel level,
            UUID maidId,
            MaidExperienceRewardPort<EntityMaid> experienceRewards
    ) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            throw new IllegalStateException("Maid mirror player requires a server level");
        }
        String seed = "tlm_companionship:maid_mirror/" + maidId;
        UUID id = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        if (server.getPlayerList().getPlayer(id) != null) {
            throw new IllegalStateException(
                    "Maid mirror profile collides with an online player: " + id
            );
        }
        return new MaidMirrorPlayer(
                server,
                level,
                new GameProfile(id, PROFILE_NAME),
                experienceRewards
        );
    }

    /**
     * 绑定女仆并同步状态。返回上一次的绑定，便于嵌套调用后恢复。
     */
    Binding bind(EntityMaid maid, PlayerAdvancements advancements) {
        Binding previous = new Binding(this.maid, this.maidAdvancements);
        this.maid = maid;
        this.maidAdvancements = advancements;
        try {
            syncFrom(maid);
            return previous;
        } catch (RuntimeException | Error failure) {
            restore(previous);
            throw failure;
        }
    }

    void bindAdvancements(PlayerAdvancements advancements) {
        MirrorPlayerCacheBridge.installAdvancements(
                this,
                bootstrapAdvancements,
                advancements
        );
        bootstrapAdvancements = null;
        this.maidAdvancements = advancements;
    }

    void restore(Binding binding) {
        this.maid = binding.maid();
        this.maidAdvancements = binding.advancements();
    }

    /** 释放原版缓存与第三方 Capability；女仆 Tracker 由管理器单独管理。 */
    void dispose() {
        MirrorPlayerCacheBridge.release(this);
        maid = null;
        maidAdvancements = null;
        bootstrapAdvancements = null;
        invalidateCaps();
    }

    private void syncFrom(EntityMaid maid) {
        moveTo(maid.getX(), maid.getY(), maid.getZ(), maid.getYRot(), maid.getXRot());
        setDeltaMovement(maid.getDeltaMovement());
        setOnGround(maid.onGround());
        setHealth(Math.min(getMaxHealth(), maid.getHealth()));
        setRemainingFireTicks(maid.getRemainingFireTicks());
        setTicksFrozen(maid.getTicksFrozen());
        setAirSupply(maid.getAirSupply());
        setPose(maid.getPose());
        setSwimming(maid.isSwimming());
        setSprinting(false);
        setShiftKeyDown(false);
        fallDistance = maid.fallDistance;
        invulnerableTime = 0;
        copyEffects(maid);
        copyInventory(maid);
    }

    private void copyEffects(EntityMaid maid) {
        // 这里只投影判定状态；行为接口会发布 Forge 效果事件，把离线镜像暴露给第三方模组。
        Map<MobEffect, MobEffectInstance> effects = getActiveEffectsMap();
        effects.clear();
        for (MobEffectInstance effect : maid.getActiveEffects()) {
            effects.put(effect.getEffect(), new MobEffectInstance(effect));
        }
    }

    /**
     * 把女仆的手持、盔甲、背包与饰品栏铺进镜像玩家自己的物品栏，
     * {@code inventory_changed} 这类判定才能看到女仆真正拿着什么。
     */
    private void copyInventory(EntityMaid maid) {
        Inventory inventory = getInventory();
        inventory.clearContent();
        inventory.selected = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            inventory.armor.set(slot.getIndex(), maid.getItemBySlot(slot).copy());
        }
        inventory.offhand.set(0, maid.getOffhandItem().copy());

        int next = 0;
        ItemStack mainHand = maid.getMainHandItem();
        if (!mainHand.isEmpty()) {
            inventory.setItem(next++, mainHand.copy());
        }
        next = copyHandler(inventory, maid.getMaidInv(), next);
        copyHandler(inventory, maid.getMaidBauble(), next);
    }

    private int copyHandler(Inventory inventory, IItemHandler handler, int firstSlot) {
        int next = firstSlot;
        for (int slot = 0; slot < handler.getSlots() && next < MAIN_SLOTS; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                inventory.setItem(next++, stack.copy());
            }
        }
        return next;
    }

    @Override
    public PlayerAdvancements getAdvancements() {
        return maidAdvancements != null ? maidAdvancements : super.getAdvancements();
    }

    @Override
    public Component getDisplayName() {
        return maid != null ? maid.getDisplayName() : super.getDisplayName();
    }

    @Override
    public void giveExperiencePoints(int points) {
        if (maid != null && points > 0) {
            experienceRewards.reward(maid, points);
        }
    }

    @Override
    public void giveExperienceLevels(int levels) {
        // 进度奖励里的经验一律按点数转交女仆，等级奖励对女仆没有对应语义。
    }

    @Override
    public boolean addItem(ItemStack stack) {
        if (maid == null || stack.isEmpty()) {
            return false;
        }
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(
                maid.getAvailableBackpackInv(),
                stack.copy(),
                false
        );
        if (remainder.isEmpty()) {
            stack.setCount(0);
            return true;
        }
        // 背包塞不下时保留剩余数量，交回原版逻辑掉在女仆脚下。
        stack.setCount(remainder.getCount());
        return false;
    }

    @Override
    public void awardRecipesByKey(ResourceLocation[] keys) {
        // 女仆没有玩家配方簿，不产生无意义的配方解锁包。
    }

    @Override
    public int awardRecipes(Collection<Recipe<?>> recipes) {
        return 0;
    }

    @Override
    public void initMenu(AbstractContainerMenu menu) {
        // 镜像没有客户端容器，保持同步器为空。
    }

    @Override
    public OptionalInt openMenu(MenuProvider provider) {
        return OptionalInt.empty();
    }

    @Override
    public void closeContainer() {
    }

    @Override
    public void tick() {
    }

    @Override
    public void doTick() {
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public void displayClientMessage(Component message, boolean actionBar) {
    }

    @Override
    public void sendSystemMessage(Component message) {
    }

    @Override
    public void onEnterCombat() {
    }

    @Override
    public void onLeaveCombat() {
    }

    @Override
    public void onUpdateAbilities() {
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
    }

    @Override
    protected void onEffectAdded(MobEffectInstance effect, Entity source) {
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance effect, boolean forced, Entity source) {
    }

    @Override
    protected void onEffectRemoved(MobEffectInstance effect) {
    }

    /**
     * 当前绑定的女仆，仅在触发器调用期间有值。
     */
    public EntityMaid boundMaid() {
        return maid;
    }

    /**
     * 镜像没有真实客户端，但仍满足 {@link ServerPlayer#connection} 的非空约束。
     * 监听器与底层连接都丢弃数据包，兼容直接绕过监听器发包的模组。
     */
    private static final class DiscardingPacketListener extends ServerGamePacketListenerImpl {
        private DiscardingPacketListener(MinecraftServer server, ServerPlayer player) {
            super(server, new DiscardingConnection(), player);
        }

        @Override
        public void tick() {
        }

        @Override
        public void disconnect(@Nonnull Component reason) {
        }

        @Override
        public void send(@Nonnull Packet<?> packet) {
        }

        @Override
        public void send(
                @Nonnull Packet<?> packet,
                @Nullable PacketSendListener listener
        ) {
        }
    }

    private static final class DiscardingConnection extends Connection {
        private DiscardingConnection() {
            super(PacketFlow.CLIENTBOUND);
        }

        @Override
        public void send(@Nonnull Packet<?> packet) {
        }

        @Override
        public void send(
                @Nonnull Packet<?> packet,
                @Nullable PacketSendListener listener
        ) {
        }

        @Override
        public void disconnect(@Nonnull Component reason) {
        }
    }

    record Binding(EntityMaid maid, PlayerAdvancements advancements) {
    }
}
