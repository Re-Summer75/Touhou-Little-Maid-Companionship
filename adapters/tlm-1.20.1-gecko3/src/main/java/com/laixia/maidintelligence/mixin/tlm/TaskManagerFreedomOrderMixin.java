package com.laixia.maidintelligence.mixin.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Puts the freedom task second in the list a player picks from.
 *
 * <p>Task order is insertion order, and every extension is registered after all
 * of TLM's own tasks, so ours would otherwise sit at the very bottom past two
 * dozen entries. Idle stays first because it is the default a maid is tamed
 * with; freedom belongs immediately after it, as the other answer to "what
 * should she do when you have not told her to work".
 *
 * <p>Only the presented list is reordered. The registry keeps its own insertion
 * order, so nothing that identifies a task by index or iterates the canonical
 * list sees a different world than TLM built.
 */
@Mixin(TaskManager.class)
public abstract class TaskManagerFreedomOrderMixin {
    private static final int FREEDOM_POSITION = 1;

    @Inject(
            method = "getNotHiddenTaskList",
            at = @At("RETURN"),
            remap = false
    )
    private static void maidIntelligence$liftFreedomTask(
            EntityMaid maid,
            CallbackInfoReturnable<List<IMaidTask>> callback
    ) {
        List<IMaidTask> tasks = callback.getReturnValue();
        if (tasks == null || tasks.size() <= FREEDOM_POSITION) {
            return;
        }
        for (int index = 0; index < tasks.size(); index++) {
            if (!FreedomMaidTask.UID.equals(tasks.get(index).getUid())) {
                continue;
            }
            if (index != FREEDOM_POSITION) {
                // The list is freshly built per call, so moving an element is
                // local to this caller and cannot disturb the registry.
                tasks.add(FREEDOM_POSITION, tasks.remove(index));
            }
            return;
        }
    }
}
