package com.laixia.maidintelligence.gametest.support;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 每件武器各自干了多少活。
 *
 * <p>"她打得好不好"和"她会不会用手上这几样"是两个问题。基准那一行只答得了前一个：
 * 清场几只、挨了多少。剑斧弓三样一起发下去之后，一局零杀六伤和一局零杀六伤可以是
 * 完全不同的两件事——一个是她换了三次手都没打赢，一个是她压根没把斧头从包里拿出来
 * 过。前者要调定价，后者是选择那一层根本没看见它。
 *
 * <p>所以这里按武器分开记四样：拿在手里多少 tick、打出多少伤害、补掉几只、挥了几下。
 *
 * <p>伤害归属问的是伤害源而不是她当时拿着什么。箭在空中飞的那一秒她完全可能已经换回
 * 剑了，按手上的算就会把弓的战果记到剑头上——而"弓有没有用"正是这一局要答的。近战没
 * 有这个问题：直接实体就是她自己，那一刻手上是什么就是什么。
 */
public final class WeaponLedger {
    /** 远程战果统一记在这个名下：伤害源只说得出是箭，说不出是哪把弓。 */
    private static final String PROJECTILE = "bow(arrow)";

    private static final String EMPTY = "empty";

    private final Map<String, Integer> heldTicks = new LinkedHashMap<>();
    private final Map<String, Double> dealt = new LinkedHashMap<>();
    private final Map<String, Integer> kills = new LinkedHashMap<>();
    private final Map<String, Integer> swings = new LinkedHashMap<>();

    private final LivingEntity[] pack;
    private final float[] lastHealth;
    private final boolean[] counted;

    private boolean previousCooling;

    public WeaponLedger(LivingEntity[] pack) {
        this.pack = pack;
        this.lastHealth = new float[pack.length];
        this.counted = new boolean[pack.length];
        for (int slot = 0; slot < pack.length; slot++) {
            lastHealth[slot] = pack[slot].getHealth();
        }
    }

    /**
     * 折进这一 tick。
     *
     * <p>要每 tick 都叫，不能采样：掉血是瞬时的，隔几 tick 看一次会把两次命中并成
     * 一次，而两次命中中间正好是她换手的地方。
     */
    public void record(EntityMaid maid) {
        String hand = nameOf(maid.getMainHandItem());
        heldTicks.merge(hand, 1, Integer::sum);

        boolean cooling = maid.getBrain()
                .hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN);
        if (cooling && !previousCooling) {
            swings.merge(hand, 1, Integer::sum);
        }
        previousCooling = cooling;

        for (int slot = 0; slot < pack.length; slot++) {
            LivingEntity foe = pack[slot];
            float health = foe.getHealth();
            if (health < lastHealth[slot]) {
                String by = creditFor(foe, hand);
                dealt.merge(by, (double) (lastHealth[slot] - health),
                        Double::sum);
                if (!foe.isAlive() && !counted[slot]) {
                    counted[slot] = true;
                    kills.merge(by, 1, Integer::sum);
                }
            }
            lastHealth[slot] = health;
        }
    }

    /**
     * 这一下算在谁头上。
     *
     * <p>直接实体是箭就记给弓，否则记给她此刻手上的东西。伤害源缺失时退回手上那件，
     * 这只在实体刚被别的东西弄死的边角上出现，宁可归错也不要丢掉一笔。
     */
    private String creditFor(LivingEntity foe, String hand) {
        DamageSource source = foe.getLastDamageSource();
        if (source != null && source.getDirectEntity() instanceof AbstractArrow) {
            return PROJECTILE;
        }
        return hand;
    }

    /** 一行，跟在 BENCH 那行后面并排读。 */
    public String line() {
        StringBuilder out = new StringBuilder("WEAPONS");
        for (Map.Entry<String, Integer> entry : heldTicks.entrySet()) {
            String name = entry.getKey();
            if (EMPTY.equals(name) && dealt.getOrDefault(name, 0.0D) <= 0.0D) {
                continue;
            }
            out.append(' ').append(name)
                    .append("[held=").append(entry.getValue()).append('t')
                    .append(" swings=").append(swings.getOrDefault(name, 0))
                    .append(" dealt=")
                    .append(String.format("%.1f",
                            dealt.getOrDefault(name, 0.0D)))
                    .append(" kills=").append(kills.getOrDefault(name, 0))
                    .append(']');
        }
        // 弓的战果挂在箭上，而箭不占她的手，所以上面那圈遍历不到它。
        if (dealt.containsKey(PROJECTILE) || kills.containsKey(PROJECTILE)) {
            out.append(' ').append(PROJECTILE)
                    .append("[dealt=")
                    .append(String.format("%.1f", dealt.get(PROJECTILE) == null
                            ? 0.0D : dealt.get(PROJECTILE)))
                    .append(" kills=").append(kills.getOrDefault(PROJECTILE, 0))
                    .append(']');
        }
        return out.toString();
    }

    /** 她整局有没有碰过这件东西——名字按物品注册名的尾巴匹配。 */
    public boolean everHeld(String suffix) {
        for (String name : heldTicks.keySet()) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static String nameOf(ItemStack stack) {
        return stack.isEmpty() ? EMPTY : stack.getItem().toString();
    }
}
