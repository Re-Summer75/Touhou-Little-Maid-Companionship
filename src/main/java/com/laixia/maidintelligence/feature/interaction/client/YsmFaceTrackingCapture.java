package com.laixia.maidintelligence.feature.interaction.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.ILocationBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class YsmFaceTrackingCapture {
    private static final int MAX_VERTICES_PER_STREAM = 200_000;
    private static final ThreadLocal<Deque<CaptureSession>> SESSIONS =
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
        if (!(entity instanceof EntityMaid maid) || !maid.isAddedToWorld()) {
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

        CaptureSession session = new CaptureSession(maid);
        Deque<CaptureSession> stack = SESSIONS.get();
        stack.push(session);
        try {
            renderer.geoRender(
                    entity,
                    entityYaw,
                    partialTick,
                    poseStack,
                    new CapturingMultiBufferSource(buffers, session),
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
        Deque<CaptureSession> stack = SESSIONS.get();
        if (stack.isEmpty() || headBones.isEmpty()) {
            return;
        }
        CaptureSession session = stack.peek();
        if (session.maid() != maid) {
            return;
        }

        PoseStack headPose = copyPose(layerPose);
        RenderUtils.prepMatrixForLocator(headPose, headBones);
        session.setFrame(new FaceGeometry.Frame(
                transformedPosition(headPose, 0.0F, 0.0F, 0.0F),
                transformedDirection(headPose, -1.0F, 0.0F, 0.0F),
                transformedDirection(headPose, 0.0F, 1.0F, 0.0F),
                transformedDirection(headPose, 0.0F, 0.0F, -1.0F)
        ));
    }

    public static void beginLayerSection(EntityMaid maid) {
        CaptureSession session = currentSession(maid);
        if (session != null) {
            session.setCapturing(false);
        }
    }

    public static void endLayerSection(EntityMaid maid) {
        CaptureSession session = currentSession(maid);
        if (session != null) {
            session.setCapturing(true);
        }
    }

    private static CaptureSession currentSession(EntityMaid maid) {
        Deque<CaptureSession> stack = SESSIONS.get();
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
            CaptureSession session
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

        List<FaceGeometry.Candidate> candidates = collectCandidates(session, maid);
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

        FaceGeometry.Selection selection = FaceCandidateSelector.select(
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

    private static List<FaceGeometry.Candidate> collectCandidates(
            CaptureSession session,
            EntityMaid maid
    ) {
        FaceGeometry.Frame frame = session.frame();
        double entityHeight = Math.max(maid.getBbHeight(), 1.0D);
        double horizontalLimit = entityHeight * 0.55D;
        double forwardLimit = entityHeight * 0.55D;
        double minimumUp = -entityHeight * 0.18D;
        double maximumUp = entityHeight * 0.65D;

        List<FaceGeometry.Candidate> candidates = new ArrayList<>();
        for (int streamIndex = 0; streamIndex < session.streams().size(); streamIndex++) {
            CapturedStream stream = session.streams().get(streamIndex);
            List<CapturedVertex> vertices = stream.vertices();
            int quadCount = vertices.size() / 4;
            for (int quadIndex = 0; quadIndex < quadCount; quadIndex++) {
                List<CapturedVertex> captured = vertices.subList(
                        quadIndex * 4,
                        quadIndex * 4 + 4
                );
                List<Vec3> positions = captured.stream()
                        .map(CapturedVertex::position)
                        .toList();
                FaceGeometry.OrderedQuad quad = FaceGeometry
                        .orderQuad(positions, frame)
                        .orElse(null);
                if (quad == null) {
                    continue;
                }

                Vec3 offset = quad.center().subtract(frame.origin());
                double horizontal = Math.abs(offset.dot(frame.right()));
                double vertical = offset.dot(frame.up());
                double forward = Math.abs(offset.dot(frame.forward()));
                if (horizontal > horizontalLimit
                        || forward > forwardLimit
                        || vertical < minimumUp
                        || vertical > maximumUp) {
                    continue;
                }

                Vec3 outward = averageNormal(captured);
                if (outward.lengthSqr() <= 1.0E-10D) {
                    continue;
                }
                double estimatedDepth = Math.max(
                        Math.min(quad.width(), quad.height()),
                        1.0E-4D
                );
                Vec3 groupCenter = quad.center().subtract(
                        outward.normalize().scale(estimatedDepth * 0.5D)
                );
                candidates.add(new FaceGeometry.Candidate(
                        new FaceGeometry.Key(
                                FaceGeometry.Source.YSM,
                                "ysm/stream_" + streamIndex,
                                quadIndex,
                                0
                        ),
                        FaceBoneClassifier.Role.HEAD,
                        positions,
                        outward,
                        groupCenter,
                        quad.width(),
                        quad.height(),
                        estimatedDepth,
                        false
                ));
            }
        }
        return candidates;
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

    private static Vec3 averageNormal(List<CapturedVertex> vertices) {
        Vec3 normal = Vec3.ZERO;
        for (CapturedVertex vertex : vertices) {
            normal = normal.add(vertex.normal());
        }
        return normal.lengthSqr() <= 1.0E-10D
                ? Vec3.ZERO
                : normal.normalize();
    }

    private static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.last().pose().set(source.last().pose());
        copy.last().normal().set(source.last().normal());
        return copy;
    }

    private static Vec3 transformedPosition(
            PoseStack pose,
            float x,
            float y,
            float z
    ) {
        Vector3f transformed = new Vector3f(x, y, z)
                .mulPosition(pose.last().pose());
        return new Vec3(transformed.x(), transformed.y(), transformed.z());
    }

    private static Vec3 transformedDirection(
            PoseStack pose,
            float x,
            float y,
            float z
    ) {
        Vector3f transformed = new Vector3f(x, y, z)
                .mulDirection(pose.last().pose())
                .normalize();
        return new Vec3(transformed.x(), transformed.y(), transformed.z());
    }

    private static final class CaptureSession {
        private final EntityMaid maid;
        private final List<CapturedStream> streams = new ArrayList<>();
        private FaceGeometry.Frame frame;
        private boolean capturing = true;

        private CaptureSession(EntityMaid maid) {
            this.maid = maid;
        }

        private EntityMaid maid() {
            return maid;
        }

        private List<CapturedStream> streams() {
            return streams;
        }

        private CapturedStream createStream(RenderType renderType) {
            CapturedStream stream = new CapturedStream(renderType);
            streams.add(stream);
            return stream;
        }

        private FaceGeometry.Frame frame() {
            return frame;
        }

        private void setFrame(FaceGeometry.Frame frame) {
            this.frame = frame;
        }

        private int totalVertexCount() {
            return streams.stream()
                    .mapToInt(stream -> stream.vertices().size())
                    .sum();
        }

        private boolean isCapturing() {
            return capturing;
        }

        private void setCapturing(boolean capturing) {
            this.capturing = capturing;
        }
    }

    private record CapturedStream(
            RenderType renderType,
            List<CapturedVertex> vertices
    ) {
        private CapturedStream(RenderType renderType) {
            this(renderType, new ArrayList<>());
        }
    }

    private record CapturedVertex(Vec3 position, Vec3 normal) {
    }

    private static final class CapturingMultiBufferSource
            implements MultiBufferSource {
        private final MultiBufferSource delegate;
        private final CaptureSession session;

        private CapturingMultiBufferSource(
                MultiBufferSource delegate,
                CaptureSession session
        ) {
            this.delegate = delegate;
            this.session = session;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            VertexConsumer target = delegate.getBuffer(renderType);
            return new CapturingVertexConsumer(
                    target,
                    session,
                    session.createStream(renderType)
            );
        }
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final CaptureSession session;
        private final CapturedStream stream;
        private Vec3 pendingPosition;
        private Vec3 pendingNormal = Vec3.ZERO;

        private CapturingVertexConsumer(
                VertexConsumer delegate,
                CaptureSession session,
                CapturedStream stream
        ) {
            this.delegate = delegate;
            this.session = session;
            this.stream = stream;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            pendingPosition = new Vec3(x, y, z);
            pendingNormal = Vec3.ZERO;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            pendingNormal = new Vec3(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
            if (pendingPosition != null
                    && streamCaptureEnabled()
                    && stream.vertices().size() < MAX_VERTICES_PER_STREAM) {
                stream.vertices().add(new CapturedVertex(
                        pendingPosition,
                        pendingNormal
                ));
            }
            pendingPosition = null;
            pendingNormal = Vec3.ZERO;
        }

        private boolean streamCaptureEnabled() {
            return session.isCapturing();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
