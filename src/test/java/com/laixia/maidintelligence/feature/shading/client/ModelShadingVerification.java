package com.laixia.maidintelligence.feature.shading.client;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.Converter;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.pojo.RawGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.raw.tree.RawGeometryTree;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.GeoBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoMesh;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoModel;
import org.joml.Vector3f;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** 离线校验逐面外法线、外向绕序与弱缓存，不创建 OpenGL 上下文。 */
public final class ModelShadingVerification {
    private static final Path MODEL_DIRECTORY = Path.of(
            "geckolib_model_reference",
            "models",
            "entity"
    );
    private static final int[][] VANILLA_FACE_CORNERS = {
            {5, 4, 0, 1},
            {2, 3, 7, 6},
            {1, 0, 3, 2},
            {4, 5, 6, 7},
            {0, 4, 7, 3},
            {5, 1, 2, 6}
    };

    private ModelShadingVerification() {
    }

    public static void main(String[] args) throws Exception {
        verifiesFaceTopologyMatchesTlm();
        verifiesGeometricOutsideNormals();
        verifiesMirroredCubeWinding();
        verifiesBundledModels();
        verifiesWeakTemplateCache();
        System.out.println("Model shading verification passed.");
    }

    private static void verifiesFaceTopologyMatchesTlm() {
        for (int face = 0; face < GeoCubeFaceTable.FACE_COUNT; face++) {
            for (int vertex = 0; vertex < 4; vertex++) {
                require(
                        GeoCubeFaceTable.corner(face, vertex)
                                == VANILLA_FACE_CORNERS[face][vertex],
                        "Face topology diverged from TLM at "
                                + face + "/" + vertex
                );
            }
        }
    }

    private static void verifiesGeometricOutsideNormals() {
        GeoMesh mesh = mesh(
                new Vector3f(),
                new Vector3f(2.0F, 0.0F, 0.0F),
                new Vector3f(0.5F, 3.0F, 0.0F),
                new Vector3f(0.2F, 0.4F, 4.0F),
                false
        );
        GeoMeshNormalTemplate template = GeoMeshNormalBuilder.build(mesh);
        require(
                !template.hasWindingCorrections(),
                "Positive cube unnecessarily left the Sodium fast path"
        );
        requireCubeNormals(mesh, template, 0);
    }

    private static void verifiesMirroredCubeWinding() {
        GeoMesh mesh = mesh(
                new Vector3f(2.0F, 0.0F, 0.0F),
                new Vector3f(-2.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, 3.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, 4.0F),
                true
        );
        GeoMeshNormalTemplate template = GeoMeshNormalBuilder.build(mesh);
        require(
                template.hasWindingCorrections(),
                "Mirrored cube did not request the outside-normal writer"
        );
        for (int face = 0; face < GeoCubeFaceTable.FACE_COUNT; face++) {
            require(
                    template.shouldReverseWinding(0, face),
                    "Mirrored face winding was not reversed outside"
            );
        }
        requireCubeNormals(mesh, template, 0);
    }

