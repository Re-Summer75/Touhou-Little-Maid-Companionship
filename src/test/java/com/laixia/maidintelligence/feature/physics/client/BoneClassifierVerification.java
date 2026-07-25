package com.laixia.maidintelligence.feature.physics.client;

import com.laixia.maidintelligence.feature.physics.client.PhysicsBoneClassifier.ChainType;

import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.require;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireChain;
import static com.laixia.maidintelligence.feature.physics.client.BonePhysicsVerificationSupport.requireNone;

final class BoneClassifierVerification {
    private BoneClassifierVerification() {
    }

    static void run() {
        verifiesTailChainClassification();
        verifiesPivotAndRigidBonesAreSkipped();
        verifiesHairAndEarClassification();
        verifiesClassifierNormalisesNames();
        verifiesNumberedSegmentsAndPinyin();
        verifiesExtendedSoftPartHints();
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
        require(
                PhysicsBoneClassifier.classifyVisibleGeometry("MFrontHair")
                        .type() == ChainType.HAIR,
                "Visible M-prefixed hair geometry lost its semantic hint"
        );
    }

    private static void verifiesHairAndEarClassification() {
        require(
                PhysicsBoneClassifier.classify("RightSideHair").type()
                        == ChainType.HAIR,
                "Side hair was not treated as a hair chain"
        );
        require(
                PhysicsBoneClassifier.classify("Bangs").type() == ChainType.HAIR,
                "Bangs were not treated as a hair chain"
        );
        require(
                PhysicsBoneClassifier.classify("Left_ear").type()
                        == ChainType.EAR,
                "Ear bone was not treated as an ear chain"
        );
        require(
                PhysicsBoneClassifier.classify("Right_ear").type()
                        == ChainType.EAR,
                "Ear bone was not treated as an ear chain"
        );
        requireChain("TwinTail2", ChainType.HAIR, 1);
        requireChain("PonyTail3", ChainType.HAIR, 2);
        requireChain("后发2", ChainType.HAIR, 1);
        require(
                PhysicsBoneClassifier.isFringeHint("MBangs")
                        && PhysicsBoneClassifier.isFringeHint("FFrontHair")
                        && PhysicsBoneClassifier.isFringeHint("HairFront")
                        && PhysicsBoneClassifier.isFringeHint("刘海"),
                "Fringe aliases were not recognised"
        );
        require(
                PhysicsBoneClassifier.isBreaker("左眼"),
                "CJK facial bone was not treated as a chain breaker"
        );
        require(
                PhysicsBoneClassifier.isFacialFeature("Left_meimao")
                        && PhysicsBoneClassifier.isFacialFeature(
                        "Mouth_smile2"
                ),
                "Pinyin eyebrow or mouth alias was not a hard exclusion"
        );
        require(
                PhysicsBoneClassifier.isFacialFeature("saihong_front")
                        && PhysicsBoneClassifier.isFacialFeature("wei_xiao"),
                "Blush or split smile alias was not a hard exclusion"
        );
        require(
                PhysicsBoneClassifier.isFacialFeature("Hair_Mouth"),
                "Mouth semantics did not override a mixed hair name"
        );
        require(
                !PhysicsBoneClassifier.isFacialFeature("Face_Bangs")
                        && PhysicsBoneClassifier.isBreaker("Face_Bangs"),
                "Generic face container incorrectly overrode explicit bangs"
        );
    }

    private static void verifiesClassifierNormalisesNames() {
        require(
                PhysicsBoneClassifier.classify("sideHair").type()
                        == ChainType.HAIR,
                "camelCase hair token was not recognised"
        );
        require(
                PhysicsBoneClassifier.classify("ponytail").type()
                        == ChainType.HAIR,
                "Ponytail was not recognised as hair"
        );
        require(
                PhysicsBoneClassifier.classify("Left_Braid").type()
                        == ChainType.HAIR,
                "Braid was not recognised as hair"
        );
        requireChain("Tail10", ChainType.TAIL, 9);
        require(
                PhysicsBoneClassifier.classify("tail").type() == ChainType.TAIL,
                "Lowercase tail was not recognised"
        );
        require(
                PhysicsBoneClassifier.classify("detail").type() == ChainType.NONE,
                "Substring 'tail' inside another word was misclassified"
        );
        require(
                PhysicsBoneClassifier.classify("Chair").type() == ChainType.NONE,
                "Substring 'hair' inside another word was misclassified"
        );
    }

    private static void verifiesNumberedSegmentsAndPinyin() {
        requireChain("LongHair2", ChainType.HAIR, 1);
        requireChain("LongRightHair2", ChainType.HAIR, 1);
        requireChain("RightSideHair2", ChainType.HAIR, 1);
        requireChain("Ear2", ChainType.EAR, 1);
        requireChain("Body_Tail3", ChainType.TAIL, 2);
        require(
                PhysicsBoneClassifier.classify("Tail999999999999999999999")
                        .type() == ChainType.TAIL,
                "Oversized numeric suffix crashed or lost its semantic hint"
        );
        require(
                PhysicsBoneClassifier.classify("shuangmawei").type()
                        == ChainType.HAIR,
                "Pinyin twin-tail (shuangmawei) was not recognised as hair"
        );
        require(
                PhysicsBoneClassifier.classify("weiqu").type() == ChainType.TAIL,
                "Pinyin tail-skirt (weiqu) was not recognised as tail"
        );
        require(
                PhysicsBoneClassifier.classify("toufa").type() == ChainType.HAIR,
                "Pinyin hair (toufa) was not recognised"
        );
        require(
                PhysicsBoneClassifier.classify("weixiao").type() == ChainType.NONE,
                "Smile bone (weixiao) was misclassified as a tail"
        );
    }

    private static void verifiesExtendedSoftPartHints() {
        requireChain("Skirt2", ChainType.SKIRT, 1);
        requireChain("bow", ChainType.RIBBON, 0);
        requireChain("BackCape3", ChainType.CAPE, 2);
        requireChain("LeftWing4", ChainType.WING, 3);
        requireChain("weiqun", ChainType.SKIRT, 0);
        requireChain("qunzi", ChainType.SKIRT, 0);
        requireChain("前裙2", ChainType.SKIRT, 1);
        requireChain("hudiejie", ChainType.RIBBON, 0);
        requireChain("guashi", ChainType.RIBBON, 0);
        requireChain("挂饰3", ChainType.RIBBON, 2);
        requireChain("pendant", ChainType.RIBBON, 0);
        requireChain("chibang2", ChainType.WING, 1);
        requireNone("MRibbon");
        requireNone("SHmask");
        requireNone("faceplate");
    }
}
