package com.laixia.maidintelligence.feature.physics.client;

import com.google.gson.JsonParser;
import com.laixia.maidintelligence.feature.physics.api.PhysicsBoneSelectionPlan;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadataJsonParser;
import com.laixia.maidintelligence.feature.physics.discovery.PhysicsBoneDiscoverer;
import com.laixia.maidintelligence.feature.physics.engine.SpringBoneSolver;
import com.laixia.maidintelligence.feature.physics.geometry.BoneModelSnapshot;
import com.laixia.maidintelligence.feature.physics.layout.PhysicsSolverLayout;
import com.laixia.maidintelligence.feature.physics.layout.SkirtBranchConstraintLayout;
import com.laixia.maidintelligence.feature.physics.metadata.PhysicsMetadata;
import org.joml.Vector3f;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.MODEL_DIRECTORY;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.coreModelFromJson;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.loadGeoModel;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;

/**
 * Verifies that adjacent skirt roots stay connected without moving in lockstep.
 */
final class SkirtBranchCouplingVerification {
    private static final float DT = 1.0F / 60.0F;
    private static final int FRAMES = 720;
    private static final Vector3f ZERO = new Vector3f();
    private static final Vector3f WIND = new Vector3f(0.32F, 0.0F, 0.08F);

    private SkirtBranchCouplingVerification() {
    }

    static void run() throws Exception {
        verifiesLayoutOnlyLinksSiblingSkirtRoots();
        verifiesBundledSkirtsReceiveTethers();
        verifiesStrongWindCannotSplitAdjacentPanels();
    }

    private static void verifiesBundledSkirtsReceiveTethers()
            throws Exception {
        require(
                bundledPairCount("winefox.json") >= 6,
                "Segmented Winefox skirt did not receive branch tethers"
        );
        require(
                bundledPairCount("winefox_salesperson.json") >= 3,
                "Four-panel Winefox skirt did not receive branch tethers"
        );
    }

