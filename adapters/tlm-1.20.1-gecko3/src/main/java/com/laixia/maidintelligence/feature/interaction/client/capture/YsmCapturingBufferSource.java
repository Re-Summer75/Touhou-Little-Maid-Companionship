package com.laixia.maidintelligence.feature.interaction.client.capture;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

final class YsmCapturingBufferSource implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final YsmCaptureSession session;

    YsmCapturingBufferSource(
            MultiBufferSource delegate,
            YsmCaptureSession session
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

    private static final class CapturingVertexConsumer
            implements VertexConsumer {
        private final VertexConsumer delegate;
        private final YsmCaptureSession session;
        private final YsmCapturedStream stream;
        private boolean hasPendingVertex;
        private double pendingX;
        private double pendingY;
        private double pendingZ;
        private float pendingNormalX;
        private float pendingNormalY;
        private float pendingNormalZ;

        private CapturingVertexConsumer(
                VertexConsumer delegate,
                YsmCaptureSession session,
                YsmCapturedStream stream
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
