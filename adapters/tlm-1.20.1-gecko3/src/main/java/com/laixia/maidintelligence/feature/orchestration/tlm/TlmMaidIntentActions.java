package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.MaidAbilityApi;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionBand;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.port.IntentActionPort;
import com.laixia.maidintelligence.feature.orchestration.tlm.action.TlmOwnerCompanionIntentAction;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmCombatAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmAlertness;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.perception.TlmThreatScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.sustenance.EatFromPackAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.arsenal.TlmWeaponScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.ApproachAndCommitAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.needs.LooseFoodErrand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.needs.CabinetMealErrand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.company.FollowOwnerErrand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.leisure.LingerNearOwnerErrand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.needs.ReturnHomeErrand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.company.KeepCompanyErrand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.leisure.RestOnSeatErrand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.leisure.JoyBlockErrand;

/**
 * The sole dispatcher allowed to apply data-driven companion side effects.
 */
public final class TlmMaidIntentActions
        implements IntentActionPort<EntityMaid> {
    private final TlmOwnerCompanionIntentAction ownerAction;
    private final ApproachAndCommitAction snackCabinetAction;
    private final ApproachAndCommitAction looseFoodAction;
    private final ApproachAndCommitAction followOwnerAction;
    private final ApproachAndCommitAction returnHomeAction;
    private final ApproachAndCommitAction restOnSeatAction;
    private final ApproachAndCommitAction joyBlockAction;
    private final ApproachAndCommitAction keepCompanyAction;
    private final ApproachAndCommitAction lingerNearOwnerAction;
    private final TlmDeployBoatIntentAction deployBoatAction;
    private final TlmCombatAction combatAction;
    private final EatFromPackAction eatFromPackAction;
    private final Map<OrchestrationId, IntentActionHandler> handlers;

    public TlmMaidIntentActions(
            Consumer<EntityMaid> hungerRequestAction
    ) {
        this(
                hungerRequestAction,
                new MaidSnackCabinetMealSource(new MaidMealAccess()),
                null
        );
    }

    public TlmMaidIntentActions(
            Consumer<EntityMaid> hungerRequestAction,
            MaidSnackCabinetMealSource snackCabinetMeals
    ) {
        this(hungerRequestAction, snackCabinetMeals, null);
    }

    public TlmMaidIntentActions(
            Consumer<EntityMaid> hungerRequestAction,
            MaidSnackCabinetMealSource snackCabinetMeals,
            MaidAbilityApi<EntityMaid> abilities
    ) {
        this(hungerRequestAction, snackCabinetMeals, abilities, null);
    }

    /**
     * The wiring production uses, hunger included.
     *
     * <p>Only the fight needs it, and only for one of its four eating rules —
     * the one that spends a quiet moment on a mouthful. Scenarios that build
     * this without a status read her as full and exercise the other three.
     */
    public TlmMaidIntentActions(
            Consumer<EntityMaid> hungerRequestAction,
            MaidSnackCabinetMealSource snackCabinetMeals,
            MaidAbilityApi<EntityMaid> abilities,
            MaidStatusApi<EntityMaid> status
    ) {
        ownerAction = new TlmOwnerCompanionIntentAction(
                hungerRequestAction
        );
        // No recogniser is registered yet, so only vanilla weapons are
        // known. A gun mod supplies one and needs nothing else changed.
        combatAction = new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE),
                status,
                // 与找地上食物的是同一份索引：掉落物登记一次，谁需要什么自己
                // 去问。空手挨追时她据此找地上的武器。
                snackCabinetMeals.perception()
        );
        eatFromPackAction = new EatFromPackAction(status);
        snackCabinetAction = new ApproachAndCommitAction(
                new CabinetMealErrand(snackCabinetMeals)
        );
        // Built from the meal source's own perception and feeding, so both
        // ways of eating agree on what is edible and what is in reach.
        followOwnerAction = new ApproachAndCommitAction(
                FollowOwnerErrand.create()
        );
        returnHomeAction = new ApproachAndCommitAction(
                ReturnHomeErrand.create()
        );
        restOnSeatAction = new ApproachAndCommitAction(
                new RestOnSeatErrand(snackCabinetMeals.perception())
        );
        joyBlockAction = new ApproachAndCommitAction(
                new JoyBlockErrand()
        );
        keepCompanyAction = new ApproachAndCommitAction(
                new KeepCompanyErrand(snackCabinetMeals.perception())
        );
        lingerNearOwnerAction = new ApproachAndCommitAction(
                LingerNearOwnerErrand.create()
        );
        looseFoodAction = new ApproachAndCommitAction(
                new LooseFoodErrand(
                        snackCabinetMeals.perception(),
                        snackCabinetMeals.mealAccess()
                )
        );
        deployBoatAction = abilities == null
                ? null
                : new TlmDeployBoatIntentAction(abilities);
        handlers = register();
        requireEveryActionHandled(handlers);
    }

    /**
     * 建表：每个动作一处登记，三个时刻一起交出去。
     *
     * <p>差事那八条走同一个适配器，所以"取消"和"复验"不可能被漏掉——它们不是
     * 各自写一遍，是同一段代码。
     */
    private Map<OrchestrationId, IntentActionHandler> register() {
        Map<OrchestrationId, IntentActionHandler> table =
                new LinkedHashMap<>();
        table.put(CompanionIntentIds.FETCH_SNACK_CABINET_MEAL,
                errand(CompanionBand.NEEDS, snackCabinetAction));
        table.put(CompanionIntentIds.PICK_UP_LOOSE_FOOD,
                errand(CompanionBand.NEEDS, looseFoodAction));
        table.put(CompanionIntentIds.FOLLOW_OWNER_ANCHOR,
                errand(CompanionBand.COMPANIONSHIP, followOwnerAction));
        table.put(CompanionIntentIds.RETURN_HOME_ANCHOR,
                errand(CompanionBand.NEEDS, returnHomeAction));
        table.put(CompanionIntentIds.REST_ON_SEAT, errand(CompanionBand.LEISURE, restOnSeatAction));
        table.put(CompanionIntentIds.USE_JOY_BLOCK, errand(CompanionBand.LEISURE, joyBlockAction));
        table.put(CompanionIntentIds.KEEP_COMPANY, errand(CompanionBand.COMPANIONSHIP, keepCompanyAction));
        table.put(CompanionIntentIds.LINGER_NEAR_OWNER,
                errand(CompanionBand.LEISURE, lingerNearOwnerAction));

        table.put(CompanionIntentIds.ENGAGE_THREAT, new IntentActionHandler() {
            @Override
            public CompanionBand band() {
                return CompanionBand.SAFETY;
            }

            @Override
            public ActionResult execute(
                    EntityMaid maid,
                    Map<String, String> parameters,
                    long gameTime,
                    int elapsedTicks
            ) {
                return combatAction.execute(maid);
            }

            @Override
            public void cancel(
                    EntityMaid maid, Map<String, String> parameters
            ) {
                // 只放手上的东西和攻击目标，**不拆移动**：一场仗中途超时抹掉她
                // 走了一半的撤退是量出来更糟的（两百 tick 里九十二 tick 站着
                // 不动）。完整收尾归战斗自己，它在仗真的结束时做。
                combatAction.releaseHands(maid);
            }
        });

        table.put(CompanionIntentIds.EAT_FROM_PACK, new IntentActionHandler() {
            @Override
            public CompanionBand band() {
                return CompanionBand.NEEDS;
            }

            @Override
            public ActionResult execute(
                    EntityMaid maid,
                    Map<String, String> parameters,
                    long gameTime,
                    int elapsedTicks
            ) {
                return eatFromPackAction.execute(maid);
            }

            @Override
            public void cancel(
                    EntityMaid maid, Map<String, String> parameters
            ) {
                eatFromPackAction.cancel(maid);
            }
        });

        table.put(CompanionIntentIds.APPROACH_OWNER, new IntentActionHandler() {
            @Override
            public CompanionBand band() {
                return CompanionBand.OWNER_COMMAND;
            }

            @Override
            public ActionResult execute(
                    EntityMaid maid,
                    Map<String, String> parameters,
                    long gameTime,
                    int elapsedTicks
            ) {
                return ownerAction.approach(maid, parameters, gameTime);
            }

            @Override
            public void cancel(
                    EntityMaid maid, Map<String, String> parameters
            ) {
                ownerAction.cancelApproach(maid, parameters);
            }

            @Override
            public boolean revalidate(
                    EntityMaid maid,
                    Map<String, String> parameters,
                    long gameTime
            ) {
                return ownerAction.revalidateMovement(
                        maid, parameters, gameTime
                );
            }
        });

        table.put(
                CompanionIntentIds.COMPANION_COMMAND_WINDOW,
                new IntentActionHandler() {
                    @Override
                    public CompanionBand band() {
                        return CompanionBand.OWNER_COMMAND;
                    }

                    @Override
                    public ActionResult execute(
                            EntityMaid maid,
                            Map<String, String> parameters,
                            long gameTime,
                            int elapsedTicks
                    ) {
                        return ownerAction.commandWindow(
                                maid, parameters, gameTime, elapsedTicks
                        );
                    }

                    @Override
                    public void cancel(
                            EntityMaid maid, Map<String, String> parameters
                    ) {
                        ownerAction.cancelCommandWindow(maid);
                    }

                    @Override
                    public boolean revalidate(
                            EntityMaid maid,
                            Map<String, String> parameters,
                            long gameTime
                    ) {
                        return ownerAction.revalidateMovement(
                                maid, parameters, gameTime
                        );
                    }
                }
        );

        table.put(
                CompanionIntentIds.REQUEST_HUNGER_ATTENTION,
                new IntentActionHandler() {
                    @Override
                    public CompanionBand band() {
                        return CompanionBand.NEEDS;
                    }

                    @Override
                    public ActionResult execute(
                            EntityMaid maid,
                            Map<String, String> parameters,
                            long gameTime,
                            int elapsedTicks
                    ) {
                        return ownerAction.requestHungerAttention(maid);
                    }

                    @Override
                    public boolean revalidate(
                            EntityMaid maid,
                            Map<String, String> parameters,
                            long gameTime
                    ) {
                        return ownerAction.revalidateHungerRequest(maid);
                    }
                }
        );

        // 没有能力接口时也登记，返回失败即可。少登记一个会让完整性检查失去意义，
        // 而"这一局没装这个功能"和"有人忘了接线"必须是两种不同的东西。
        table.put(CompanionIntentIds.DEPLOY_BOAT, new IntentActionHandler() {
            @Override
            public CompanionBand band() {
                return CompanionBand.OWNER_COMMAND;
            }

            @Override
            public ActionResult execute(
                    EntityMaid maid,
                    Map<String, String> parameters,
                    long gameTime,
                    int elapsedTicks
            ) {
                return deployBoatAction == null
                        ? ActionResult.FAILED
                        : deployBoatAction.execute(maid, parameters, gameTime);
            }

            @Override
            public boolean revalidate(
                    EntityMaid maid,
                    Map<String, String> parameters,
                    long gameTime
            ) {
                return deployBoatAction != null
                        && deployBoatAction.revalidate(
                                maid, parameters, gameTime
                        );
            }
        });
        return table;
    }

    /** 差事的三个时刻都由骨架回答，所以它们只需要交出去一次。 */
    private static IntentActionHandler errand(
            CompanionBand band,
            ApproachAndCommitAction action
    ) {
        return new IntentActionHandler() {
            @Override
            public CompanionBand band() {
                return band;
            }

            @Override
            public ActionResult execute(
                    EntityMaid maid,
                    Map<String, String> parameters,
                    long gameTime,
                    int elapsedTicks
            ) {
                return action.execute(maid, parameters, gameTime);
            }

            @Override
            public void cancel(
                    EntityMaid maid, Map<String, String> parameters
            ) {
                action.cancel(maid);
            }

            @Override
            public boolean revalidate(
                    EntityMaid maid,
                    Map<String, String> parameters,
                    long gameTime
            ) {
                return action.revalidate(maid, gameTime);
            }
        };
    }

    /**
     * 词表里的每一个动作都必须有人接。
     *
     * <p>此前漏接一个动作的表现是它静默失败——引擎照常选中它、照常报"执行失败"，
     * 而失败在这套系统里是**正常结果**（没有空座位、认领被抢走），所以没有任何人
     * 会注意到。现在漏接的是启动时就说话的错误。
     */
    private static void requireEveryActionHandled(
            Map<OrchestrationId, IntentActionHandler> table
    ) {
        Set<OrchestrationId> missing = new LinkedHashSet<>(
                CompanionIntentIds.vocabulary().actions()
        );
        missing.removeAll(table.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Companion actions with no handler: " + missing
            );
        }
    }

    @Override
    public ActionResult execute(
            EntityMaid maid,
            OrchestrationId action,
            Map<String, String> parameters,
            long gameTime,
            int elapsedTicks
    ) {
        IntentActionHandler handler = handlers.get(action);
        if (handler == null) {
            return ActionResult.FAILED;
        }
        // 许可矩阵，问在唯一一处每个动作都必经的地方。放在这里而不是各个动作里，
        // 是因为"这一类事现在能不能做"只有一个答案，而散着问必然会散着答。
        if (!TlmAlertness.of(maid).permits(handler.band())) {
            return ActionResult.FAILED;
        }
        return handler.execute(maid, parameters, gameTime, elapsedTicks);
    }

    @Override
    public void cancel(
            EntityMaid maid,
            OrchestrationId action,
            Map<String, String> parameters
    ) {
        IntentActionHandler handler = handlers.get(action);
        if (handler != null) {
            handler.cancel(maid, parameters);
        }
    }

    @Override
    public boolean revalidate(
            EntityMaid maid,
            OrchestrationId action,
            Map<String, String> parameters,
            long gameTime
    ) {
        IntentActionHandler handler = handlers.get(action);
        return handler != null
                && handler.revalidate(maid, parameters, gameTime);
    }
}
