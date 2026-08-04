package com.sporty.jackpot.dto;

import com.sporty.jackpot.model.ContributionConfig;
import com.sporty.jackpot.model.RewardConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * A jackpot to create. The id is assigned by the service.
 */
@Schema(description = "A jackpot to create")
public record JackpotRequest(

        @Schema(description = "Human-readable name", example = "Weekend Special")
        @NotBlank(message = "name is required")
        String name,

        @Schema(description = "The pool starts here and returns here whenever the jackpot is awarded",
                example = "5000.00")
        @NotNull(message = "initialPoolAmount is required")
        @DecimalMin(value = "0.00", message = "initialPoolAmount must not be negative")
        BigDecimal initialPoolAmount,

        @NotNull(message = "contribution is required")
        @Valid
        ContributionConfig contribution,

        @NotNull(message = "reward is required")
        @Valid
        RewardConfig reward) {
}
