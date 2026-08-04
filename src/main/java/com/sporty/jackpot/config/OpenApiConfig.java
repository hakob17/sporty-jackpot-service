package com.sporty.jackpot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Describes the API served at /swagger-ui.html (document at /v3/api-docs). The per-endpoint
 * detail lives on the controllers as {@code @Operation} / {@code @ApiResponse} annotations.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jackpotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Jackpot Contribution and Reward Service")
                        .version("1.0.0")
                        .description("""
                                Receives bets, contributes each one to a matching jackpot pool and \
                                evaluates bets for a jackpot reward.

                                **Flow:** `POST /api/bets` publishes to the `jackpot-bets` Kafka topic; \
                                the consumer matches the jackpot by id and contributes to its pool. \
                                `POST /api/bets/{betId}/jackpot-reward` then draws against the jackpot's \
                                current chance — a win pays out the whole pool and resets it to the \
                                jackpot's initial amount.

                                Both contribution and reward are idempotent per `betId`: a redelivered \
                                bet is not counted twice, and a bet that already won is not paid twice.

                                Jackpots are seeded on startup (H2 is in-memory, so a restart resets \
                                them): `JP-FIXED`, `JP-VARIABLE`, and `JP-DEMO` — whose pool is \
                                guaranteed to pay out once it reaches 200.00, which makes a win \
                                reproducible without luck."""))
                .servers(List.of(new Server().url("/").description("This instance")));
    }
}
