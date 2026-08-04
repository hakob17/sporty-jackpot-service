package com.sporty.jackpot.dto;

import com.sporty.jackpot.model.ContributionConfig;
import com.sporty.jackpot.model.RewardConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The mutable part of a jackpot.
 *
 * <p>{@code initialPoolAmount} is deliberately absent: the variable curves are derived from
 * {@code currentPool - initialPool}, so changing it after the fact would retroactively move every
 * percentage the jackpot has ever calculated.
 */
@Schema(description = "The mutable part of a jackpot - the initial pool amount is fixed at creation")
public record JackpotUpdateRequest(

        @Schema(description = "Human-readable name", example = "Weekend Special")
        @NotBlank(message = "name is required")
        String name,

        @NotNull(message = "contribution is required")
        @Valid
        ContributionConfig contribution,

        @NotNull(message = "reward is required")
        @Valid
        RewardConfig reward) {
}
