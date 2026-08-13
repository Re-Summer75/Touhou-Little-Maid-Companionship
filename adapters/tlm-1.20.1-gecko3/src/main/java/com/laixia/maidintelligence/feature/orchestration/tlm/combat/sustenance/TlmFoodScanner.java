package com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.domain.combat.sustenance.FoodValue;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads her pack and reports what each edible thing would actually buy her.
 *
 * <p>The counterpart to the weapon scanner, and built the same way: ask the
 * item what it is rather than recognising its name. A golden apple is not
 * special because it is a golden apple — it is special because it carries
 * absorption and regeneration, and any modded fruit that carries the same is
 * exactly as good. Nothing here names an item.
 *
 * <p>Effects are converted into the currency the fight settles in. Regeneration
 * is health arriving slowly and slowly still counts inside a fifteen-second
 * fight; absorption is damage she gets to ignore; resistance is damage she gets
 * to ignore expressed as a fraction, and it is the one that cannot be valued
 * without knowing what is being resisted, so it is valued against the beating
 * she is currently taking rather than in the abstract.
 */
public final class TlmFoodScanner {
    private static final double TICKS_PER_SECOND = 20.0D;

    /** Ticks a mouthful takes when the item declines to say. */
    private static final int ASSUMED_EATING_TICKS = 32;

    /**
     * Health one level of regeneration returns per second.
     *
     * <p>Vanilla heals once every {@code 50 >> amplifier} ticks, so level one is
     * a heart every two and a half seconds. Stated as a rate because what the
     * decision needs is "how much of this arrives before the fight is over",
     * and the fight decides that, not the potion.
     */
    private static final double REGENERATION_HEALTH_PER_SECOND = 0.4D;

    /**
     * How much of a long effect the fight is assumed to collect.
     *
     * <p>A golden apple regenerates for five seconds and absorbs for two
     * minutes; crediting either in full would have her value a mouthful by how
     * long it lasts rather than by how much of it she uses. Ten seconds is about
     * how long one of these fights runs, and it is the honest ceiling on what
     * any effect can be worth to it.
     */
    private static final double FIGHT_SECONDS = 10.0D;

    /** Absorption hearts, in the damage they actually soak. */
    private static final double HEALTH_PER_HEART = 2.0D;

    /**
     * Her hunger scale against the item's.
     *
     * <p>Items are written against a player's twenty; the host gives her a
     * hundred, and {@code DefaultHungerPolicy} restores five points per point of
     * nutrition. Reporting raw nutrition here would make every food look a fifth
     * as filling as it is, and the rule that spends a lull on a mouthful would
     * then almost never fire.
     */
    private static final double NUTRITION_TO_HUNGER = 5.0D;

    /** Everything edible she is carrying, with what it would buy. */
    public List<FoodValue> scan(EntityMaid maid) {
        List<FoodValue> larder = new ArrayList<>();
        IItemHandler backpack = maid.getAvailableBackpackInv();
        for (int slot = 0; slot < backpack.getSlots(); slot++) {
            FoodValue value = valueOf(maid, backpack.getStackInSlot(slot), slot);
            if (value != null) {
                larder.add(value);
            }
        }
        return larder;
    }

    /**
     * 一个"碍事但不掉血"的效果每秒记多少账。
     *
     * <p>半点。它不是一个物理量，是一个排序用的价钱：够让带副作用的那一口输给
     * 任何一口干净的食物，仅此而已。
     */
    private static final double NUISANCE_DAMAGE_PER_SECOND = 0.5D;

    /**
     * 所有"碍事但不掉血"的效果加起来最多记多少。
     *
     * <p>两颗心。缓慢、虚弱、失明、饥饿都不会真的把她打死，所以它们的账必须
     * 封顶——不封顶的话，腐肉那三十秒饥饿按每秒半点要记十五点，比她一半的命
     * 还多，于是"有点碍事"在定价里比"真的中毒"更可怕。上限只保证一件事：
     * 它们排在干净食物后面，但永远排在真会掉血的东西前面。
     */
    private static final double NUISANCE_DAMAGE_CEILING = 4.0D;

    /**
     * 一个有害效果按预计伤害折算成多少。
     *
     * <p>毒与凋零按原版自己的节奏掉血：毒每 25/(等级) tick 一点，凋零每 40/(等级)
     * 一点，都是原版 {@code MobEffect.applyEffectTick} 里的数，不是这里估的。
     * 其余有害效果不直接掉血，按时长记一笔小账并封顶——它表达的是"这一口不
     * 值得"，而不是"这一口会要她的命"。
     */
    private static double harmOf(MobEffect effect, double seconds, int level) {
        if (effect == MobEffects.POISON) {
            return seconds * TICKS_PER_SECOND / (25.0D / level);
        }
        if (effect == MobEffects.WITHER) {
            return seconds * TICKS_PER_SECOND / (40.0D / level);
        }
        return Math.min(
                seconds * NUISANCE_DAMAGE_PER_SECOND * level,
                NUISANCE_DAMAGE_CEILING
        );
    }

