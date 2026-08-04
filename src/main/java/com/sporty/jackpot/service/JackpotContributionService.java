package com.sporty.jackpot.service;

import com.sporty.jackpot.dto.Bet;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.JackpotContribution;
import com.sporty.jackpot.repository.JackpotContributionRepository;
import com.sporty.jackpot.repository.JackpotRepository;
import com.sporty.jackpot.service.contribution.ContributionStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Use case 3: a consumed bet contributes to its matching jackpot pool.
 */
@Service
public class JackpotContributionService {

    private static final Logger log = LoggerFactory.getLogger(JackpotContributionService.class);

    private final JackpotRepository jackpotRepository;
    private final JackpotContributionRepository contributionRepository;
    private final ContributionStrategies contributionStrategies;
    private final Clock clock;

    public JackpotContributionService(JackpotRepository jackpotRepository,
                                      JackpotContributionRepository contributionRepository,
                                      ContributionStrategies contributionStrategies,
                                      Clock clock) {
        this.jackpotRepository = jackpotRepository;
        this.contributionRepository = contributionRepository;
        this.contributionStrategies = contributionStrategies;
        this.clock = clock;
    }

    /**
     * Contributes the bet to the jackpot it names. Returns empty when there is no matching jackpot.
     *
     * <p>Idempotent: Kafka may redeliver a bet, and a bet must never be counted twice.
     */
    @Transactional
    public Optional<JackpotContribution> contribute(Bet bet) {
        Optional<JackpotContribution> alreadyContributed = contributionRepository.findByBetId(bet.betId());
        if (alreadyContributed.isPresent()) {
            log.info("Bet {} already contributed to jackpot {} - skipping", bet.betId(),
                    alreadyContributed.get().getJackpotId());
            return alreadyContributed;
        }

        Optional<Jackpot> matching = jackpotRepository.findByIdForUpdate(bet.jackpotId());
        if (matching.isEmpty()) {
            log.info("No jackpot {} for bet {} - no contribution made", bet.jackpotId(), bet.betId());
            return Optional.empty();
        }

        Jackpot jackpot = matching.get();
        BigDecimal percentage = contributionStrategies.percentageFor(jackpot);
        BigDecimal amount = contributionStrategies.contributionFor(jackpot, bet.betAmount());
        BigDecimal poolAfter = jackpot.contribute(amount);
        jackpotRepository.save(jackpot);

        JackpotContribution contribution = contributionRepository.save(new JackpotContribution(
                bet.betId(), bet.userId(), jackpot.getId(), bet.betAmount(), amount, poolAfter,
                Instant.now(clock)));

        log.info("Bet {} contributed {} ({}% {} of stake {}) to jackpot {} - pool is now {}",
                bet.betId(), amount, percentage,
                jackpot.getContributionConfig().getClass().getSimpleName(), bet.betAmount(),
                jackpot.getId(), poolAfter);
        return Optional.of(contribution);
    }
}
