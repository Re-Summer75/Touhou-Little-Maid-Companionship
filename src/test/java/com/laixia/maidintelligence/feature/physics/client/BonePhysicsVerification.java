package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier.ChainType;
import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier.Classification;

/**
 * Dependency-free checks for the bone-physics classifier: it must pick out the
 * real swinging chains (tail, hair, ear), skip pivots and rigid bones, and fold
 * casing, camelCase, and separators to the same result. The spring integration
 * itself lives in {@code MaidBonePhysics} against live Gecko bones and is
 * exercised in-game rather than here.
 */
public final class BonePhysicsVerification {
    private BonePhysicsVerification() {
    }

    public static void main(String[] args) {
        verifiesTailChainClassification();
        verifiesPivotAndRigidBonesAreSkipped();
        verifiesHairAndEarClassification();
        verifiesClassifierNormalisesNames();
        System.out.println("Bone physics verification passed.");
    }

    private static void verifiesTailChainClassification() {
        requireChain("Tail", ChainType.TAIL, 0);
        requireChain("Tail2", ChainType.TAIL, 1);
        requireChain("Tail4", ChainType.TAIL, 3);
        requireChain("Tail7", ChainType.TAIL, 6);
    }

    private static void verifiesPivotAndRigidBonesAreSkipped() {
        requireNone("MTail");
        requireNone("MHead");
        requireNone("MRightSideHair");
        requireNone("Head");
        requireNone("UpBody");
        requireNone("UpperBody");
        requireNone("bow");
    }

    private static void verifiesHairAndEarClassification() {
        require(
                PhysicsBoneClassifier.classify("RightSideHair").type() == ChainType.HAIR,
                "Side hair was not treated as a hair chain"
        );
        require(
                PhysicsBoneClassifier.classify("Bangs").type() == ChainType.HAIR,
                "Bangs were not treated as a hair chain"
        );
        require(
                PhysicsBoneClassifier.classify("Left_ear").type() == ChainType.EAR,
                "Ear bone was not treated as an ear chain"
        );
        require(
                PhysicsBoneClassifier.classify("Right_ear").type() == ChainType.EAR,
                "Ear bone was not treated as an ear chain"
        );
    }

    private static void verifiesClassifierNormalisesNames() {
        // camelCase, separators, and casing must all fold to the same chain.
        require(
                PhysicsBoneClassifier.classify("sideHair").type() == ChainType.HAIR,
                "camelCase hair token was not recognised"
        );
        require(
                PhysicsBoneClassifier.classify("ponytail").type() == ChainType.HAIR,
                "Ponytail was not recognised as hair"
        );
        require(
                PhysicsBoneClassifier.classify("Left_Braid").type() == ChainType.HAIR,
                "Braid was not recognised as hair"
        );
        requireChain("Tail10", ChainType.TAIL, 9);
        require(
                PhysicsBoneClassifier.classify("tail").type() == ChainType.TAIL,
                "Lowercase tail was not recognised"
        );
        // A word that merely contains a chain token as a substring must not match.
        require(
                PhysicsBoneClassifier.classify("detail").type() == ChainType.NONE,
                "Substring 'tail' inside another word was misclassified"
        );
        require(
                PhysicsBoneClassifier.classify("Chair").type() == ChainType.NONE,
                "Substring 'hair' inside another word was misclassified"
        );
    }

    private static void requireChain(String name, ChainType type, int depth) {
        Classification classification = PhysicsBoneClassifier.classify(name);
        require(
                classification.type() == type && classification.depth() == depth,
                "Bone " + name + " classified as " + classification.type()
                        + " depth " + classification.depth()
        );
    }

    private static void requireNone(String name) {
        require(
                PhysicsBoneClassifier.classify(name).type() == ChainType.NONE,
                "Bone " + name + " should not receive physics"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
