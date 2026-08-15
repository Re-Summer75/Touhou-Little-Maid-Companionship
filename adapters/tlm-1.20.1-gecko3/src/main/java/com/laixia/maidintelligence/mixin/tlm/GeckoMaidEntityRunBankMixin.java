package com.laixia.maidintelligence.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller
        .AnimationController;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate
        .AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.context
        .AnimationContext;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated
        .AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated
        .AnimatedGeoModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 跑步时整个模型跟着视角侧倾，去掉。
 *
 * <p>病灶在模型自带的动画数据里。以 `winefox`（大正女仆酒狐）为例，
 * `geckolib/animation/winefox.animation.json` 的 `run` 写着：
 *
 * <pre>
 *   "Root": { "rotation": [0, 0, "0.4*ysm.head_yaw"], ... }
 * </pre>
 *
 * <p>根骨骼绕 Z 轴按**头部偏航**侧倾，而根骨骼挂着整个模型，所以整个人跟着歪。
 * `walk` 与 `idle` 里都没有这一条，因此只有跑起来才看得见。
 *
 * <p>它读的量是错的：头朝哪和身体受什么力没有关系——跑直线时扭头看一眼就压弯，
 * 真转弯时若眼睛盯着别处反倒不压。方向也压向外侧，与保持平衡该做的相反。
 *
 * <h2>为什么它看起来像"Gecko 通用、YSM 也有"</h2>
 *
 * <p>那条表达式用的是 {@code ysm.} 命名空间——**YSM 的 molang 变量**。这批模型按
 * YSM 的约定写成，本体实现了同名变量以兼容，于是同一个模型在两个 mod 里都会侧倾。
 * 所以它既不在 GeckoLib 里，也不在渲染代码里，而是**模型作者的写法在两边都被执行**。
 * 换个没有这条表达式的模型（本体自带的二十来个 Gecko 模型里多数没有），倾斜就消失。
 *
 * <h2>注入点</h2>
 *
 * <p>{@code setCustomAnimations} 的 {@code RETURN}，与 {@link
 * GeckoMaidEntityBonePhysicsMixin} 同一处：它在控制器动画和硬编码动画**都跑完之后**
 * 触发，此时骨骼上的角度就是这一帧最终要用的值。写在更早的位置会被后面的动画盖掉。
 *
 * <p>{@code require = 0}：本体改了这个方法名或参数时，这条补丁静默失效、画面回到
 * 本体原样，而不是让人开不了游戏。一个美术修正没有资格拿别人的启动去换。
 */
@Mixin(value = GeckoMaidEntity.class, remap = false)
public abstract class GeckoMaidEntityRunBankMixin {
    /** 根骨骼的名字。整个模型都挂在它下面。 */
    private static final String ROOT_BONE = "Root";

    @Inject(
            method = "setCustomAnimations",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private void maidIntelligence$dropRunBank(
            AnimationContext<?> context,
            AnimationEvent<?> event,
            CallbackInfoReturnable<Boolean> callback
    ) {
        AnimatableEntity<?> self = (AnimatableEntity<?>) (Object) this;
        if (!(self.getEntity() instanceof LivingEntity maid)) {
            return;
        }
        if (!maidIntelligence$withinRunOrItsFadeOut(maid, event)) {
            return;
        }
        AnimatedGeoModel model = self.getCurrentModel();
        if (model == null) {
            return;
        }
        AnimatedGeoBone root = model.bones().get(ROOT_BONE);
        if (root != null) {
            root.setRotationZ(0.0F);
        }
    }

    /**
     * 她正在跑，或者刚停下、`run` 还没淡完。
     *
     * <p>只门控 {@code isSprinting()} 会在她停下的那一刻立刻收手，而 Gecko 还在把
     * `run` 混合淡出——那几帧里侧倾又露出来一下再归位，实机看到的就是"一瞬间的细微
     * 倾斜然后还原"。所以要一直盖到淡出结束。
     *
     * <p>盖多久**问动画系统自己要**（{@code transitionLengthTicks}），不是设一个数：
     * 这个值本体改了或模型换了，保持期跟着变，不会有一个悄悄过期的常数留在这里。
     *
     * <p>多盖这几 tick 是安全的，因为那时候在播的只可能是 `walk` 或 `idle`：`walk`
     * 写给 Root 的就是 {@code [0, 0, 0]}，归零与它一致；`idle` 根本不碰 Root，归零
     * 给的正是它要的休息值。整个模型里唯一真的侧倾根骨骼的是 `sleep`（23.76°），
     * 而她不可能在冲刺结束后的几 tick 内躺下。
     */
    private static boolean maidIntelligence$withinRunOrItsFadeOut(
            LivingEntity maid, AnimationEvent<?> event
    ) {
        if (maid.isSprinting()) {
            SPRINTED_AT.put(maid, maid.tickCount);
            return true;
        }
        Integer sprinted = SPRINTED_AT.get(maid);
        if (sprinted == null) {
            return false;
        }
        AnimationController<?> controller = event.getController();
        double fade = controller == null
                ? 0.0D
                : controller.transitionLengthTicks;
        if (maid.tickCount - sprinted > fade + 1.0D) {
            SPRINTED_AT.remove(maid);
            return false;
        }
        return true;
    }

    /**
     * 她最后一次在跑是第几 tick。
     *
     * <p>存它是因为"淡出还没结束"派生不出来：那要知道跑步是什么时候停的，而实体上
     * 没有这个量。所有者只有这个类，淡完就删，弱引用随实体卸载一起走。
     */
    private static final java.util.Map<LivingEntity, Integer> SPRINTED_AT =
            new java.util.WeakHashMap<>();
}
