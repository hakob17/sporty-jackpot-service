package com.sporty.jackpot.service;

import com.sporty.jackpot.dto.JackpotRequest;
import com.sporty.jackpot.dto.JackpotUpdateRequest;
import com.sporty.jackpot.model.FixedContribution;
import com.sporty.jackpot.model.FixedReward;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.VariableContribution;
import com.sporty.jackpot.model.VariableReward;
import com.sporty.jackpot.repository.JackpotContributionRepository;
import com.sporty.jackpot.repository.JackpotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JackpotServiceTest {

    private static final UUID JACKPOT_ID = UUID.randomUUID();

    private final JackpotRepository jackpotRepository = mock(JackpotRepository.class);
    private final JackpotContributionRepository contributionRepository = mock(JackpotContributionRepository.class);
    private final JackpotService service = new JackpotService(jackpotRepository, contributionRepository);

    @BeforeEach
    void setUp() {
        when(jackpotRepository.save(any(Jackpot.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static Jackpot existingJackpot() {
        return new Jackpot(JACKPOT_ID, "Original", new BigDecimal("1000.00"),
                new FixedContribution(new BigDecimal("5.00")),
                new FixedReward(new BigDecimal("10.00")));
    }

    @Test
    void createsAJackpotWithAGeneratedIdAndTheRequestedConfiguration() {
        JackpotRequest request = new JackpotRequest("Weekend Special", new BigDecimal("5000.00"),
                new VariableContribution(new BigDecimal("10.00"), new BigDecimal("2.00"),
                        new BigDecimal("1.00"), new BigDecimal("1000.00")),
                new FixedReward(new BigDecimal("10.00")));

        Jackpot created = service.create(request);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Weekend Special");
        assertThat(created.getInitialPoolAmount()).isEqualByComparingTo("5000.00");
        assertThat(created.getCurrentPoolAmount()).isEqualByComparingTo("5000.00");
        assertThat(created.getContributionConfig()).isInstanceOf(VariableContribution.class);
        assertThat(((VariableContribution) created.getContributionConfig()).minPercentage())
                .isEqualByComparingTo("2.00");
        assertThat(created.getRewardConfig()).isInstanceOf(FixedReward.class);
        verify(jackpotRepository).save(created);
    }

    @Test
    void refusesToCreateAJackpotWhoseNameIsTaken() {
        when(jackpotRepository.existsByName("Weekend Special")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new JackpotRequest("Weekend Special",
                new BigDecimal("5000.00"), new FixedContribution(new BigDecimal("5.00")),
                new FixedReward(new BigDecimal("10.00")))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Weekend Special");

        verify(jackpotRepository, never()).save(any());
    }

    @Test
    void refusesToRenameOntoAnotherJackpotsName() {
        when(jackpotRepository.findById(JACKPOT_ID)).thenReturn(Optional.of(existingJackpot()));
        when(jackpotRepository.existsByNameAndIdNot("Taken", JACKPOT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(JACKPOT_ID, new JackpotUpdateRequest("Taken",
                new FixedContribution(new BigDecimal("5.00")),
                new FixedReward(new BigDecimal("10.00")))))
                .isInstanceOf(ConflictException.class);

        verify(jackpotRepository, never()).save(any());
    }

    @Test
    void aJackpotMayKeepItsOwnName() {
        Jackpot jackpot = existingJackpot();
        when(jackpotRepository.findById(JACKPOT_ID)).thenReturn(Optional.of(jackpot));
        // existsByNameAndIdNot excludes this jackpot, so its own name never collides with itself
        when(jackpotRepository.existsByNameAndIdNot("Original", JACKPOT_ID)).thenReturn(false);

        Jackpot updated = service.update(JACKPOT_ID, new JackpotUpdateRequest("Original",
                new FixedContribution(new BigDecimal("9.00")),
                new FixedReward(new BigDecimal("10.00"))));

        assertThat(updated.getName()).isEqualTo("Original");
        assertThat(((FixedContribution) updated.getContributionConfig()).percentage())
                .isEqualByComparingTo("9.00");
    }

    @Test
    void updateRenamesAndReconfiguresWithoutTouchingThePool() {
        Jackpot jackpot = existingJackpot();
        jackpot.contribute(new BigDecimal("250.00"));
        when(jackpotRepository.findById(JACKPOT_ID)).thenReturn(Optional.of(jackpot));

        Jackpot updated = service.update(JACKPOT_ID, new JackpotUpdateRequest("Renamed",
                new FixedContribution(new BigDecimal("7.50")),
                new VariableReward(new BigDecimal("1.00"), new BigDecimal("2.00"),
                        new BigDecimal("1000.00"), new BigDecimal("20000.00"))));

        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(((FixedContribution) updated.getContributionConfig()).percentage())
                .isEqualByComparingTo("7.50");
        assertThat(updated.getRewardConfig()).isInstanceOf(VariableReward.class);
        assertThat(updated.getCurrentPoolAmount()).isEqualByComparingTo("1250.00");
        assertThat(updated.getInitialPoolAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void updatingAJackpotThatDoesNotExistIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(jackpotRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(unknown, new JackpotUpdateRequest("x",
                new FixedContribution(new BigDecimal("5.00")),
                new FixedReward(new BigDecimal("5.00")))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletesAJackpotThatNothingHasContributedTo() {
        Jackpot jackpot = existingJackpot();
        when(jackpotRepository.findById(JACKPOT_ID)).thenReturn(Optional.of(jackpot));
        when(contributionRepository.existsByJackpotId(JACKPOT_ID)).thenReturn(false);

        service.delete(JACKPOT_ID);

        verify(jackpotRepository).delete(jackpot);
    }

    @Test
    void refusesToDeleteAJackpotThatHasContributions() {
        Jackpot jackpot = existingJackpot();
        when(jackpotRepository.findById(JACKPOT_ID)).thenReturn(Optional.of(jackpot));
        when(contributionRepository.existsByJackpotId(JACKPOT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(JACKPOT_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(JACKPOT_ID.toString());

        // The contribution rows are the record of real bets - never orphan them.
        verify(jackpotRepository, never()).delete(any());
    }
}
