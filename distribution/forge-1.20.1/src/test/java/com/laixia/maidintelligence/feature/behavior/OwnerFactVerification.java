package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.domain.owner.OwnerFactIds;
import com.laixia.maidintelligence.feature.behavior.domain.owner.OwnerFacts;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.fact.FactType;

import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Owner facts as a catalogue: declared, readable, and typed consistently.
 *
 * <p>The checks that matter here are the ones a person cannot hold in their
 * head while adding the twenty-sixth fact — that every id declared can still be
 * read back, that no two ids answer with the same field, and that nothing was
 * declared without being wired.
 */
public final class OwnerFactVerification {
    private OwnerFactVerification() {
    }

    public static void main(String[] args) {
        absentMeansUnknownNotZero();
        everyDeclaredFactIsReadable();
        noTwoFactsShareAField();
        everyRecordComponentIsDeclared();
        everyFactIsInTheVocabulary();
        unknownIdsAreNotClaimed();
    }

    /**
     * A maid with no owner has taken no reading. Answering zero would let
     * "his health is low" quietly hold true for an owner who is not there.
     */
    private static void absentMeansUnknownNotZero() {
        OwnerFacts absent = OwnerFacts.absent();
        for (OrchestrationId fact : OwnerFactIds.facts().keySet()) {
            require(Double.isNaN(absent.value(fact)),
                    "Absent owner answered " + absent.value(fact)
                            + " for " + fact.path());
        }
    }

    /**
     * Catches the failure mode of a hand-written dispatch: an id added to the
     * catalogue but never given a branch, which reads as a permanently unknown
     * fact rather than as an error.
     */
    private static void everyDeclaredFactIsReadable() {
        OwnerFacts facts = distinctValues();
        for (OrchestrationId fact : OwnerFactIds.facts().keySet()) {
            require(!Double.isNaN(facts.value(fact)),
                    "Declared fact " + fact.path() + " has no dispatch branch");
        }
    }

    /** Catches the other half: a branch copied and left pointing at a twin. */
    private static void noTwoFactsShareAField() {
        OwnerFacts facts = distinctValues();
        Set<Double> seen = new HashSet<>();
        for (OrchestrationId fact : OwnerFactIds.facts().keySet()) {
            double value = facts.value(fact);
            require(seen.add(value),
                    "Two owner facts read the same field, at " + fact.path());
        }
    }

    /** And the third: a field added to the record but never given an id. */
    private static void everyRecordComponentIsDeclared() {
        int declared = OwnerFactIds.facts().size();
        int components = OwnerFacts.class.getRecordComponents().length;
        require(declared == components,
                "OwnerFacts has " + components + " fields but "
                        + declared + " are declared as facts");
    }

    /**
     * A fact a datapack cannot name is a fact that does not exist. The
     * vocabulary keeps the declared set and the type table apart, so both have
     * to be checked — a fact present in only one is unusable either way.
     */
    private static void everyFactIsInTheVocabulary() {
        Set<OrchestrationId> declared =
                CompanionIntentIds.vocabulary().facts();
        Map<OrchestrationId, FactType> types =
                CompanionIntentIds.vocabulary().factTypes();
        for (Map.Entry<OrchestrationId, FactType> entry
                : OwnerFactIds.facts().entrySet()) {
            OrchestrationId fact = entry.getKey();
            require(declared.contains(fact),
                    "Owner fact " + fact.path()
                            + " is typed but not declared usable");
            FactType type = types.get(fact);
            require(type != null,
                    "Owner fact " + fact.path() + " has no declared type");
            require(type == entry.getValue(),
                    "Owner fact " + fact.path() + " is typed " + type
                            + " in the vocabulary but " + entry.getValue()
                            + " in its catalogue");
        }
    }

    private static void unknownIdsAreNotClaimed() {
        OwnerFacts facts = distinctValues();
        require(Double.isNaN(facts.value(CompanionIntentIds.HUNGER)),
                "An owner reading claimed a fact belonging to the maid");
        require(Double.isNaN(facts.value(null)),
                "A null fact id was answered");
    }

    /**
     * One reading with a different value in every field, so a field read by
     * two ids — or by none — shows up as a repeat or a gap.
     */
    private static OwnerFacts distinctValues() {
        RecordComponent[] components = OwnerFacts.class.getRecordComponents();
        Object[] values = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            values[index] = index + 1.0D;
        }
        try {
            Class<?>[] types = new Class<?>[components.length];
            for (int index = 0; index < components.length; index++) {
                types[index] = components[index].getType();
            }
            return OwnerFacts.class
                    .getDeclaredConstructor(types)
                    .newInstance(values);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(
                    "OwnerFacts could not be built reflectively",
                    failure
            );
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
