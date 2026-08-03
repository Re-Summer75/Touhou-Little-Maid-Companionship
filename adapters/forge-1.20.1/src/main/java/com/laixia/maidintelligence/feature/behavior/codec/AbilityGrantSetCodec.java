package com.laixia.maidintelligence.feature.behavior.codec;

import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityGrant;
import com.laixia.maidintelligence.feature.behavior.domain.ability.AbilityGrantSet;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AbilityGrantSetCodec {
    private static final Codec<OrchestrationId> ID =
            Codec.STRING.comapFlatMap(
                    AbilityGrantSetCodec::parseId,
                    OrchestrationId::toString
            );
    private static final Codec<AbilityGrant> GRANT =
            RecordCodecBuilder.create(instance -> instance.group(
                    ID.fieldOf("ability").forGetter(AbilityGrant::ability),
                    Codec.LONG.fieldOf("granted_at_tick")
                            .forGetter(AbilityGrant::grantedAtTick),
                    Codec.STRING.fieldOf("source")
                            .forGetter(AbilityGrant::source)
            ).apply(instance, AbilityGrant::new));

    private static final Codec<Persisted> PERSISTED =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.optionalFieldOf("revision", 0L)
                            .forGetter(Persisted::revision),
                    GRANT.listOf().optionalFieldOf("grants", List.of())
                            .forGetter(Persisted::grants)
            ).apply(instance, Persisted::new));

    public static final Codec<AbilityGrantSet> CODEC =
            PERSISTED.comapFlatMap(
                    AbilityGrantSetCodec::decode,
                    set -> new Persisted(
                            set.revision(),
                            List.copyOf(set.grants().values())
                    )
            );

    private AbilityGrantSetCodec() {
    }

    private static DataResult<AbilityGrantSet> decode(Persisted persisted) {
        try {
            Map<OrchestrationId, AbilityGrant> grants =
                    new LinkedHashMap<>();
            for (AbilityGrant grant : persisted.grants()) {
                if (grants.putIfAbsent(
                        grant.ability(),
                        grant
                ) != null) {
                    return DataResult.error(() ->
                            "Duplicate ability grant: " + grant.ability());
                }
            }
            return DataResult.success(new AbilityGrantSet(
                    persisted.revision(),
                    grants
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<OrchestrationId> parseId(String value) {
        try {
            return DataResult.success(OrchestrationId.parse(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private record Persisted(
            long revision,
            List<AbilityGrant> grants
    ) {
    }
}
