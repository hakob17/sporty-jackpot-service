package com.sporty.jackpot.controller;

import com.sporty.jackpot.dto.Bet;
import com.sporty.jackpot.dto.JackpotRewardResult;
import com.sporty.jackpot.service.BetService;
import com.sporty.jackpot.service.JackpotRewardService;
import com.sporty.jackpot.service.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BetController.class)
class BetControllerTest {

    private static final UUID BET_ID = UUID.fromString("0a5d7e12-8c3b-4f6a-9d21-5b7c1e4f8a30");
    private static final UUID USER_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final UUID JACKPOT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BetService betService;

    @MockBean
    private JackpotRewardService rewardService;

    @Test
    void publishingABetIsAccepted() throws Exception {
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"betId":"%s","userId":"%s","jackpotId":"%s","betAmount":200.00}"""
                                .formatted(BET_ID, USER_ID, JACKPOT_ID)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.betId").value(BET_ID.toString()));

        verify(betService).publish(new Bet(BET_ID, USER_ID, JACKPOT_ID, new BigDecimal("200.00")));
    }

    @Test
    void anInvalidBetIsRejected() throws Exception {
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"betId":null,"userId":"%s","jackpotId":"%s","betAmount":0}"""
                                .formatted(USER_ID, JACKPOT_ID)))
                .andExpect(status().isBadRequest());

        verify(betService, never()).publish(any());
    }

    @Test
    void anIdThatIsNotAUuidIsRejected() throws Exception {
        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"betId":"not-a-uuid","userId":"%s","jackpotId":"%s","betAmount":200.00}"""
                                .formatted(USER_ID, JACKPOT_ID)))
                .andExpect(status().isBadRequest());

        verify(betService, never()).publish(any());
    }

    @Test
    void aBetForAJackpotThatDoesNotExistIsNotFound() throws Exception {
        UUID unknownJackpot = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Jackpot " + unknownJackpot + " does not exist"))
                .when(betService).publish(any());

        mockMvc.perform(post("/api/bets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"betId":"%s","userId":"%s","jackpotId":"%s","betAmount":200.00}"""
                                .formatted(BET_ID, USER_ID, unknownJackpot)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theRewardOutcomeIsRead_notDrawn() throws Exception {
        when(rewardService.resultFor(BET_ID)).thenReturn(new JackpotRewardResult(BET_ID, USER_ID,
                JACKPOT_ID, true, new BigDecimal("1500.00"), new BigDecimal("10.00"),
                new BigDecimal("4.2"), Instant.parse("2026-08-04T10:15:30Z")));

        mockMvc.perform(get("/api/bets/{betId}/jackpot-reward", BET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.won").value(true))
                .andExpect(jsonPath("$.jackpotRewardAmount").value(1500.00))
                .andExpect(jsonPath("$.chancePercentage").value(10.00))
                .andExpect(jsonPath("$.drawnValue").value(4.2));
    }

    @Test
    void theOutcomeEndpointNoLongerAcceptsPost() throws Exception {
        // It is a read now - a POST would imply asking for a fresh draw, which is the bug it fixed.
        mockMvc.perform(post("/api/bets/{betId}/jackpot-reward", BET_ID))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void askingForABetThatWasNeverEvaluatedIsNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(rewardService.resultFor(unknown))
                .thenThrow(new ResourceNotFoundException("Bet " + unknown + " has not been evaluated"));

        mockMvc.perform(get("/api/bets/{betId}/jackpot-reward", unknown))
                .andExpect(status().isNotFound());
    }

    @Test
    void askingWithAnIdThatIsNotAUuidIsRejected() throws Exception {
        mockMvc.perform(get("/api/bets/{betId}/jackpot-reward", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