    private static int bundledPairCount(String fileName) throws Exception {
        BoneModelSnapshot model = coreModel(loadGeoModel(
                MODEL_DIRECTORY.resolve(fileName)
        ));
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:skirt_tethers:" + fileName,
                model,
                PhysicsMetadata.EMPTY
        );
        return PhysicsSolverLayout.build(model, plan)
                .skirtBranchConstraints()
                .pairCount();
    }

    private static void verifiesLayoutOnlyLinksSiblingSkirtRoots() {
        Fixture fixture = fixture(true);
        SkirtBranchConstraintLayout constraints =
                fixture.layout().skirtBranchConstraints();
        require(
                constraints.pairCount() == 2,
                "Three sibling skirt roots did not produce two tethers: "
                        + constraints.pairCount()
        );
        for (int index = 0; index < constraints.pairCount(); index++) {
            SkirtBranchConstraintLayout.Pair pair = constraints.pair(index);
            PhysicsSolverLayout.Node leader =
                    fixture.layout().node(pair.leaderNodeIndex());
            PhysicsSolverLayout.Node follower =
                    fixture.layout().node(pair.followerNodeIndex());
            require(
                    pair.leaderNodeIndex() < pair.followerNodeIndex()
                            && leader.parentIndex() == follower.parentIndex()
                            && leader.decision().type()
                            == PhysicsBoneSelectionPlan.PartType.SKIRT
                            && follower.decision().type()
                            == PhysicsBoneSelectionPlan.PartType.SKIRT,
                    "Skirt tether crossed mount, type, or solver order"
            );
        }
        require(
                fixture.layout().skirtBranchConstraints()
                        .pairForFollowerNode(fixture.hairNode()) < 0,
                "A sibling hair strand was treated as skirt cloth"
        );
        require(
                fixture(false, false).layout().skirtBranchConstraints()
                        .pairCount() == 0,
                "Skirt roots under separate mounts were coupled"
        );
    }

    private static void verifiesStrongWindCannotSplitAdjacentPanels() {
        Fixture coupled = fixture(true);
        Fixture independent = fixture(false);
        int[][] edges = couplingEdges(coupled);
        MotionResult coupledMotion = simulate(coupled, edges);
        MotionResult independentMotion = simulate(independent, edges);
        require(
                independentMotion.maximumStretchPixels() > 0.75F,
                "The control skirt did not reproduce branch splitting: "
                        + independentMotion.maximumStretchPixels()
        );
        require(
                coupledMotion.tetherFrames() > 0,
                "The sibling skirt never exercised its branch tethers"
        );
        require(
                coupledMotion.maximumStretchPixels()
                        < independentMotion.maximumStretchPixels() * 0.65F,
                "Skirt tethers did not contain branch splitting: coupled="
                        + coupledMotion.maximumStretchPixels()
                        + " px, control="
                        + independentMotion.maximumStretchPixels() + " px"
        );
        require(
                coupledMotion.maximumDirectionDifference() > 0.005F,
                "Skirt tethers collapsed independent flutter into lockstep"
        );
    }

    private static int[][] couplingEdges(Fixture fixture) {
        SkirtBranchConstraintLayout constraints =
                fixture.layout().skirtBranchConstraints();
        int[][] edges = new int[constraints.pairCount()][2];
        for (int edge = 0; edge < constraints.pairCount(); edge++) {
            SkirtBranchConstraintLayout.Pair pair = constraints.pair(edge);
            edges[edge][0] = panelIndex(
                    fixture,
                    pair.leaderNodeIndex()
            );
            edges[edge][1] = panelIndex(
                    fixture,
                    pair.followerNodeIndex()
            );
        }
        return edges;
    }

    private static int panelIndex(Fixture fixture, int nodeIndex) {
        for (int panel = 0; panel < fixture.panelNodes().length; panel++) {
            if (fixture.panelNodes()[panel] == nodeIndex) {
                return panel;
            }
        }
        throw new AssertionError("Skirt tether referenced a non-panel node");
    }

    private static MotionResult simulate(Fixture fixture, int[][] edges) {
        Vector3f firstPivot = new Vector3f();
        Vector3f firstTip = new Vector3f();
        Vector3f secondPivot = new Vector3f();
        Vector3f secondTip = new Vector3f();
        Vector3f firstRest = new Vector3f();
        Vector3f secondRest = new Vector3f();
        Vector3f firstRestTip = new Vector3f();
        Vector3f secondRestTip = new Vector3f();
        Vector3f firstDirection = new Vector3f();
        Vector3f secondDirection = new Vector3f();
        float maximumStretch = 0.0F;
        float maximumDifference = 0.0F;
        int tetherFrames = 0;
        for (int frame = 0; frame < FRAMES; frame++) {
            fixture.solver().restoreAnimationPose();
            fixture.solver().solve(ZERO, WIND, 0.0F, DT, false);
            for (int[] edge : edges) {
                int firstNode = fixture.panelNodes()[edge[0]];
                int secondNode = fixture.panelNodes()[edge[1]];
                int firstSlot = fixture.panelSlots()[edge[0]];
                int secondSlot = fixture.panelSlots()[edge[1]];
                require(
                        fixture.solver().copyRuntimePivot(
                                firstNode,
                                firstPivot
                        )
                                && fixture.solver().copyRuntimeTip(
                                firstNode,
                                firstTip
                        )
                                && fixture.solver().copyRuntimePivot(
                                secondNode,
                                secondPivot
                        )
                                && fixture.solver().copyRuntimeTip(
                                secondNode,
                                secondTip
                        )
                                && fixture.solver().copyRestDirection(
                                firstSlot,
                                firstRest
                        )
                                && fixture.solver().copyRestDirection(
                                secondSlot,
                                secondRest
                        )
                                && fixture.solver().copyCurrentDirection(
                                firstSlot,
                                firstDirection
                        )
                                && fixture.solver().copyCurrentDirection(
                                secondSlot,
                                secondDirection
                        ),
                        "Skirt branch runtime pose was unavailable"
                );
                firstRestTip.set(firstPivot).fma(
                        firstPivot.distance(firstTip),
                        firstRest
                );
                secondRestTip.set(secondPivot).fma(
                        secondPivot.distance(secondTip),
                        secondRest
                );
                maximumStretch = Math.max(
                        maximumStretch,
                        firstTip.distance(secondTip)
                                - firstRestTip.distance(secondRestTip)
                );
                maximumDifference = Math.max(
                        maximumDifference,
                        firstDirection.distance(secondDirection)
                );
                if ((fixture.solver().lastProjectionSource(secondSlot) & 4)
                        != 0) {
                    tetherFrames++;
                }
            }
        }
        return new MotionResult(
                maximumStretch * 16.0F,
                maximumDifference,
                tetherFrames
        );
    }

    private static Fixture fixture(boolean sharedMount) {
        return fixture(sharedMount, true);
    }

    private static Fixture fixture(
            boolean sharedMount,
            boolean includeHair
    ) {
        String parentA = sharedMount ? "SkirtMount" : "MountA";
        String parentB = sharedMount ? "SkirtMount" : "MountB";
        String parentC = sharedMount ? "SkirtMount" : "MountC";
        BoneModelSnapshot model = coreModelFromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"identifier":"geometry.skirt_coupling",
                    "texture_width":32,"texture_height":32},
                  "bones":[
                    {"name":"Root","pivot":[0,0,0]},
                    {"name":"SkirtMount","parent":"Root","pivot":[0,12,0]},
                    {"name":"MountA","parent":"Root","pivot":[0,12,0]},
                    {"name":"MountB","parent":"Root","pivot":[0,12,0]},
                    {"name":"MountC","parent":"Root","pivot":[0,12,0]},
                    {"name":"PanelA","parent":"%s","pivot":[-2,12,0],
                     "cubes":[{"origin":[-3,0,-.5],"size":[2,12,1],
                       "uv":[0,0]}]},
                    {"name":"PanelB","parent":"%s","pivot":[0,12,0],
                     "cubes":[{"origin":[-1,0,-.5],"size":[2,12,1],
                       "uv":[0,0]}]},
                    {"name":"PanelC","parent":"%s","pivot":[2,12,0],
                     "cubes":[{"origin":[1,0,-.5],"size":[2,12,1],
                       "uv":[0,0]}]},
                    {"name":"HairA","parent":"SkirtMount","pivot":[-1,12,2],
                     "cubes":[{"origin":[-1.5,4,1.5],"size":[1,8,1],
                       "uv":[0,0]}]},
                    {"name":"HairB","parent":"SkirtMount","pivot":[1,12,2],
                     "cubes":[{"origin":[.5,4,1.5],"size":[1,8,1],
                       "uv":[0,0]}]}
                  ]}]}
                """.formatted(parentA, parentB, parentC));
        String hairChains = includeHair
                ? """
                  ,{"id":"hair_a","type":"HAIR","root":"HairA",
                    "profile":{"gravity_scale":0,"wind_scale":4},
                    "constraints":{"swing_limits":{
                      "left_degrees":45,"right_degrees":45,
                      "outward_degrees":45,"inward_degrees":45},
                      "collision":{"auto":false}}},
                   {"id":"hair_b","type":"HAIR","root":"HairB",
                    "profile":{"gravity_scale":0,"wind_scale":4},
                    "constraints":{"swing_limits":{
                      "left_degrees":45,"right_degrees":45,
                      "outward_degrees":45,"inward_degrees":45},
                      "collision":{"auto":false}}}
                  """
                : "";
        PhysicsBoneSelectionPlan plan = PhysicsBoneDiscoverer.discover(
                "verification:skirt_coupling",
                model,
                PhysicsMetadataJsonParser.parse(
                        JsonParser.parseString("""
                                {"schema_version":3,"mode":"explicit",
                                 "chains":[
                                  {"id":"panel_a","type":"SKIRT","root":"PanelA",
                                   "profile":{"gravity_scale":0,"wind_scale":4},
                                   "constraints":{"swing_limits":{
                                     "left_degrees":45,"right_degrees":45,
                                     "outward_degrees":45,"inward_degrees":45},
                                     "collision":{"auto":false}}},
                                  {"id":"panel_b","type":"SKIRT","root":"PanelB",
                                   "profile":{"gravity_scale":0,"wind_scale":4},
                                   "constraints":{"swing_limits":{
                                     "left_degrees":45,"right_degrees":45,
                                     "outward_degrees":45,"inward_degrees":45},
                                     "collision":{"auto":false}}},
                                  {"id":"panel_c","type":"SKIRT","root":"PanelC",
                                   "profile":{"gravity_scale":0,"wind_scale":4},
                                   "constraints":{"swing_limits":{
                                     "left_degrees":45,"right_degrees":45,
                                     "outward_degrees":45,"inward_degrees":45},
                                     "collision":{"auto":false}}}
                                  %s]}
                                """.formatted(hairChains)).getAsJsonObject(),
                        "skirt branch coupling verification"
                )
        );
        PhysicsSolverLayout layout = PhysicsSolverLayout.build(model, plan);
        int[] panelNodes = new int[]{
                nodeIndex(layout, model.bones().get("PanelA")),
                nodeIndex(layout, model.bones().get("PanelB")),
                nodeIndex(layout, model.bones().get("PanelC"))
        };
        int[] panelSlots = new int[]{
                layout.node(panelNodes[0]).drivenSlot(),
                layout.node(panelNodes[1]).drivenSlot(),
                layout.node(panelNodes[2]).drivenSlot()
        };
        return new Fixture(
                layout,
                new SpringBoneSolver(layout),
                panelNodes,
                panelSlots,
                includeHair
                        ? nodeIndex(layout, model.bones().get("HairB"))
                        : -1
        );
    }

    private static int nodeIndex(
            PhysicsSolverLayout layout,
            BoneModelSnapshot.Bone bone
    ) {
        for (int index = 0; index < layout.activeNodeCount(); index++) {
            if (layout.node(index).bone() == bone) {
                return index;
            }
        }
        throw new AssertionError("Fixture bone left the active layout");
    }

    private record Fixture(
            PhysicsSolverLayout layout,
            SpringBoneSolver solver,
            int[] panelNodes,
            int[] panelSlots,
            int hairNode
    ) {
    }

    private record MotionResult(
            float maximumStretchPixels,
            float maximumDirectionDifference,
            int tetherFrames
    ) {
    }
}