    /** What this stack is worth, or {@code null} if it is not food. */
    public FoodValue valueOf(EntityMaid maid, ItemStack stack, int slot) {
        if (stack.isEmpty()) {
            return null;
        }
        FoodProperties food = stack.getFoodProperties(maid);
        if (food == null) {
            return null;
        }
        double health = 0.0D;
        double absorption = 0.0D;
        double harm = 0.0D;
        for (var chance : food.getEffects()) {
            MobEffectInstance effect = chance.getFirst();
            if (effect == null) {
                continue;
            }
            // Weighted by its own chance of applying. A stew that helps one time
            // in four is worth a quarter of a stew that always does, and the
            // item states which it is.
            double odds = Math.max(0.0D, Math.min(1.0D, chance.getSecond()));
            double seconds = Math.min(
                    FIGHT_SECONDS, effect.getDuration() / TICKS_PER_SECOND
            );
            int level = effect.getAmplifier() + 1;
            // What she already has, she does not buy again. Re-applying an
            // effect she is standing in adds nothing but a longer clock, and
            // for absorption not even that: the hearts are granted once, so a
            // second apple on top of the first is worth exactly zero. Pricing
            // it at face value is how a stack of five goes down in eight
            // seconds while the situation never appears to improve.
            MobEffectInstance standing = maid.getEffect(effect.getEffect());
            boolean alreadyCovered = standing != null
                    && standing.getAmplifier() >= effect.getAmplifier();
            if (effect.getEffect() == MobEffects.REGENERATION) {
                // Only the time this adds beyond what is already running.
                double running = alreadyCovered
                        ? Math.min(
                                FIGHT_SECONDS,
                                standing.getDuration() / TICKS_PER_SECOND)
                        : 0.0D;
                health += odds * Math.max(0.0D, seconds - running)
                        * REGENERATION_HEALTH_PER_SECOND * level;
            } else if (effect.getEffect() == MobEffects.ABSORPTION) {
                // Absorption does not expire inside a fight this short, so the
                // whole of it counts — unless she is already carrying it, in
                // which case none of it does.
                absorption += alreadyCovered
                        ? 0.0D
                        : odds * level * 2.0D * HEALTH_PER_HEART;
            } else if (effect.getEffect() == MobEffects.HEAL) {
                health += odds * level * 2.0D * HEALTH_PER_HEART;
            } else if (effect.getEffect().getCategory()
                    == MobEffectCategory.HARMFUL) {
                // 有害效果此前一律估价为零，于是毒马铃薯、河豚、腐肉在她眼里
                // 和面包一样免费——玩家报的"她不知道吃了有什么减益"就是这个。
                //
                // 掉血的两种按它们自己的节奏折算成伤害；其余（缓慢、虚弱、失明、
                // 饥饿）不直接掉血，但没有一样是她在打架时想要的，按时长记一笔
                // 小账，足够让它输给任何一口干净的食物，又不至于把"只剩这个能
                // 吃"变成宁可饿死。
                //
                // 判据问的是效果自己的分类，不是一张名单：模组的新负面效果
                // 不需要这里认识它。
                harm += odds * harmOf(effect.getEffect(), seconds, level);
            }
        }
        return new FoodValue(
                slot,
                food.getNutrition() * NUTRITION_TO_HUNGER,
                // Healing is capped by the room there is to heal into. Four
                // points of regeneration on a maid at full health is four
                // points into the floor, and pricing it at face value makes an
                // apple look worth eating at twenty out of twenty.
                Math.min(health, Math.max(
                        0.0D, maid.getMaxHealth() - maid.getHealth()
                )),
                absorption,
                harm,
                eatingSeconds(stack)
        );
    }

    /**
     * How long this particular mouthful ties up her hands.
     *
     * <p>Asked of the item, because items disagree: most food is thirty-two
     * ticks, dried kelp is sixteen, and a modded ration can be anything. A
     * constant here would price a snatched bite and a slow meal as the same
     * interruption, and the interruption is the entire cost of eating — the
     * decision is exactly "is what this buys worth the window it opens", so
     * getting the window wrong gets every one of those decisions wrong.
     */
    private double eatingSeconds(ItemStack stack) {
        int ticks = stack.getUseDuration();
        return (ticks > 0 ? ticks : ASSUMED_EATING_TICKS) / TICKS_PER_SECOND;
    }

}
