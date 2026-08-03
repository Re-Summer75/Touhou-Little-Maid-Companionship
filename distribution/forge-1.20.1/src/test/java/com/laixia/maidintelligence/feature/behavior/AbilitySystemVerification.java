package com.laixia.maidintelligence.feature.behavior;

import com.laixia.maidintelligence.feature.behavior.application.ability.AbilityTemplateCompiler;
import com.laixia.maidintelligence.feature.behavior.application.ability.DefaultMaidAbilityService;
import com.laixia.maidintelligence.feature.behavior.application.ability.MutableAbilityCatalog;
import com.laixia.maidintelligence.feature.behavior.domain.CompanionIntentIds;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationRequest;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationSource;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityCatalog;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityDefinition;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityGrantSet;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityTemplate;
import com.laixia.maidintelligence.feature.behavior.domain.ability.CompanionAbilityIds;
import com.laixia.maidintelligence.feature.behavior.domain.ability.CompiledAbilityTemplate;
import com.laixia.maidintelligence.feature.behavior.port.AbilityGrantPort;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentCatalog;
import com.laixia.maidintelligence.feature.orchestration.domain.IntentVocabulary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class AbilitySystemVerification {
    private AbilitySystemVerification() {
    }

    public static void main(String[] args) {
        verify();
    }

    private static void verify() {
        templateCreatesTwoIntentsOverOnePlan();
        grantsRequestsExecutionAndCooldownRemainSeparated();
        sharedCoordinationIdentityIsPreserved();
    }

    private static void templateCreatesTwoIntentsOverOnePlan() {
        AbilityTemplateCompiler compiler = new AbilityTemplateCompiler();
        CompiledAbilityTemplate compiled = compiler.compile(definition());
        require(compiled.intents().size() == 2,
                "Ability did not compile command and autonomous intents");
        require(compiled.intents().stream().allMatch(intent ->
                        intent.plan().equals(compiled.plan().id())),
                "Ability intents did not share one plan");
        IntentVocabulary vocabulary = compiler.extendVocabulary(
                CompanionIntentIds.vocabulary(),
                List.of(compiled)
        );
        IntentCatalog catalog = IntentCatalog.compile(
                1L,
                compiled.intents(),
                List.of(compiled.plan()),
                vocabulary
        );
        require(catalog.intents().size() == 2
                        && catalog.planCount() == 1,
                "Generated ability artifacts did not compile");
    }

    private static void grantsRequestsExecutionAndCooldownRemainSeparated() {
        Object maid = new Object();
        MutableAbilityCatalog catalog = new MutableAbilityCatalog();
        catalog.publish(AbilityCatalog.compile(
                1L,
                List.of(definition())
        ));
        MemoryGrantPort grants = new MemoryGrantPort();
        List<String> signals = new ArrayList<>();
        AtomicLong ids = new AtomicLong();
        DefaultMaidAbilityService<Object> abilities =
                new DefaultMaidAbilityService<>(
                        catalog,
                        grants,
                        (subject, signal, gameTime, ttlTicks) -> {
                            signals.add(signal.toString());
                            return true;
                        },
                        () -> new UUID(0L, ids.incrementAndGet())
                );

        require(abilities.request(
                        maid,
                        CompanionAbilityIds.DEPLOY_BOAT,
                        AbilityActivationSource.COMMAND,
                        100L
                ).isEmpty(),
                "Ungraded ability produced an activation request");
        require(abilities.grant(
                        maid,
                        CompanionAbilityIds.DEPLOY_BOAT,
                        100L,
                        "verification"
                ),
                "Known ability could not be granted");
        require(signals.isEmpty(),
                "Granting alone activated the ability");

        AbilityActivationRequest request = abilities.request(
                maid,
                CompanionAbilityIds.DEPLOY_BOAT,
                AbilityActivationSource.COMMAND,
                100L
        ).orElseThrow();
        require(signals.size() == 1,
                "Activation request did not emit its intent signal");
        require(abilities.beginExecution(
                        maid,
                        request.requestId(),
                        100L
                ),
                "Valid request did not enter execution");
        abilities.complete(maid, request.requestId(), true, 101L);
        require(abilities.request(
                        maid,
                        CompanionAbilityIds.DEPLOY_BOAT,
                        AbilityActivationSource.COMMAND,
                        110L
                ).isEmpty(),
                "Ability ignored its runtime cooldown");
        require(abilities.request(
                        maid,
                        CompanionAbilityIds.DEPLOY_BOAT,
                        AbilityActivationSource.COMMAND,
                        122L
                ).isPresent(),
                "Ability remained unavailable after cooldown");
        require(abilities.revoke(maid, CompanionAbilityIds.DEPLOY_BOAT)
                        && !abilities.granted(
                        maid,
                        CompanionAbilityIds.DEPLOY_BOAT
                ),
                "Ability revocation did not clear authorization");
    }

    private static void sharedCoordinationIdentityIsPreserved() {
        Object maid = new Object();
        MutableAbilityCatalog catalog = new MutableAbilityCatalog();
        catalog.publish(AbilityCatalog.compile(
                1L,
                List.of(definition())
        ));
        DefaultMaidAbilityService<Object> abilities =
                new DefaultMaidAbilityService<>(
                        catalog,
                        new MemoryGrantPort(),
                        (subject, signal, gameTime, ttlTicks) -> true
                );
        abilities.grant(
                maid,
                CompanionAbilityIds.DEPLOY_BOAT,
                0L,
                "coordination_verification"
        );
        UUID shared = new UUID(40L, 50L);
        AbilityActivationRequest request = abilities.request(
                maid,
                CompanionAbilityIds.DEPLOY_BOAT,
                AbilityActivationSource.AUTONOMOUS,
                1L,
                shared
        ).orElseThrow();
        require(request.requestId().equals(shared),
                "Shared request identity was replaced per maid");
    }

    private static AbilityDefinition definition() {
        return new AbilityDefinition(
                CompanionAbilityIds.DEPLOY_BOAT,
                AbilityTemplate.WORLD_ITEM_DEPLOY,
                CompanionIntentIds.DEPLOY_BOAT,
                Map.of(),
                40,
                10,
                20,
                900.0D,
                120.0D,
                500
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class MemoryGrantPort
            implements AbilityGrantPort<Object> {
        private final Map<Object, AbilityGrantSet> values = new HashMap<>();

        @Override
        public AbilityGrantSet load(Object subject) {
            return values.getOrDefault(subject, AbilityGrantSet.empty());
        }

        @Override
        public void save(Object subject, AbilityGrantSet grants) {
            values.put(subject, grants);
        }
    }
}
