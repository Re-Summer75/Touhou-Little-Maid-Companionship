package com.laixia.maidintelligence.feature.interaction.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.ILocationBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.laixia.maidintelligence.feature.shading.client.ShadowPassDetector;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

public final class YsmFaceTrackingCapture {
    private static final int MAX_VERTICES_PER_STREAM = 200_000;
    private static final int INITIAL_STREAM_CAPACITY = 64;
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
        FaceGeometry.Frame.tryCreate(
                transformedPosition(headPose, 0.0F, 0.0F, 0.0F),
                transformedDirection(headPose, -1.0F, 0.0F, 0.0F),
                transformedDirection(headPose, 0.0F, 1.0F, 0.0F),
                transformedDirection(headPose, 0.0F, 0.0F, -1.0F)
        ).ifPresent(session::setFrame);
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
        Vec3 origin = frame.origin();
        Vec3 right = frame.right();
        Vec3 up = frame.up();
        Vec3 forward = frame.forward();

        List<FaceGeometry.Candidate> candidates = new ArrayList<>();
        List<CapturedStream> streams = session.streams();
        for (int streamIndex = 0; streamIndex < streams.size(); streamIndex++) {
            CapturedStream stream = streams.get(streamIndex);
            double[] positions = stream.positions();
            float[] normals = stream.normals();
            int quadCount = stream.vertexCount() / 4;
            for (int quadIndex = 0; quadIndex < quadCount; quadIndex++) {
                int base = quadIndex * 12;
                // The head-region test only needs the quad centroid, which is
                // permutation-independent, so it can run on the raw capture
                // buffer before any quad ordering or Vec3 boxing happens.
                double centerX = (positions[base]
                        + positions[base + 3]
                        + positions[base + 6]
                        + positions[base + 9]) * 0.25D;
                double centerY = (positions[base + 1]
                        + positions[base + 4]
                        + positions[base + 7]
                        + positions[base + 10]) * 0.25D;
                double centerZ = (positions[base + 2]
                        + positions[base + 5]
                        + positions[base + 8]
                        + positions[base + 11]) * 0.25D;
                double offsetX = centerX - origin.x;
                double offsetY = centerY - origin.y;
                double offsetZ = centerZ - origin.z;
                double horizontal = Math.abs(
                        offsetX * right.x + offsetY * right.y + offsetZ * right.z
                );
                double vertical = offsetX * up.x + offsetY * up.y + offsetZ * up.z;
                double forwardDistance = Math.abs(
                        offsetX * forward.x + offsetY * forward.y + offsetZ * forward.z
                );
                if (horizontal > horizontalLimit
                        || forwardDistance > forwardLimit
                        || vertical < minimumUp
                        || vertical > maximumUp) {
                    continue;
                }

                double normalSumX = (double) normals[base]
                        + normals[base + 3]
                        + normals[base + 6]
                        + normals[base + 9];
                double normalSumY = (double) normals[base + 1]
                        + normals[base + 4]
                        + normals[base + 7]
                        + normals[base + 10];
                double normalSumZ = (double) normals[base + 2]
                        + normals[base + 5]
                        + normals[base + 8]
                        + normals[base + 11];
                double normalLengthSqr = normalSumX * normalSumX
                        + normalSumY * normalSumY
                        + normalSumZ * normalSumZ;
                if (normalLengthSqr <= 1.0E-10D) {
                    continue;
                }

                List<Vec3> quadPositions = List.of(
                        new Vec3(
                                positions[base],
                                positions[base + 1],
                                positions[base + 2]
                        ),
                        new Vec3(
                                positions[base + 3],
                                positions[base + 4],
                                positions[base + 5]
                        ),
                        new Vec3(
                                positions[base + 6],
                                positions[base + 7],
                                positions[base + 8]
                        ),
                        new Vec3(
                                positions[base + 9],
                                positions[base + 10],
                                positions[base + 11]
                        )
                );
                FaceGeometry.OrderedQuad quad = FaceGeometry
                        .orderQuad(quadPositions, frame)
                        .orElse(null);
                if (quad == null) {
                    continue;
                }

                Vec3 outward = new Vec3(normalSumX, normalSumY, normalSumZ)
                        .normalize();
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
                        quadPositions,
                        outward,
                        groupCenter,
                        quad.width(),
                        quad.height(),
                        estimatedDepth,
                        false,
                        quad
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

        private CapturedStream createStream() {
            CapturedStream stream = new CapturedStream();
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
            int total = 0;
            for (CapturedStream stream : streams) {
                total += stream.vertexCount();
            }
            return total;
        }

        private boolean isCapturing() {
            return capturing;
        }

        private void setCapturing(boolean capturing) {
            this.capturing = capturing;
        }
    }

    /**
     * Flat primitive capture buffer: positions keep the emitted double
     * precision, normals are always emitted as floats. One entry per vertex.
     */
    private static final class CapturedStream {
        private double[] positions = new double[INITIAL_STREAM_CAPACITY * 3];
        private float[] normals = new float[INITIAL_STREAM_CAPACITY * 3];
        private int vertexCount;

        private void add(
                double x,
                double y,
                double z,
                float normalX,
                float normalY,
                float normalZ
        ) {
            if (vertexCount >= MAX_VERTICES_PER_STREAM) {
                return;
            }
            int base = vertexCount * 3;
            if (base == positions.length) {
                int grownVertices = Math.min(
                        vertexCount * 2,
                        MAX_VERTICES_PER_STREAM
                );
                positions = Arrays.copyOf(positions, grownVertices * 3);
                normals = Arrays.copyOf(normals, grownVertices * 3);
            }
            positions[base] = x;
            positions[base + 1] = y;
            positions[base + 2] = z;
            normals[base] = normalX;
            normals[base + 1] = normalY;
            normals[base + 2] = normalZ;
            vertexCount++;
        }

        private double[] positions() {
            return positions;
        }

        private float[] normals() {
            return normals;
        }

        private int vertexCount() {
            return vertexCount;
        }
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
                    session.createStream()
            );
        }
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final CaptureSession session;
        private final CapturedStream stream;
        private boolean hasPendingVertex;
        private double pendingX;
        private double pendingY;
        private double pendingZ;
        private float pendingNormalX;
        private float pendingNormalY;
        private float pendingNormalZ;

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
            pendingX = x;
            pendingY = y;
            pendingZ = z;
            pendingNormalX = 0.0F;
            pendingNormalY = 0.0F;
            pendingNormalZ = 0.0F;
            hasPendingVertex = true;
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
            pendingNormalX = x;
            pendingNormalY = y;
            pendingNormalZ = z;
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
            if (hasPendingVertex && session.isCapturing()) {
                stream.add(
                        pendingX,
                        pendingY,
                        pendingZ,
                        pendingNormalX,
                        pendingNormalY,
                        pendingNormalZ
                );
            }
            hasPendingVertex = false;
        }

        // Bulk-vertex fast path: forwards one call instead of the seven the
        // interface default would fan out, and captures without going through
        // the pending-field state machine.
        @Override
        public void vertex(
                float x,
                float y,
                float z,
                float red,
                float green,
                float blue,
                float alpha,
                float u,
                float v,
                int overlay,
                int light,
                float normalX,
                float normalY,
                float normalZ
        ) {
            delegate.vertex(
                    x,
                    y,
                    z,
                    red,
                    green,
                    blue,
                    alpha,
                    u,
                    v,
                    overlay,
                    light,
                    normalX,
                    normalY,
                    normalZ
            );
            if (session.isCapturing()) {
                stream.add(x, y, z, normalX, normalY, normalZ);
            }
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
