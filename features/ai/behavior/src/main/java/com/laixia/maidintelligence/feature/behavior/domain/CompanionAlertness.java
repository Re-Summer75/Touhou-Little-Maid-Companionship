package com.laixia.maidintelligence.feature.behavior.domain;

/**
 * How much of her attention the world is currently demanding.
 *
 * <p>Answers one question the rest of the mod could not: <em>given what is
 * around her, what is she allowed to be doing?</em> Interrupt bands order this
 * mod's own intents against each other, and behaviour occupancy describes what
 * the host is already doing — neither can say "there is a creeper twelve blocks
 * away, so this is not the moment to walk off and pick up a carrot". That gap is
 * why she would break off mid-fight to collect drops.
 *
 * <p>Deliberately about the <em>situation</em>, not about a decision. It does
 * not pick actions; it withdraws permission for the ones that should not be on
 * the table, which is what keeps it from becoming a second scheduler competing
 * with the intent engine.
 *
 * <p>Ordered from least to most demanding, so {@link #atLeast} reads naturally
 * and new states can be inserted without rewriting every comparison.
 */
public enum CompanionAlertness {
    /** Nothing hostile in sight. She may do whatever she likes. */
    CALM,
    /**
     * Something hostile is around, but not arriving yet.
     *
     * <p>She keeps working and keeps her errands — a zombie across a field is
     * not a reason to stop living. What she gives up is leisure: sitting down to
     * read while something hunts her reads as broken, not relaxed.
     */
    WARY,
    /**
     * Something can reach her, or is about to.
     *
     * <p>Errands stop here. The distinction from {@link #WARY} is arrival time
     * rather than distance, which is why this state needs the prediction layer:
     * "close" and "about to be on me" are different questions, and only the
     * second one should cancel what she is doing.
     */
    THREATENED,
    /** She is committed to a fight. Nothing else gets her hands or her feet. */
    FIGHTING;

    /** Whether this state is at least as demanding as {@code other}. */
    public boolean atLeast(CompanionAlertness other) {
        return ordinal() >= other.ordinal();
    }

    /**
     * Whether she may walk off to fetch, carry, sit or tidy.
     *
     * <p>Picking things up is the case that prompted this. Item collection is a
     * host behaviour with a genuine movement commitment, so once it starts it
     * competes with the fight for her feet and wins often enough to matter — she
     * would wander into a mob for a dropped feather. Stopping it at the source
     * is cheaper and clearer than out-ranking it every tick.
     */
    public boolean allowsErrands() {
        return !atLeast(THREATENED);
    }

    /** Whether she may settle into something she cannot leave quickly. */
    public boolean allowsLeisure() {
        return this == CALM;
    }

    /**
     * 这一档处境下，这一类事情还能不能做。
     *
     * <p>**许可矩阵就是这一个方法。**在它出现之前，同一个问题在四个地方各答一遍：
     * 差事骨架里的原始记忆检查、三条进食意图 JSON 里的敌情条件、{@code
     * hostile_pressure}、以及本枚举那三个没有生产消费者的谓词。四份判据不可能长期
     * 一致，而不一致的表现是"某一类行为在某种处境下偶尔还会发生"，没有人看得出来。
     *
     * <p>它只**收回**选项，不挑动作。挑动作永远是意图引擎的事——任何会设置状态并
     * 据此选择行为的东西都是第二个调度器。
     *
     * <table>
     *   <caption>处境 × 类别</caption>
     *   <tr><th></th><th>安全</th><th>主人命令</th><th>生存</th><th>陪伴</th>
     *       <th>自娱</th></tr>
     *   <tr><td>平静</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td></tr>
     *   <tr><td>警戒</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td><td>✗</td></tr>
     *   <tr><td>危险</td><td>✓</td><td>✓</td><td>✗</td><td>✗</td><td>✗</td></tr>
     *   <tr><td>交战</td><td>✓</td><td>✗</td><td>✗</td><td>✗</td><td>✗</td></tr>
     * </table>
     */
    public boolean permits(CompanionBand band) {
        if (band == null) {
            // 认不出的类别一律不许。数据包写了新的优先级数字时，该由闸门拒绝并
            // 说出来，而不是在这里被静默放行。
            return false;
        }
        return switch (band) {
            case SAFETY -> true;
            case OWNER_COMMAND -> this != FIGHTING;
            case NEEDS, COMPANIONSHIP -> allowsErrands();
            case LEISURE -> allowsLeisure();
        };
    }

    /** Whether her hands need to stay free for a weapon. */
    public boolean needsHandsFree() {
        return atLeast(THREATENED);
    }

    /**
     * Classify from what she can perceive.
     *
     * @param committed        whether she is already in a fight
     * @param hostilesSeen     how many hostiles she can perceive at all
     * @param soonestContact   seconds until the first of them could strike her,
     *                         infinite when none of them is closing
     * @param reactionSeconds  how much warning she needs to be ready in time
     */
    public static CompanionAlertness of(
            boolean committed,
            int hostilesSeen,
            double soonestContact,
            double reactionSeconds
    ) {
        if (committed) {
            return FIGHTING;
        }
        if (hostilesSeen <= 0) {
            return CALM;
        }
        // NaN would fall through every comparison and quietly report WARY, which
        // is the state that still lets her wander off.
        if (Double.isNaN(soonestContact)) {
            return THREATENED;
        }
        return soonestContact <= reactionSeconds ? THREATENED : WARY;
    }
}
