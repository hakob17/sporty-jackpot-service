package com.sporty.jackpot.controller;

import com.sporty.jackpot.dto.Bet;
import com.sporty.jackpot.dto.JackpotRewardResult;
import com.sporty.jackpot.service.BetService;
import com.sporty.jackpot.service.JackpotRewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/bets")
@Tag(name = "Bets", description = "Publish bets and evaluate them for a jackpot reward")
public class BetController {

    private final BetService betService;
    private final JackpotRewardService rewardService;

    public BetController(BetService betService, JackpotRewardService rewardService) {
        this.betService = betService;
        this.rewardService = rewardService;
    }

    /** Use case 1: publish a bet to the {@code jackpot-bets} topic. */
    @PostMapping
    @Operation(summary = "Publish a bet",
            description = """
                    Publishes the bet to the `jackpot-bets` Kafka topic. The consumer then matches the \
                    jackpot by `jackpotId` and contributes to its pool according to that jackpot's \
                    contribution configuration.

                    The jackpot is checked before publishing: a bet naming one that does not exist is \
                    rejected with 404 and never reaches the topic. Publishing the same `betId` twice \
                    is accepted — retries are legitimate — and contributes only once.""")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Bet accepted and published"),
            @ApiResponse(responseCode = "400", description = "Invalid bet", content = @Content),
            @ApiResponse(responseCode = "404", description = "No such jackpot - nothing was published",
                    content = @Content)
    })
    public ResponseEntity<Bet> publish(@Valid @RequestBody Bet bet) {
        betService.publish(bet);
        return ResponseEntity.accepted().body(bet);
    }

    /**
     * Use case 4: the jackpot reward outcome for a bet. Answers 200 with the outcome either way;
     * 404 if the bet has not been evaluated.
     */
    @GetMapping("/{betId}/jackpot-reward")
    @Operation(summary = "The bet's jackpot reward outcome",
            description = """
                    Reads the outcome decided when the bet was processed. The draw happens once, in the \
                    pipeline, immediately after the bet contributes — so this is a read, and asking \
                    again always gives the same answer.

                    Answers 200 either way; read the `won` field. The response also carries the chance \
                    the draw was made against and the value that came up, so an outcome can be \
                    explained rather than taken on trust.

                    404 means the bet has not been evaluated: it named a jackpot that does not exist, \
                    or it has not been consumed off the topic yet.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The outcome - see the `won` field"),
            @ApiResponse(responseCode = "404", description = "The bet has not been evaluated",
                    content = @Content)
    })
    public JackpotRewardResult rewardOutcome(
            @Parameter(description = "Id of a bet that has contributed to a jackpot",
                    example = "0a5d7e12-8c3b-4f6a-9d21-5b7c1e4f8a30")
            @PathVariable UUID betId) {
        return rewardService.resultFor(betId);
    }
}
