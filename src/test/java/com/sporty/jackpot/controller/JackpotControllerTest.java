package com.sporty.jackpot.controller;

import com.sporty.jackpot.model.CappedContribution;
import com.sporty.jackpot.model.FixedReward;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.VariableContribution;
import com.sporty.jackpot.service.ConflictException;
import com.sporty.jackpot.service.JackpotService;
import com.sporty.jackpot.service.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JackpotController.class)
class JackpotControllerTest {

    private static final UUID JACKPOT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JackpotService jackpotService;

    private static Jackpot jackpot(String name) {
        return new Jackpot(JACKPOT_ID, name, new BigDecimal("5000.00"),
                new VariableContribution(new BigDecimal("10.00"), new BigDecimal("2.00"),
                        new BigDecimal("1.00"), new BigDecimal("1000.00")),
                new FixedReward(new BigDecimal("10.00")));
    }

    private static Jackpot cappedJackpot() {
        return new Jackpot(JACKPOT_ID, "Capped Special", new BigDecimal("5000.00"),
                new CappedContribution(new BigDecimal("10.00"), new BigDecimal("50.00")),
                new FixedReward(new BigDecimal("10.00")));
    }

    @Test
    void creatingAJackpotReturnsItsLocation() throws Exception {
        when(jackpotService.create(any())).thenReturn(jackpot("Weekend Special"));

        mockMvc.perform(post("/api/jackpots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Weekend Special",
                                  "initialPoolAmount": 5000.00,
                                  "contribution": {"type":"VARIABLE","percentage":10.00,"minPercentage":2.00,
                                                   "decreasePerStep":1.00,"poolStep":1000.00},
                                  "reward": {"type":"FIXED","chancePercentage":10.00}
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/jackpots/" + JACKPOT_ID))
                .andExpect(jsonPath("$.name").value("Weekend Special"))
                .andExpect(jsonPath("$.contributionConfig.type").value("VARIABLE"));
    }

    @Test
    void aVariableContributionMissingItsOwnFieldsIsRejected() throws Exception {
        // percentage alone is enough for FIXED, but VARIABLE needs all four - the typed request
        // records are what make that difference enforceable.
        mockMvc.perform(post("/api/jackpots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Half configured",
                                  "initialPoolAmount": 5000.00,
                                  "contribution": {"type":"VARIABLE","percentage":10.00},
                                  "reward": {"type":"FIXED","chancePercentage":10.00}
                                }"""))
                .andExpect(status().isBadRequest());

        verify(jackpotService, never()).create(any());
    }

    @Test
    void anUnknownContributionTypeIsRejected() throws Exception {
        mockMvc.perform(post("/api/jackpots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad type",
                                  "initialPoolAmount": 5000.00,
                                  "contribution": {"type":"LOGARITHMIC","percentage":10.00},
                                  "reward": {"type":"FIXED","chancePercentage":10.00}
                                }"""))
                .andExpect(status().isBadRequest());

        verify(jackpotService, never()).create(any());
    }

    @Test
    void creatingAJackpotWithACappedContributionReturnsTheStoredConfiguration() throws Exception {
        when(jackpotService.create(any())).thenReturn(cappedJackpot());

        mockMvc.perform(post("/api/jackpots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Capped Special",
                                  "initialPoolAmount": 5000.00,
                                  "contribution": {"type":"CAPPED","percentage":10.00,"maxContribution":50.00},
                                  "reward": {"type":"FIXED","chancePercentage":10.00}
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/jackpots/" + JACKPOT_ID))
                .andExpect(jsonPath("$.contributionConfig.type").value("CAPPED"))
                .andExpect(jsonPath("$.contributionConfig.percentage").value(10.00))
                .andExpect(jsonPath("$.contributionConfig.maxContribution").value(50.00));
    }

    @Test
    void aCappedContributionMissingItsCeilingIsRejected() throws Exception {
        mockMvc.perform(post("/api/jackpots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad capped",
                                  "initialPoolAmount": 5000.00,
                                  "contribution": {"type":"CAPPED","percentage":10.00},
                                  "reward": {"type":"FIXED","chancePercentage":10.00}
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("maxContribution")));

        verify(jackpotService, never()).create(any());
    }

    @Test
    void aCappedContributionWithAZeroPercentageIsRejected() throws Exception {
        mockMvc.perform(post("/api/jackpots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad percentage",
                                  "initialPoolAmount": 5000.00,
                                  "contribution": {"type":"CAPPED","percentage":0,"maxContribution":50.00},
                                  "reward": {"type":"FIXED","chancePercentage":10.00}
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("percentage")));

        verify(jackpotService, never()).create(any());
    }

    @Test
    void updatingAJackpotReturnsTheNewState() throws Exception {
        when(jackpotService.update(eq(JACKPOT_ID), any())).thenReturn(jackpot("Renamed"));

        mockMvc.perform(put("/api/jackpots/{id}", JACKPOT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Renamed",
                                  "contribution": {"type":"FIXED","percentage":7.50},
                                  "reward": {"type":"FIXED","chancePercentage":10.00}
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void deletingAJackpotWithNoContributionsIsNoContent() throws Exception {
        mockMvc.perform(delete("/api/jackpots/{id}", JACKPOT_ID))
                .andExpect(status().isNoContent());

        verify(jackpotService).delete(JACKPOT_ID);
    }

    @Test
    void deletingAJackpotThatHasContributionsIsAConflict() throws Exception {
        doThrow(new ConflictException("Jackpot " + JACKPOT_ID + " has contributions and cannot be deleted"))
                .when(jackpotService).delete(JACKPOT_ID);

        mockMvc.perform(delete("/api/jackpots/{id}", JACKPOT_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("contributions")));
    }

    @Test
    void deletingAJackpotThatDoesNotExistIsNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Jackpot " + unknown + " does not exist"))
                .when(jackpotService).delete(unknown);

        mockMvc.perform(delete("/api/jackpots/{id}", unknown))
                .andExpect(status().isNotFound());
    }
}
