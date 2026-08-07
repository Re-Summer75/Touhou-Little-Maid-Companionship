package com.laixia.maidintelligence.feature.orchestration.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.behavior.api.MaidAbilityApi;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.orchestration.domain.ActionResult;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.port.IntentActionPort;
import com.laixia.maidintelligence.feature.orchestration.tlm.action.TlmOwnerCompanionIntentAction;
import com.laixia.maidintelligence.feature.status.tlm.MaidMealAccess;
import com.laixia.maidintelligence.feature.status.tlm.MaidSnackCabinetMealSource;

import java.util.Map;
import java.util.function.Consumer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.RangedWeaponRecognizer;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmCombatAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmThreatScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.combat.TlmWeaponScanner;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.ApproachAndCommitAction;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.LooseFoodErrand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.CabinetMealErrand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.MaintainProximityErrand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.KeepCompanyErrand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.RestOnSeatErrand;
import com.laixia.maidintelligence.feature.orchestration.tlm.errand.JoyBlockErrand;

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
    private final TlmDeployBoatIntentAction deployBoatAction;
    private final TlmCombatAction combatAction;

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
        ownerAction = new TlmOwnerCompanionIntentAction(
                hungerRequestAction
        );
        // No recogniser is registered yet, so only vanilla weapons are
        // known. A gun mod supplies one and needs nothing else changed.
        combatAction = new TlmCombatAction(
                new TlmThreatScanner(),
                new TlmWeaponScanner(RangedWeaponRecognizer.NONE)
        );
        snackCabinetAction = new ApproachAndCommitAction(
                new CabinetMealErrand(snackCabinetMeals)
        );
        // Built from the meal source's own perception and feeding, so both
        // ways of eating agree on what is edible and what is in reach.
        followOwnerAction = new ApproachAndCommitAction(
                MaintainProximityErrand.followingOwner()
        );
        returnHomeAction = new ApproachAndCommitAction(
                MaintainProximityErrand.returningHome()
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
        looseFoodAction = new ApproachAndCommitAction(
                new LooseFoodErrand(
                        snackCabinetMeals.perception(),
                        snackCabinetMeals.mealAccess()
                )
        );
        deployBoatAction = abilities == null
                ? null
                : new TlmDeployBoatIntentAction(abilities);
    }

    @Override
    public ActionResult execute(
            EntityMaid maid,
            OrchestrationId action,
            Map<String, String> parameters,
            long gameTime,
            int elapsedTicks
    ) {
        if (action.equals(CompanionIntentIds.ENGAGE_THREAT)) {
            return combatAction.execute(maid, elapsedTicks);
        }
        if (action.equals(CompanionIntentIds.APPROACH_OWNER)) {
            return ownerAction.approach(maid, parameters, gameTime);
        }
        if (action.equals(
                CompanionIntentIds.FETCH_SNACK_CABINET_MEAL
        )) {
            return snackCabinetAction.execute(
                    maid,
                    parameters,
                    gameTime
            );
        }
        if (action.equals(CompanionIntentIds.PICK_UP_LOOSE_FOOD)) {
            return looseFoodAction.execute(maid, parameters, gameTime);
        }
        if (action.equals(CompanionIntentIds.FOLLOW_OWNER_ANCHOR)) {
            return followOwnerAction.execute(maid, parameters, gameTime);
        }
        if (action.equals(CompanionIntentIds.RETURN_HOME_ANCHOR)) {
            return returnHomeAction.execute(maid, parameters, gameTime);
        }
        if (action.equals(CompanionIntentIds.REST_ON_SEAT)) {
            return restOnSeatAction.execute(maid, parameters, gameTime);
        }
        if (action.equals(CompanionIntentIds.USE_JOY_BLOCK)) {
            return joyBlockAction.execute(maid, parameters, gameTime);
        }
        if (action.equals(CompanionIntentIds.KEEP_COMPANY)) {
            return keepCompanyAction.execute(maid, parameters, gameTime);
        }
        if (action.equals(
                CompanionIntentIds.COMPANION_COMMAND_WINDOW
        )) {
            return ownerAction.commandWindow(
                    maid,
                    parameters,
                    gameTime,
                    elapsedTicks
            );
        }
        if (action.equals(
                CompanionIntentIds.REQUEST_HUNGER_ATTENTION
        )) {
            return ownerAction.requestHungerAttention(maid);
        }
        if (action.equals(CompanionIntentIds.DEPLOY_BOAT)
                && deployBoatAction != null) {
            return deployBoatAction.execute(maid, parameters, gameTime);
        }
        return ActionResult.FAILED;
    }

    @Override
    public void cancel(
            EntityMaid maid,
            OrchestrationId action,
            Map<String, String> parameters
    ) {
        if (action.equals(CompanionIntentIds.APPROACH_OWNER)) {
            ownerAction.cancelApproach(maid, parameters);
        } else if (action.equals(
                CompanionIntentIds.FETCH_SNACK_CABINET_MEAL
        )) {
            snackCabinetAction.cancel(maid);
        } else if (action.equals(CompanionIntentIds.PICK_UP_LOOSE_FOOD)) {
            looseFoodAction.cancel(maid);
        } else if (action.equals(CompanionIntentIds.FOLLOW_OWNER_ANCHOR)) {
            followOwnerAction.cancel(maid);
        } else if (action.equals(CompanionIntentIds.RETURN_HOME_ANCHOR)) {
            returnHomeAction.cancel(maid);
        } else if (action.equals(CompanionIntentIds.REST_ON_SEAT)) {
            restOnSeatAction.cancel(maid);
        } else if (action.equals(CompanionIntentIds.USE_JOY_BLOCK)) {
            joyBlockAction.cancel(maid);
        } else if (action.equals(CompanionIntentIds.KEEP_COMPANY)) {
            keepCompanyAction.cancel(maid);
        } else if (action.equals(
                CompanionIntentIds.COMPANION_COMMAND_WINDOW
        )) {
            ownerAction.cancelCommandWindow(maid);
        }
    }

    @Override
    public boolean revalidate(
            EntityMaid maid,
            OrchestrationId action,
            Map<String, String> parameters,
            long gameTime
    ) {
        if (action.equals(
                CompanionIntentIds.FETCH_SNACK_CABINET_MEAL
        )) {
            return snackCabinetAction.revalidate(maid, gameTime);
        }
        if (action.equals(CompanionIntentIds.PICK_UP_LOOSE_FOOD)) {
            return looseFoodAction.revalidate(maid, gameTime);
        }
        if (action.equals(CompanionIntentIds.FOLLOW_OWNER_ANCHOR)) {
            return followOwnerAction.revalidate(maid, gameTime);
        }
        if (action.equals(CompanionIntentIds.RETURN_HOME_ANCHOR)) {
            return returnHomeAction.revalidate(maid, gameTime);
        }
        if (action.equals(CompanionIntentIds.REST_ON_SEAT)) {
            return restOnSeatAction.revalidate(maid, gameTime);
        }
        if (action.equals(CompanionIntentIds.USE_JOY_BLOCK)) {
            return joyBlockAction.revalidate(maid, gameTime);
        }
        if (action.equals(CompanionIntentIds.KEEP_COMPANY)) {
            return keepCompanyAction.revalidate(maid, gameTime);
        }
        if (action.equals(CompanionIntentIds.APPROACH_OWNER)
                || action.equals(
                CompanionIntentIds.COMPANION_COMMAND_WINDOW
        )) {
            return ownerAction.revalidateMovement(
                    maid,
                    parameters,
                    gameTime
            );
        }
        if (action.equals(
                CompanionIntentIds.REQUEST_HUNGER_ATTENTION
        )) {
            return ownerAction.revalidateHungerRequest(maid);
        }
        if (action.equals(CompanionIntentIds.DEPLOY_BOAT)
                && deployBoatAction != null) {
            return deployBoatAction.revalidate(
                    maid,
                    parameters,
                    gameTime
            );
        }
        return false;
    }
}
