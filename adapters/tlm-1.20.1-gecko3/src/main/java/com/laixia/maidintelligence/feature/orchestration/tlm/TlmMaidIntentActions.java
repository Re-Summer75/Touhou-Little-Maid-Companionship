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

/**
 * The sole dispatcher allowed to apply data-driven companion side effects.
 */
public final class TlmMaidIntentActions
        implements IntentActionPort<EntityMaid> {
    private final TlmOwnerCompanionIntentAction ownerAction;
    private final TlmSnackCabinetIntentAction snackCabinetAction;
    private final TlmDeployBoatIntentAction deployBoatAction;

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
        snackCabinetAction = new TlmSnackCabinetIntentAction(
                snackCabinetMeals
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
