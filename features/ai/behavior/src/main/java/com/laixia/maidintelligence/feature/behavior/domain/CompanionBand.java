package com.laixia.maidintelligence.feature.behavior.domain;

/**
 * 一件事属于哪一类。
 *
 * <p>{@code docs/architecture/behavior-spec.md} 里的第三根轴。它此前只以整数
 * 形式存在——写在每份意图 JSON 的
 * {@code interrupt_priority} 里、又在验证里抄了一份常量，而没有任何类型把"这五个数
 * 是一个封闭集合"这件事说出来。
 *
 * <p><b>无序。</b>数值只用来回答"谁能打断谁"，不表达偏好——偏好由效用分数回答。把
 * 偏好写回这些数字，等于关掉效用层，而优先级阶梯会一次一个意图地长回来。
 *
 * <p>这里也**不是**处境。处境（{@link CompanionAlertness}）问的是"外界此刻要求她
 * 多少"，是有序的；类别问的是"这件事是哪一种"。两者相乘才得到"现在能不能做"，
 * 见 {@link CompanionAlertness#permits}。
 */
public enum CompanionBand {
    /** 打起来了，或者要打起来了。可以打断任何东西。 */
    SAFETY(100),
    /** 主人明确要求的。 */
    OWNER_COMMAND(80),
    /** 吃饭、回家——不做会出事的那些。 */
    NEEDS(50),
    /** 跟着他、待在他附近。 */
    COMPANIONSHIP(30),
    /** 消遣、落座、游走，以及将来她自己的工作。 */
    LEISURE(10);

    private final int priority;

    CompanionBand(int priority) {
        this.priority = priority;
    }

    /** 意图定义里写的那个数。 */
    public int priority() {
        return priority;
    }

    /**
     * 这个数属于哪一档，不认识则为 {@code null}。
     *
     * <p>数据包写了新数字时答 {@code null} 而不是硬凑一档：那是一次刻意的闸门变更，
     * 应当被拒绝并说出来，而不是被静默归类。
     */
    public static CompanionBand of(int priority) {
        for (CompanionBand band : values()) {
            if (band.priority == priority) {
                return band;
            }
        }
        return null;
    }
}
