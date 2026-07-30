package com.laixia.maidintelligence.feature.advancement.server;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.level.LevelFeature;
import com.laixia.maidintelligence.feature.level.api.ExperienceSource;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
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
import java.util.OptionalInt;
import java.util.UUID;

/**
 * 女仆的镜像玩家：原版所有 criteria 触发器都只接受 {@link ServerPlayer}，且分发是按
 * {@code player.getAdvancements()} 找监听者的，所以要让女仆用上原版进度系统，就得有一个
 * 状态与女仆同步的玩家替身。
 * <p>
 * 它每个维度只有一个，不加入世界、不进玩家列表、不 tick，只在触发器调用期间被绑定到某只女仆：
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

    private MaidMirrorPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
        super(server, level, profile);
    }

    static MaidMirrorPlayer create(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            throw new IllegalStateException("Maid mirror player requires a server level");
        }
        String seed = "tlm_companionship:maid_mirror/" + level.dimension().location();
        UUID id = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        return new MaidMirrorPlayer(server, level, new GameProfile(id, PROFILE_NAME));
    }

    /**
     * 绑定女仆并同步状态。返回上一次的绑定，便于嵌套调用后恢复。
     */
    Binding bind(EntityMaid maid, PlayerAdvancements advancements) {
        Binding previous = new Binding(this.maid, this.maidAdvancements);
        this.maid = maid;
        this.maidAdvancements = advancements;
        syncFrom(maid);
        return previous;
    }

    void bindAdvancements(PlayerAdvancements advancements) {
        this.maidAdvancements = advancements;
    }

    void restore(Binding binding) {
        this.maid = binding.maid();
        this.maidAdvancements = binding.advancements();
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
        if (!getActiveEffects().isEmpty()) {
            removeAllEffects();
        }
        for (MobEffectInstance effect : maid.getActiveEffects()) {
            addEffect(new MobEffectInstance(effect));
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
            LevelFeature.INSTANCE.api().awardExperience(maid, points, ExperienceSource.ADVANCEMENT);
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
        // 原版实现会经 connection 下发配方解锁包，镜像玩家没有连接。
    }

    @Override
    public int awardRecipes(Collection<Recipe<?>> recipes) {
        return 0;
    }

    @Override
    public void initMenu(AbstractContainerMenu menu) {
        // 保持容器同步器为空，否则奖励发放里的 broadcastChanges 会走 connection。
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

    record Binding(EntityMaid maid, PlayerAdvancements advancements) {
    }
}
