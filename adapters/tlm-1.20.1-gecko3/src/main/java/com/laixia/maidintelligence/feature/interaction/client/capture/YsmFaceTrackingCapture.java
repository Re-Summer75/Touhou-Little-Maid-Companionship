package com.laixia.maidintelligence.feature.interaction.client.capture;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.ILocationBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.laixia.maidintelligence.feature.interaction.api.FaceSelectionApi;
import com.laixia.maidintelligence.feature.interaction.application.FaceCandidateSelector;
import com.laixia.maidintelligence.feature.interaction.client.render.FaceVertexMarkerRenderer;
import com.laixia.maidintelligence.feature.interaction.client.tracking.DynamicMaidFaceTracker;
import com.laixia.maidintelligence.feature.interaction.client.tracking.FaceTrackingDemand;
import com.laixia.maidintelligence.feature.interaction.client.tracking.FaceTrackingGeometryCache;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;
import com.laixia.maidintelligence.feature.interaction.domain.MaidFacePlane;
import com.laixia.maidintelligence.feature.shading.client.ShadowPassDetector;
import com.laixia.maidintelligence.shared.geometry.Vec3d;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class YsmFaceTrackingCapture {
    private static final FaceSelectionApi FACE_SELECTOR =
            new FaceCandidateSelector();
    private static final ThreadLocal<Deque<YsmCaptureSession>> SESSIONS =
            ThreadLocal.withInitial(ArrayDeque::new);

    private YsmFaceTrackingCapture() {
    }

    public static <T extends Entity> void render(
            IGeoEntityRenderer<T> renderer,
            T entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        if (ShadowPassDetector.isActive()
                || !(entity instanceof EntityMaid maid)
                || !maid.isAddedToWorld()
                || !FaceTrackingDemand.shouldTrack(maid)) {
            renderer.geoRender(
                    entity,
                    entityYaw,
                    partialTick,
                    poseStack,
                    buffers,
                    packedLight
            );
            return;
        }

        YsmCaptureSession session = new YsmCaptureSession(maid);
        Deque<YsmCaptureSession> stack = SESSIONS.get();
        stack.push(session);
        try {
            renderer.geoRender(
                    entity,
                    entityYaw,
                    partialTick,
                    poseStack,
                    new YsmCapturingBufferSource(buffers, session),
                    packedLight
            );
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                SESSIONS.remove();
            }
        }
        finish(renderer, entity, maid, buffers, session);
    }

    public static void captureHeadFrame(
            EntityMaid maid,
            PoseStack layerPose,
            List<? extends ILocationBone> headBones
    ) {
        Deque<YsmCaptureSession> stack = SESSIONS.get();
        if (stack.isEmpty() || headBones.isEmpty()) {
            return;
        }
        YsmCaptureSession session = stack.peek();
        if (session.maid() != maid) {
            return;
        }

        PoseStack headPose = copyPose(layerPose);
        RenderUtils.prepMatrixForLocator(headPose, headBones);
        FaceGeometry.Frame.tryCreate(
                transformedPosition(headPose, 0.0F, 0.0F, 0.0F),
                transformedDirection(headPose, -1.0F, 0.0F, 0.0F),
                transformedDirection(headPose, 0.0F, 1.0F, 0.0F),
                transformedDirection(headPose, 0.0F, 0.0F, -1.0F)
        ).ifPresent(session::setFrame);
    }

    public static void beginLayerSection(EntityMaid maid) {
        YsmCaptureSession session = currentSession(maid);
        if (session != null) {
            session.setCapturing(false);
        }
    }

    public static void endLayerSection(EntityMaid maid) {
        YsmCaptureSession session = currentSession(maid);
        if (session != null) {
            session.setCapturing(true);
        }
    }

    private static YsmCaptureSession currentSession(EntityMaid maid) {
        Deque<YsmCaptureSession> stack = SESSIONS.get();
        if (stack.isEmpty() || stack.peek().maid() != maid) {
            return null;
        }
        return stack.peek();
    }

    private static <T extends Entity> void finish(
            IGeoEntityRenderer<T> renderer,
            T entity,
            EntityMaid maid,
            MultiBufferSource buffers,
            YsmCaptureSession session
    ) {
        Object model = renderer.getGeoEntity(entity).getGeoModel();
        if (session.frame() == null) {
            fail(
                    model,
                    maid,
                    FaceGeometry.FailureReason.NO_HEAD_ANCHOR
            );
            return;
        }

        List<FaceGeometry.Candidate> candidates =
                YsmFaceCandidateCollector.collect(session, maid);
        if (candidates.isEmpty()) {
            fail(
                    model,
                    maid,
                    session.totalVertexCount() == 0
                            ? FaceGeometry.FailureReason.UNSUPPORTED_GEOMETRY_SOURCE
                            : FaceGeometry.FailureReason.NO_GEOMETRY
            );
            return;
        }

        FaceGeometry.Selection selection = FACE_SELECTOR.select(
                candidates,
                session.frame()
        );
        if (!selection.isAccepted()) {
            fail(model, maid, selection.failureReason());
            return;
        }

        FaceGeometry.RankedCandidate selected = selection.ranked().get(0);
        MaidFacePlane plane = MaidFacePlane
                .fromOrderedQuad(selected.quad())
                .orElse(null);
        if (plane == null) {
            fail(model, maid, FaceGeometry.FailureReason.NO_GEOMETRY);
            return;
        }
        DynamicMaidFaceTracker.update(maid, plane);
        FaceVertexMarkerRenderer.render(
                buffers,
                plane,
                FaceGeometry.Source.YSM,
                selected.confidence()
        );
        FaceTrackingGeometryCache.reportSuccessOnce(
                model,
                maid.getYsmModelId(),
                FaceGeometry.Source.YSM,
                selected.candidate().key(),
                selected.confidence()
        );
    }

    private static void fail(
            Object model,
            EntityMaid maid,
            FaceGeometry.FailureReason reason
    ) {
        DynamicMaidFaceTracker.invalidate(maid);
        FaceTrackingGeometryCache.reportFailureOnce(
                model == null ? maid : model,
                maid.getYsmModelId(),
                FaceGeometry.Source.YSM,
                reason
        );
    }

    private static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    private static Vec3d transformedPosition(
            PoseStack pose,
            float x,
            float y,
            float z
    ) {
        Vector3f transformed = new Vector3f(x, y, z)
                .mulPosition(pose.last().pose());
        return new Vec3d(
                transformed.x(),
                transformed.y(),
                transformed.z()
        );
    }

    private static Vec3d transformedDirection(
            PoseStack pose,
            float x,
            float y,
            float z
    ) {
        Vector3f transformed = new Vector3f(x, y, z)
                .mulDirection(pose.last().pose())
                .normalize();
        return new Vec3d(
                transformed.x(),
                transformed.y(),
                transformed.z()
        );
    }
}
