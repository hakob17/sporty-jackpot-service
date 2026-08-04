package com.sporty.jackpot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A bet as accepted by {@code POST /api/bets} and as published on the {@code jackpot-bets} topic.
 */
@Schema(description = "A bet placed by a user against a jackpot")
public record Bet(

        @Schema(description = "Unique id of the bet. Contribution and reward are idempotent per bet id.",
                example = "0a5d7e12-8c3b-4f6a-9d21-5b7c1e4f8a30",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "betId is required")
        UUID betId,

        @Schema(description = "Id of the user who placed the bet",
                example = "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "userId is required")
        UUID userId,

        @Schema(description = "Id of the jackpot this bet plays for. A bet naming a jackpot that does "
                + "not exist contributes nothing.", example = "33333333-3333-3333-3333-333333333333",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "jackpotId is required")
        UUID jackpotId,

        @Schema(description = "The stake. The jackpot's contribution configuration decides what "
                + "percentage of this goes into the pool.", example = "100.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "betAmount is required")
        @DecimalMin(value = "0.01", message = "betAmount must be positive")
        BigDecimal betAmount) {
}
