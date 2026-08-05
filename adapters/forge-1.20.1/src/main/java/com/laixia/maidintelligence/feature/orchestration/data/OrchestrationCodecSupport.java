package com.laixia.maidintelligence.feature.orchestration.data;

import com.laixia.maidintelligence.feature.orchestration.domain.FactComparison;
import com.laixia.maidintelligence.feature.orchestration.domain.OrchestrationId;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityAggregation;
import com.laixia.maidintelligence.feature.orchestration.domain.utility.UtilityCurve;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Locale;

final class OrchestrationCodecSupport {
    static final Codec<OrchestrationId> ID = Codec.STRING.comapFlatMap(
            value -> parseId(value),
            OrchestrationId::toString
    );
    static final Codec<FactComparison> COMPARISON =
            Codec.STRING.comapFlatMap(
                    OrchestrationCodecSupport::parseComparison,
                    OrchestrationCodecSupport::comparisonName
            );
    static final Codec<UtilityCurve> CURVE = Codec.STRING.comapFlatMap(
            OrchestrationCodecSupport::parseCurve,
            curve -> curve.name().toLowerCase(Locale.ROOT)
    );
    static final Codec<UtilityAggregation> AGGREGATION =
            Codec.STRING.comapFlatMap(
                    OrchestrationCodecSupport::parseAggregation,
                    aggregation -> aggregation.name().toLowerCase(Locale.ROOT)
            );

    private OrchestrationCodecSupport() {
    }

    private static DataResult<OrchestrationId> parseId(String value) {
        try {
            return DataResult.success(OrchestrationId.parse(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<FactComparison> parseComparison(String value) {
        return switch (value) {
            case "lt" -> DataResult.success(FactComparison.LESS_THAN);
            case "lte" -> DataResult.success(FactComparison.LESS_OR_EQUAL);
            case "eq" -> DataResult.success(FactComparison.EQUAL);
            case "neq" -> DataResult.success(FactComparison.NOT_EQUAL);
            case "gte" -> DataResult.success(FactComparison.GREATER_OR_EQUAL);
            case "gt" -> DataResult.success(FactComparison.GREATER_THAN);
            default -> DataResult.error(
                    () -> "Unknown fact comparison: " + value
            );
        };
    }

    private static String comparisonName(FactComparison comparison) {
        return switch (comparison) {
            case LESS_THAN -> "lt";
            case LESS_OR_EQUAL -> "lte";
            case EQUAL -> "eq";
            case NOT_EQUAL -> "neq";
            case GREATER_OR_EQUAL -> "gte";
            case GREATER_THAN -> "gt";
        };
    }

    private static DataResult<UtilityCurve> parseCurve(String value) {
        try {
            return DataResult.success(UtilityCurve.valueOf(
                    value.toUpperCase(Locale.ROOT)
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(
                    () -> "Unknown utility curve: " + value
            );
        }
    }

    private static DataResult<UtilityAggregation> parseAggregation(
            String value
    ) {
        try {
            return DataResult.success(UtilityAggregation.valueOf(
                    value.toUpperCase(Locale.ROOT)
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(
                    () -> "Unknown utility aggregation: " + value
            );
        }
    }
}
