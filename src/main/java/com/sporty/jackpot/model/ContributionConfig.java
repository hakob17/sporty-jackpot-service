package com.sporty.jackpot.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How a jackpot turns a bet amount into a pool contribution.
 *
 * <p>Each shape is its own record holding exactly the values it needs — there are no columns that
 * mean nothing for the configuration in use. The whole thing is stored as one JSON document on the
 * jackpot, so supporting a new shape is a new record plus a matching
 * {@code ContributionStrategy}: no schema change, no migration, and nothing existing to modify.
 *
 * <p>The {@code type} property is the persisted discriminator, written and read by Jackson.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FixedContribution.class, name = "FIXED"),
        @JsonSubTypes.Type(value = VariableContribution.class, name = "VARIABLE"),
        @JsonSubTypes.Type(value = CappedContribution.class, name = "CAPPED")
})
@Schema(description = "Contribution configuration; the `type` property selects the shape",
        discriminatorProperty = "type",
        oneOf = {FixedContribution.class, VariableContribution.class, CappedContribution.class})
public sealed interface ContributionConfig permits FixedContribution, VariableContribution, CappedContribution {
}