    private static void verifiesBundledModels() throws Exception {
        require(
                Files.isDirectory(MODEL_DIRECTORY),
                "Bundled Gecko model directory is missing"
        );
        int modelCount = 0;
        int correctedMeshes = 0;
        try (Stream<Path> paths = Files.list(MODEL_DIRECTORY)) {
            List<Path> models = paths
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path modelPath : models) {
                GeoModel model = loadGeoModel(modelPath);
                for (GeoMesh mesh : collectMeshes(model)) {
                    GeoMeshNormalTemplate template =
                            GeoMeshNormalBuilder.build(mesh);
                    if (template.hasWindingCorrections()) {
                        correctedMeshes++;
                    }
                    for (int cube = 0;
                         cube < mesh.getCubeCount();
                         cube++) {
                        requireCubeNormals(mesh, template, cube);
                    }
                }
                modelCount++;
            }
        }
        require(modelCount >= 27, "Expected at least 27 bundled Gecko models");
        require(
                correctedMeshes > 0,
                "Bundled models did not exercise winding correction"
        );
    }

    private static void requireCubeNormals(
            GeoMesh mesh,
            GeoMeshNormalTemplate template,
            int cube
    ) {
        Vector3f[] corners = corners(mesh, cube);
        Vector3f center = new Vector3f(mesh.position(cube))
                .fma(0.5F, mesh.dx(cube))
                .fma(0.5F, mesh.dy(cube))
                .fma(0.5F, mesh.dz(cube));
        for (int face = 0; face < GeoCubeFaceTable.FACE_COUNT; face++) {
            if ((mesh.faces(cube) & (1 << face)) == 0) {
                continue;
            }
            Vector3f normal = normal(template, cube, face);
            require(isFinite(normal), "Face normal is not finite");
            requireNear(normal.length(), 1.0F, 2.0E-4F, "Normal is not unit");

            Vector3f faceCenter = new Vector3f(
                    corners[GeoCubeFaceTable.diagonalStart(face)]
            ).add(corners[GeoCubeFaceTable.diagonalEnd(face)]).mul(0.5F);
            Vector3f outward = faceCenter.sub(center);
            if (outward.lengthSquared() > 1.0E-12F) {
                require(
                        outward.dot(normal) > 0.0F,
                        "Face normal points into its source cube"
                );
            }

            Vector3f first = corners[GeoCubeFaceTable.corner(face, 0)];
            requirePerpendicular(
                    normal,
                    new Vector3f(
                            corners[GeoCubeFaceTable.corner(face, 1)]
                    ).sub(first)
            );
            requirePerpendicular(
                    normal,
                    new Vector3f(
                            corners[GeoCubeFaceTable.corner(face, 3)]
                    ).sub(first)
            );
        }
    }

    private static void requirePerpendicular(
            Vector3f normal,
            Vector3f edge
    ) {
        if (edge.lengthSquared() <= 1.0E-12F) {
            return;
        }
        requireNear(
                Math.abs(normal.dot(edge.normalize())),
                0.0F,
                2.0E-4F,
                "Face normal is not perpendicular to its edge"
        );
    }

    private static void verifiesWeakTemplateCache() {
        GeoMeshNormalCache.clear();
        GeoMesh mesh = mesh(
                new Vector3f(),
                new Vector3f(1.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, 1.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, 1.0F),
                false
        );
        GeoMeshNormalTemplate first = GeoMeshNormalCache.getOrBuild(mesh);
        GeoMeshNormalTemplate second = GeoMeshNormalCache.getOrBuild(mesh);
        require(first == second, "Normal template cache missed by identity");
        require(GeoMeshNormalCache.size() == 1, "Unexpected cache size");
        GeoMeshNormalCache.clear();
        require(GeoMeshNormalCache.size() == 0, "Normal cache did not clear");
    }

    private static GeoModel loadGeoModel(Path path) throws Exception {
        RawGeoModel raw;
        try (InputStream input = Files.newInputStream(path)) {
            raw = Converter.fromInputStream(input);
        }
        return GeoBuilder.getGeoBuilder().constructGeoModel(
                RawGeometryTree.parseHierarchy(raw)
        );
    }

    private static List<GeoMesh> collectMeshes(GeoModel model) {
        List<GeoMesh> output = new ArrayList<>();
        for (GeoBone bone : model.topLevelBones()) {
            collectMeshes(bone, output);
        }
        return output;
    }

    private static void collectMeshes(GeoBone bone, List<GeoMesh> output) {
        output.add(bone.cubes());
        for (GeoBone child : bone.children()) {
            collectMeshes(child, output);
        }
    }

    private static GeoMesh mesh(
            Vector3f position,
            Vector3f dx,
            Vector3f dy,
            Vector3f dz,
            boolean mirrored
    ) {
        return new GeoMesh(
                1,
                new int[]{0b111111 | (mirrored ? 0b1000000 : 0)},
                new Vector3f[]{new Vector3f(position)},
                new Vector3f[]{new Vector3f(dx)},
                new Vector3f[]{new Vector3f(dy)},
                new Vector3f[]{new Vector3f(dz)},
                new float[GeoMesh.FACE_COUNT],
                new float[GeoMesh.FACE_COUNT],
                new float[GeoMesh.FACE_COUNT],
                new float[GeoMesh.FACE_COUNT]
        );
    }

    private static Vector3f[] corners(GeoMesh mesh, int cube) {
        Vector3f origin = mesh.position(cube);
        Vector3f dx = mesh.dx(cube);
        Vector3f dy = mesh.dy(cube);
        Vector3f dz = mesh.dz(cube);
        return new Vector3f[]{
                new Vector3f(origin),
                new Vector3f(origin).add(dx),
                new Vector3f(origin).add(dx).add(dy),
                new Vector3f(origin).add(dy),
                new Vector3f(origin).add(dz),
                new Vector3f(origin).add(dx).add(dz),
                new Vector3f(origin).add(dx).add(dy).add(dz),
                new Vector3f(origin).add(dy).add(dz)
        };
    }

    private static Vector3f normal(
            GeoMeshNormalTemplate template,
            int cube,
            int face
    ) {
        return new Vector3f(
                template.normalX(cube, face),
                template.normalY(cube, face),
                template.normalZ(cube, face)
        );
    }

    private static boolean isFinite(Vector3f value) {
        return Float.isFinite(value.x)
                && Float.isFinite(value.y)
                && Float.isFinite(value.z);
    }

    private static void requireNear(
            float actual,
            float expected,
            float tolerance,
            String message
    ) {
        require(
                Math.abs(actual - expected) <= tolerance,
                message + ": expected " + expected + ", got " + actual
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
