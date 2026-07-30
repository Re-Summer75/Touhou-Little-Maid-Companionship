package com.laixia.maidintelligence.feature.interaction.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BreakingItemParticle;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class MaidEatingParticleEffect {
    private static final int PARTICLE_COUNT = 5;
    private static final float PARTICLE_SCALE = 0.75F;
    private static final double SIDE_SPREAD = 0.18D;
    private static final double FORWARD_SPEED = 0.03D;
    private static final double FORWARD_VARIATION = 0.015D;
    private static final double DOWNWARD_45_DEGREES_COMPONENT = Math.sqrt(0.5D);
    private static final int MAX_TRACKED_FACE_WAIT_TICKS = 20;
    private static final List<PendingTrackedFaceEffect> PENDING_TRACKED_FACE_EFFECTS =
            new ArrayList<>();

    private MaidEatingParticleEffect() {
    }

    public static void spawn(
            ItemStack food,
            Vec3 worldCenter,
            Vec3 worldNormal
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || food.isEmpty() || worldNormal.lengthSqr() <= 1.0E-8D) {
            return;
        }

        Vec3 forward = tiltDownward45Degrees(worldNormal.normalize());
        Vec3 upReference = Math.abs(forward.y) < 0.9D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 right = upReference.cross(forward).normalize();
        Vec3 up = forward.cross(right).normalize();
        RandomSource random = level.random;

        for (int i = 0; i < PARTICLE_COUNT; i++) {
            double rightSpeed = (random.nextFloat() - 0.5F) * SIDE_SPREAD;
            double upSpeed = (random.nextFloat() - 0.5F) * SIDE_SPREAD;
            double forwardSpeed = FORWARD_SPEED
                    + random.nextDouble() * FORWARD_VARIATION;
            Vec3 velocity = forward.scale(forwardSpeed)
                    .add(right.scale(rightSpeed))
                    .add(up.scale(upSpeed));
            minecraft.particleEngine.add(new SmallFoodParticle(
                    level,
                    worldCenter,
                    velocity,
                    food
            ));
        }
    }

    public static void spawnFromTrackedFace(int maidEntityId, ItemStack food) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || food.isEmpty()) {
            return;
        }
        if (!trySpawnFromTrackedFace(level, maidEntityId, food)) {
            for (PendingTrackedFaceEffect pending : PENDING_TRACKED_FACE_EFFECTS) {
                if (pending.level == level && pending.maidEntityId == maidEntityId) {
                    pending.food = food.copy();
                    pending.remainingTicks = MAX_TRACKED_FACE_WAIT_TICKS;
                    return;
                }
            }
            PENDING_TRACKED_FACE_EFFECTS.add(new PendingTrackedFaceEffect(
                    level,
                    maidEntityId,
                    food.copy(),
                    MAX_TRACKED_FACE_WAIT_TICKS
            ));
        }
    }

    public static void tickPendingEffects() {
        ClientLevel currentLevel = Minecraft.getInstance().level;
        Iterator<PendingTrackedFaceEffect> iterator =
                PENDING_TRACKED_FACE_EFFECTS.iterator();
        while (iterator.hasNext()) {
            PendingTrackedFaceEffect pending = iterator.next();
            if (currentLevel == null || pending.level != currentLevel) {
                iterator.remove();
                continue;
            }
            if (trySpawnFromTrackedFace(
                    currentLevel,
                    pending.maidEntityId,
                    pending.food
            ) || --pending.remainingTicks <= 0) {
                iterator.remove();
            }
        }
    }

    public static void clearPendingEffects() {
        PENDING_TRACKED_FACE_EFFECTS.clear();
    }

    static boolean hasPendingTrackedFaceEffect(EntityMaid maid) {
        if (PENDING_TRACKED_FACE_EFFECTS.isEmpty()) {
            return false;
        }
        int maidEntityId = maid.getId();
        for (PendingTrackedFaceEffect pending : PENDING_TRACKED_FACE_EFFECTS) {
            if (pending.maidEntityId == maidEntityId
                    && pending.level == maid.level()) {
                return true;
            }
        }
        return false;
    }

    private static boolean trySpawnFromTrackedFace(
            ClientLevel level,
            int maidEntityId,
            ItemStack food
    ) {
        Entity entity = level.getEntity(maidEntityId);
        if (entity == null) {
            return false;
        }
        if (!(entity instanceof EntityMaid maid)) {
            return true;
        }
        if (!maid.isUsingItem()) {
            return true;
        }
        return DynamicMaidFaceTracker.getTrackedFace(maid)
                .map(face -> {
                    spawn(food, face.worldCenter(), face.worldNormal());
                    return true;
                })
                .orElse(false);
    }

    private static Vec3 tiltDownward45Degrees(Vec3 faceNormal) {
        Vec3 worldDown = new Vec3(0.0D, -1.0D, 0.0D);
        Vec3 downAlongFace = worldDown.subtract(
                faceNormal.scale(worldDown.dot(faceNormal))
        );
        if (downAlongFace.lengthSqr() <= 1.0E-8D) {
            return faceNormal;
        }
        return faceNormal.scale(DOWNWARD_45_DEGREES_COMPONENT)
                .add(downAlongFace.normalize().scale(DOWNWARD_45_DEGREES_COMPONENT))
                .normalize();
    }

    private static final class PendingTrackedFaceEffect {
        private final ClientLevel level;
        private final int maidEntityId;
        private ItemStack food;
        private int remainingTicks;

        private PendingTrackedFaceEffect(
                ClientLevel level,
                int maidEntityId,
                ItemStack food,
                int remainingTicks
        ) {
            this.level = level;
            this.maidEntityId = maidEntityId;
            this.food = food;
            this.remainingTicks = remainingTicks;
        }
    }

    private static final class SmallFoodParticle extends BreakingItemParticle {
        private SmallFoodParticle(
                ClientLevel level,
                Vec3 position,
                Vec3 velocity,
                ItemStack food
        ) {
            super(level, position.x, position.y, position.z, food);
            setParticleSpeed(velocity.x, velocity.y, velocity.z);
            scale(PARTICLE_SCALE);
        }
    }
}
