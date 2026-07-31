package com.laixia.maidintelligence.feature.interaction.client.capture;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.laixia.maidintelligence.feature.interaction.domain.FaceGeometry;

import java.util.ArrayList;
import java.util.List;

final class YsmCaptureSession {
    private final EntityMaid maid;
    private final List<YsmCapturedStream> streams = new ArrayList<>();
    private FaceGeometry.Frame frame;
    private boolean capturing = true;

    YsmCaptureSession(EntityMaid maid) {
        this.maid = maid;
    }

    EntityMaid maid() {
        return maid;
    }

    List<YsmCapturedStream> streams() {
        return streams;
    }

    YsmCapturedStream createStream() {
        YsmCapturedStream stream = new YsmCapturedStream();
        streams.add(stream);
        return stream;
    }

    FaceGeometry.Frame frame() {
        return frame;
    }

    void setFrame(FaceGeometry.Frame frame) {
        this.frame = frame;
    }

    int totalVertexCount() {
        int total = 0;
        for (YsmCapturedStream stream : streams) {
            total += stream.vertexCount();
        }
        return total;
    }

    boolean isCapturing() {
        return capturing;
    }

    void setCapturing(boolean capturing) {
        this.capturing = capturing;
    }
}
