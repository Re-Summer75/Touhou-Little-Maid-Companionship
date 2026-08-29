package com.laixia.maidintelligence.gametest.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.laixia.maidintelligence.feature.behavior.tlm.freedom.FreedomMaidTask;
import com.laixia.maidintelligence.gametest.support.CombatTrace;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 仗被打断之后，她还回得来吗——战后余波的钉子。
 *
 * <p>玩家稳定复现并指定的场景：滞空独径平台八格，持剑女仆锁定卫道士，
 * 一股力把卫道士推下平台——敌人**活着**脱离（不是死在感知内），之后她
 * 完全静止、任何行为都不再执行，直到手动更新状态。这里一比一照建，全程
 * {@code CombatTrace} 逐 tick 记录（坐标、意图、判决、记忆），无论过挂
 * 都打印，战后拿"她还找不找主人"验收。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class CombatAftermathGameTests {
    private CombatAftermathGameTests() {
    }

    /** 卫道士被推下滞空平台后，她必须放下这一仗、还找得到主人。 */
    @GameTest(batch = "aftermath", templateNamespace = "minecraft",
            template = "empty", timeoutTicks = 700)
    public static void aFoeShovedOffThePlatformDoesNotFreezeHer(
            GameTestHelper helper
    ) {
        // 地面房：卫道士落下去站的地方，主人也站在这层。
        for (int x = 0; x <= 11; x++) {
            for (int z = 0; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        // 滞空独径平台：八格长一格宽，高出地面八格。
        for (int x = 2; x <= 9; x++) {
            helper.setBlock(new BlockPos(x, 9, 2), Blocks.STONE);
        }

        Player owner = helper.makeMockPlayer();
        BlockPos zero = helper.absolutePos(BlockPos.ZERO);
        owner.setPos(zero.getX() + 10.5D, zero.getY() + 2.0D,
                zero.getZ() + 4.5D);

        com.laixia.maidintelligence.gametest.support.world.StrayMaids.sweep(helper);
        EntityMaid maid = new EntityMaid(helper.getLevel()) {
            @Override
            public LivingEntity getOwner() {
                return owner;
            }
        };
        maid.setPos(zero.getX() + 3.5D, zero.getY() + 10.0D,
                zero.getZ() + 2.5D);
        maid.setTame(true);
        maid.setPickup(false);
        maid.setTask(TaskManager.findTask(FreedomMaidTask.UID).orElseThrow());
        maid.setHomeModeEnable(false);
        maid.setOwnerUUID(owner.getUUID());
        maid.getAvailableBackpackInv().setStackInSlot(
                0, new ItemStack(Items.IRON_SWORD)
        );
        helper.getLevel().addFreshEntity(maid);

        Vindicator foe = EntityType.VINDICATOR.create(helper.getLevel());
        if (foe == null) {
            throw new IllegalStateException("夹具无法创建卫道士");
        }
        foe.setPos(zero.getX() + 8.5D, zero.getY() + 10.0D,
                zero.getZ() + 2.5D);
        foe.setTarget(maid);
        foe.setPersistenceRequired();
        foe.restrictTo(foe.blockPosition(), 12);
        helper.getLevel().addFreshEntity(foe);

        CombatTrace trace = new CombatTrace("aftermath: shoved-off foe");
        boolean[] engaged = new boolean[]{false};
        boolean[] shoved = new boolean[]{false};
        Vec3[] afterSettle = new Vec3[]{null};

        for (int tick = 1; tick <= 600; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                trace.sample(at, maid, foe);
                if (!engaged[0] && maid.getBrain()
                        .getMemory(MemoryModuleType.ATTACK_TARGET)
                        .isPresent()) {
                    engaged[0] = true;
                }
                // 交战确立后（最晚一百二十 tick）：一股力把卫道士推下平台。
                if (!shoved[0] && (engaged[0] || at >= 120) && at >= 40) {
                    shoved[0] = true;
                    foe.setDeltaMovement(0.0D, 0.35D, 1.1D);
                    foe.hurtMarked = true;
                }
                // 复刻实机竞态的后半：坠落的卫道士在**感知之外死掉**（玩家
                // 复现里它摔死在三十二格下）。取消那一瞬它还活着还在感知内，
                // 一次性的清理已经放过它——此后只有每 tick 的门房救得了。
                if (shoved[0] && at == 260 && foe.isAlive()) {
                    foe.teleportTo(foe.getX(), foe.getY() - 24.0D,
                            foe.getZ() + 6.0D);
                    foe.kill();
                }
                // 落地尘埃落定后记一个基准点，战后位移拿它量。
                if (shoved[0] && afterSettle[0] == null && at >= 300) {
                    afterSettle[0] = maid.position();
                }
            });
        }

        helper.runAfterDelay(620, () -> {
            trace.dump();
            helper.assertTrue(
                    engaged[0],
                    "She never engaged the vindicator; the scene proves"
                            + " nothing. " + trace.summary()
            );
            boolean released = maid.getBrain()
                    .getMemory(MemoryModuleType.ATTACK_TARGET)
                    .isEmpty();
            double moved = afterSettle[0] == null ? 0.0D
                    : maid.position().distanceTo(afterSettle[0]);
            double toOwner = maid.distanceTo(owner);
            helper.assertTrue(
                    released,
                    "The shoved-off foe was never let go of: ATTACK_TARGET"
                            + " still set, foe at "
                            + foe.position() + " alive=" + foe.isAlive()
                            + " dist=" + String.format("%.1f",
                                    maid.distanceTo(foe))
                            + ". " + trace.summary()
            );
            helper.assertTrue(
                    moved > 2.5D || toOwner < 5.0D,
                    "After the fight broke she never went back to living:"
                            + " moved=" + String.format("%.1f", moved)
                            + " toOwner=" + String.format("%.1f", toOwner)
                            + ". " + trace.summary()
            );
            maid.discard();
            foe.discard();
            helper.succeed();
        });
    }
}
