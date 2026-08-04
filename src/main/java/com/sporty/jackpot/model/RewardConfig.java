package com.sporty.jackpot.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

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

    /**
     * Checks this configuration against the pool of the jackpot it is being attached to.
     *
     * <p>A record validates its own fields in its compact constructor, but it cannot see the
     * jackpot — so rules that need both live here. Overriding is how a configuration opts in, which
     * keeps this polymorphic instead of a branch on the configuration type in {@link Jackpot}.
     *
     * @throws IllegalArgumentException if the combination would misbehave
     */
    default void validateAgainstPool(BigDecimal initialPoolAmount) {
    }
}
