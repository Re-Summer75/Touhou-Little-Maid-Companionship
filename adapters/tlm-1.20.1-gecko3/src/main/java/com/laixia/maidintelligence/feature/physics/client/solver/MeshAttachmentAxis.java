package com.laixia.maidintelligence.feature.physics.client.solver;

import org.joml.Vector3f;

record MeshAttachmentAxis(
        Vector3f proximal,
        Vector3f distal,
        float confidence,
        float length,
        float supportConfidence
) {
}
