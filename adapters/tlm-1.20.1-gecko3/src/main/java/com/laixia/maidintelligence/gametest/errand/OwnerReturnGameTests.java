package com.laixia.maidintelligence.gametest.errand;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.feature.status.api.MaidStatusApi;
import com.laixia.maidintelligence.gametest.support.GameTestPositions;
import com.laixia.maidintelligence.gametest.support.IntentGameTestRuntime;
import com.laixia.maidintelligence.platform.resource.ModResources;
import com.laixia.maidintelligence.platform.runtime.AdapterRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class OwnerReturnGameTests {
    private OwnerReturnGameTests() {
    }

    // completedWorkReturnsToOwnerOnce 已删。它验证的是"本体工作目标释放后
    // 归队"，而 TARGET_POS 由本体工作行为写入，自由模式不再注册它们——触发源
    // 消失，测试断言的是一段不可达的路径。若将来本模组自己的差事需要"做完
    // 归队"，那是新的触发源，届时连同实现一起重建。


    private static void assertWalkTargetOwner(
            GameTestHelper helper,
            Fixture fixture
    ) {
        helper.assertTrue(
                fixture.maid().getBrain()
                        .getMemory(MemoryModuleType.WALK_TARGET)
                        .map(target -> target.getTarget())
                        .filter(EntityTracker.class::isInstance)
                        .map(EntityTracker.class::cast)
                        .map(EntityTracker::getEntity)
                        .filter(fixture.owner()::equals)
                        .isPresent(),
                "Owner return did not retain the owner movement target"
        );
    }

    private static Fixture fixture(GameTestHelper helper) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Player owner = helper.makeMockPlayer();
        owner.setPos(GameTestPositions.center(helper, 6, 2, 1));
        TaskAttack task = new TaskAttack();
        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }

            @Override
            public TaskAttack getTask() {
                return task;
            }
        };
        maid.setPos(GameTestPositions.center(helper, 1, 2, 1));
        maid.setTame(true);
        maid.setTask(
                TaskManager.findTask(FreedomMaidTask.UID).orElseThrow()
        );
        maid.setHomeModeEnable(false);
        helper.getLevel().addFreshEntity(maid);
        maid.getBrain().setActiveActivityIfPossible(Activity.WORK);
        return new Fixture(owner, maid);
    }

    @SuppressWarnings("unchecked")
    private static MaidStatusApi<EntityMaid> status() {
        return (MaidStatusApi<EntityMaid>) AdapterRuntime.require(
                MaidStatusApi.class
        );
    }

    private record Fixture(Player owner, EntityMaid maid) {
    }
}
