package com.laixia.maidintelligence.feature.behavior.application.ability;

import com.laixia.maidintelligence.feature.behavior.api.MaidAbilityApi;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationRequest;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityActivationSource;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityCandidate;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityDefinition;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityGrant;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityGrantSet;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityRuntimeState;
import com.laixia.maidintelligence.feature.behavior.port.AbilityCatalogPort;
import com.laixia.maidintelligence.feature.behavior.port.AbilityGrantPort;
import com.laixia.maidintelligence.feature.behavior.port.AbilitySignalPort;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class DefaultMaidAbilityService<M>
        implements MaidAbilityApi<M> {
    private static final int MAX_ACTIVE_REQUESTS = 16;

    private final AbilityCatalogPort catalogs;
    private final AbilityGrantPort<M> grantPort;
    private final AbilitySignalPort<M> signals;
    private final Supplier<UUID> requestIds;
    private final Map<M, Runtime> runtime = new WeakHashMap<>();

    public DefaultMaidAbilityService(
            AbilityCatalogPort catalogs,
            AbilityGrantPort<M> grantPort,
            AbilitySignalPort<M> signals
    ) {
        this(catalogs, grantPort, signals, UUID::randomUUID);
    }

    public DefaultMaidAbilityService(
            AbilityCatalogPort catalogs,
            AbilityGrantPort<M> grantPort,
            AbilitySignalPort<M> signals,
            Supplier<UUID> requestIds
    ) {
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.grantPort = Objects.requireNonNull(grantPort, "grantPort");
        this.signals = Objects.requireNonNull(signals, "signals");
        this.requestIds = Objects.requireNonNull(requestIds, "requestIds");
    }

    @Override
    public long catalogGeneration() {
        return catalogs.current().generation();
    }

    @Override
    public List<AbilityDefinition> definitions() {
        return catalogs.current().definitions();
    }

    @Override
    public AbilityGrantSet grants(M subject) {
        return grantPort.load(Objects.requireNonNull(subject, "subject"));
    }

    @Override
    public boolean grant(
            M subject,
            OrchestrationId ability,
            long gameTime,
            String source
    ) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(ability, "ability");
        if (catalogs.current().definition(ability) == null) {
            return false;
        }
        AbilityGrantSet current = grantPort.load(subject);
        grantPort.save(
                subject,
                current.grant(new AbilityGrant(
                        ability,
                        gameTime,
                        source
                ))
        );
        return true;
    }

    @Override
    public boolean revoke(M subject, OrchestrationId ability) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(ability, "ability");
        AbilityGrantSet current = grantPort.load(subject);
        AbilityGrantSet updated = current.revoke(ability);
        if (updated == current) {
            return false;
        }
        grantPort.save(subject, updated);
        Runtime state = runtime.get(subject);
        if (state != null) {
            state.removeAbility(ability);
        }
        return true;
    }

    @Override
    public boolean granted(M subject, OrchestrationId ability) {
        return grantPort.load(subject).contains(ability)
                && catalogs.current().definition(ability) != null;
    }

    @Override
    public Optional<AbilityActivationRequest> request(
            M subject,
            OrchestrationId ability,
            AbilityActivationSource source,
            long gameTime
    ) {
        return requestInternal(
                subject,
                ability,
                source,
                gameTime,
                null
        );
    }

    @Override
    public Optional<AbilityActivationRequest> request(
            M subject,
            OrchestrationId ability,
            AbilityActivationSource source,
            long gameTime,
            UUID sharedRequestId
    ) {
        return requestInternal(
                subject,
                ability,
                source,
                gameTime,
                Objects.requireNonNull(
                        sharedRequestId,
                        "sharedRequestId"
                )
        );
    }

    private Optional<AbilityActivationRequest> requestInternal(
            M subject,
            OrchestrationId ability,
            AbilityActivationSource source,
            long gameTime,
            UUID sharedRequestId
    ) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(ability, "ability");
        Objects.requireNonNull(source, "source");
        AbilityDefinition definition =
                catalogs.current().definition(ability);
        if (definition == null
                || !grantPort.load(subject).contains(ability)) {
            return Optional.empty();
        }
        Runtime state = runtime.computeIfAbsent(
                subject,
                ignored -> new Runtime()
        );
        state.prune(gameTime, catalogs);
        if (state.executing.contains(ability)
                || state.cooldownUntil.getOrDefault(ability, Long.MIN_VALUE)
                > gameTime) {
            return Optional.empty();
        }
        AbilityActivationRequest existing =
                state.request(ability, source, gameTime);
        if (existing != null) {
            if (sharedRequestId == null
                    || sharedRequestId.equals(existing.requestId())) {
                return emit(subject, existing, definition, gameTime);
            }
            state.requests.remove(existing.requestId());
        }
        if (state.requests.size() >= MAX_ACTIVE_REQUESTS) {
            return Optional.empty();
        }
        AbilityActivationRequest created = new AbilityActivationRequest(
                sharedRequestId == null
                        ? requestIds.get()
                        : sharedRequestId,
                ability,
                source,
                gameTime,
                addSaturated(gameTime, definition.requestTtlTicks())
        );
        state.requests.put(created.requestId(), created);
        Optional<AbilityActivationRequest> emitted =
                emit(subject, created, definition, gameTime);
        if (emitted.isEmpty()) {
            state.requests.remove(created.requestId());
        }
        return emitted;
    }

    @Override
    public Optional<AbilityActivationRequest> activeRequest(
            M subject,
            OrchestrationId ability,
            long gameTime
    ) {
        Runtime state = runtime.get(subject);
        if (state == null) {
            return Optional.empty();
        }
        state.prune(gameTime, catalogs);
        return state.requests.values().stream()
                .filter(request -> request.ability().equals(ability))
                .filter(request -> request.active(gameTime))
                .sorted(requestOrder())
                .findFirst();
    }

    @Override
    public List<AbilityCandidate> candidates(M subject, long gameTime) {
        Runtime state = runtime.get(subject);
        if (state == null) {
            return List.of();
        }
        state.prune(gameTime, catalogs);
        AbilityGrantSet grants = grantPort.load(subject);
        List<AbilityCandidate> candidates = new ArrayList<>();
        for (AbilityActivationRequest request : state.requests.values()) {
            AbilityDefinition definition =
                    catalogs.current().definition(request.ability());
            AbilityGrant grant = grants.grants().get(request.ability());
            if (definition != null
                    && grant != null
                    && request.active(gameTime)
                    && !state.executing.contains(request.ability())
                    && state.cooldownUntil.getOrDefault(
                    request.ability(),
                    Long.MIN_VALUE
            ) <= gameTime) {
                candidates.add(new AbilityCandidate(
                        definition,
                        grant,
                        request,
                        score(definition, request.source())
                ));
            }
        }
        candidates.sort(Comparator
                .comparingDouble(AbilityCandidate::score)
                .reversed()
                .thenComparing(candidate ->
                        candidate.definition().id()));
        return List.copyOf(candidates);
    }

    @Override
    public boolean beginExecution(
            M subject,
            UUID requestId,
            long gameTime
    ) {
        Runtime state = runtime.get(subject);
        if (state == null) {
            return false;
        }
        state.prune(gameTime, catalogs);
        AbilityActivationRequest request = state.requests.get(requestId);
        if (request == null
                || state.executing.contains(request.ability())
                || !grantPort.load(subject).contains(request.ability())) {
            return false;
        }
        state.executing.add(request.ability());
        return true;
    }

    @Override
    public void complete(
            M subject,
            UUID requestId,
            boolean succeeded,
            long gameTime
    ) {
        Runtime state = runtime.get(subject);
        if (state == null) {
            return;
        }
        AbilityActivationRequest request = state.requests.remove(requestId);
        if (request == null) {
            return;
        }
        state.executing.remove(request.ability());
        if (succeeded) {
            AbilityDefinition definition =
                    catalogs.current().definition(request.ability());
            if (definition != null && definition.cooldownTicks() > 0) {
                state.cooldownUntil.put(
                        request.ability(),
                        addSaturated(gameTime, definition.cooldownTicks())
                );
            }
            state.requests.values().removeIf(candidate ->
                    candidate.ability().equals(request.ability()));
        }
    }

    @Override
    public AbilityRuntimeState inspect(M subject, long gameTime) {
        Runtime state = runtime.get(subject);
        if (state == null) {
            return AbilityRuntimeState.idle();
        }
        state.prune(gameTime, catalogs);
        return new AbilityRuntimeState(
                state.requests.values().stream()
                        .sorted(requestOrder())
                        .toList(),
                state.cooldownUntil,
                state.executing
        );
    }

    @Override
    public void forget(M subject) {
        runtime.remove(subject);
    }

    private Optional<AbilityActivationRequest> emit(
            M subject,
            AbilityActivationRequest request,
            AbilityDefinition definition,
            long gameTime
    ) {
        return signals.signal(
                subject,
                AbilityTemplateCompiler.signal(
                        definition.id(),
                        request.source()
                ),
                gameTime,
                (int) Math.max(
                        1L,
                        request.expiresAtTick() - gameTime + 1L
                )
        ) ? Optional.of(request) : Optional.empty();
    }

    private static double score(
            AbilityDefinition definition,
            AbilityActivationSource source
    ) {
        return source == AbilityActivationSource.COMMAND
                ? definition.commandScore()
                : definition.autonomousScore();
    }

    private static Comparator<AbilityActivationRequest> requestOrder() {
        return Comparator
                .comparingInt((AbilityActivationRequest request) ->
                        request.source() == AbilityActivationSource.COMMAND
                                ? 0
                                : 1)
                .thenComparingLong(
                        AbilityActivationRequest::createdAtTick
                )
                .thenComparing(AbilityActivationRequest::requestId);
    }

    private static long addSaturated(long value, int amount) {
        return value > Long.MAX_VALUE - amount
                ? Long.MAX_VALUE
                : value + amount;
    }

    private static final class Runtime {
        private final Map<UUID, AbilityActivationRequest> requests =
                new HashMap<>();
        private final Map<OrchestrationId, Long> cooldownUntil =
                new HashMap<>();
        private final Set<OrchestrationId> executing = new HashSet<>();

        private AbilityActivationRequest request(
                OrchestrationId ability,
                AbilityActivationSource source,
                long gameTime
        ) {
            return requests.values().stream()
                    .filter(request -> request.ability().equals(ability))
                    .filter(request -> request.source() == source)
                    .filter(request -> request.active(gameTime))
                    .findFirst()
                    .orElse(null);
        }

        private void prune(
                long gameTime,
                AbilityCatalogPort catalogs
        ) {
            requests.values().removeIf(request ->
                    !request.active(gameTime)
                            || catalogs.current().definition(
                            request.ability()
                    ) == null);
            cooldownUntil.entrySet().removeIf(entry ->
                    entry.getValue() <= gameTime
                            || catalogs.current().definition(
                            entry.getKey()
                    ) == null);
            executing.removeIf(ability ->
                    catalogs.current().definition(ability) == null);
        }

        private void removeAbility(OrchestrationId ability) {
            requests.values().removeIf(request ->
                    request.ability().equals(ability));
            cooldownUntil.remove(ability);
            executing.remove(ability);
        }
    }
}
