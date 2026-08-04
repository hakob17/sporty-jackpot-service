package com.sporty.jackpot.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How a jackpot decides the chance that a contributing bet wins the pool.
 *
 * <p>Same shape as {@link ContributionConfig}: one record per rule, stored as a JSON document, so a
 * new rule is a new record plus a matching {@code RewardChanceStrategy}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FixedReward.class, name = "FIXED"),
        @JsonSubTypes.Type(value = VariableReward.class, name = "VARIABLE")
})
@Schema(description = "Reward configuration; the `type` property selects the shape",
        discriminatorProperty = "type",
        oneOf = {FixedReward.class, VariableReward.class})
public sealed interface RewardConfig permits FixedReward, VariableReward {
}
