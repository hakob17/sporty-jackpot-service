package com.sporty.jackpot.dto;

import com.sporty.jackpot.model.JackpotEvaluation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The answer of the reward evaluation endpoint: either the bet won the pool, or it did not.
 */
@Schema(description = "The outcome of evaluating a bet for the jackpot reward")
public record JackpotRewardResult(

        @Schema(description = "The evaluated bet", example = "0a5d7e12-8c3b-4f6a-9d21-5b7c1e4f8a30")
        UUID betId,

        @Schema(description = "The user who placed it", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        UUID userId,

        @Schema(description = "The jackpot it played for", example = "33333333-3333-3333-3333-333333333333")
        UUID jackpotId,

        @Schema(description = "Whether the bet won the pool", example = "true")
        boolean won,

        @Schema(description = "The awarded pool amount; zero when the bet did not win", example = "200.00")
        BigDecimal jackpotRewardAmount,

        @Schema(description = "The chance the draw was made against, as a percentage", example = "100")
        BigDecimal chancePercentage,

        @Schema(description = "The value that came up, in [0, 100). The bet won when it is below the "
                + "chance — recorded so an outcome can be explained rather than taken on trust.",
                example = "42.7")
        BigDecimal drawnValue,

        @Schema(description = "When the bet was evaluated")
        Instant createdAt) {

    public static JackpotRewardResult of(JackpotEvaluation evaluation) {
        return new JackpotRewardResult(evaluation.getBetId(), evaluation.getUserId(),
                evaluation.getJackpotId(), evaluation.isWon(), evaluation.getAwardedAmount(),
                evaluation.getChancePercentage(), evaluation.getDrawnValue(), evaluation.getCreatedAt());
    }
}
