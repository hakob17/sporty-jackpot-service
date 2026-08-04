package com.sporty.jackpot.service;

import com.sporty.jackpot.dto.JackpotRewardResult;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.JackpotContribution;
import com.sporty.jackpot.model.JackpotEvaluation;
import com.sporty.jackpot.model.JackpotReward;
import com.sporty.jackpot.repository.JackpotEvaluationRepository;
import com.sporty.jackpot.repository.JackpotRepository;
import com.sporty.jackpot.repository.JackpotRewardRepository;
import com.sporty.jackpot.service.reward.RandomProvider;
import com.sporty.jackpot.service.reward.RewardChanceStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Use case 4: decide whether a contributing bet wins the jackpot pool.
 *
 * <p>The decision is made <b>once</b>, by the pipeline, immediately after the bet contributes — not
 * when a client asks. That closes two holes at the same time: a client cannot choose the moment it
 * claims (and so cannot wait for the pool and the chance to grow), and cannot ask again after a loss
 * until it wins. Both were possible while the draw happened inside the endpoint and only wins were
 * recorded.
 *
 * <p>{@link #resultFor(UUID)} is a pure read of what was already decided.
 */
@Service
public class JackpotRewardService {

    private static final Logger log = LoggerFactory.getLogger(JackpotRewardService.class);

    private final JackpotRepository jackpotRepository;
    private final JackpotEvaluationRepository evaluationRepository;
    private final JackpotRewardRepository rewardRepository;
    private final RewardChanceStrategies rewardChanceStrategies;
    private final RandomProvider randomProvider;
    private final Clock clock;

    public JackpotRewardService(JackpotRepository jackpotRepository,
                                JackpotEvaluationRepository evaluationRepository,
                                JackpotRewardRepository rewardRepository,
                                RewardChanceStrategies rewardChanceStrategies,
                                RandomProvider randomProvider,
                                Clock clock) {
        this.jackpotRepository = jackpotRepository;
        this.evaluationRepository = evaluationRepository;
        this.rewardRepository = rewardRepository;
        this.rewardChanceStrategies = rewardChanceStrategies;
        this.randomProvider = randomProvider;
        this.clock = clock;
    }

    /**
     * Draws for a contributing bet, once. A win pays out the whole pool and resets it to the
     * jackpot's initial amount.
     *
     * <p>Idempotent by {@code betId}: Kafka redelivers, so a bet that has already been evaluated
     * returns its stored evaluation instead of drawing again.
     */
    @Transactional
    public JackpotEvaluation evaluate(JackpotContribution contribution) {
        Optional<JackpotEvaluation> alreadyEvaluated =
                evaluationRepository.findByBetId(contribution.getBetId());
        if (alreadyEvaluated.isPresent()) {
            log.info("Bet {} was already evaluated - keeping {}", contribution.getBetId(),
                    alreadyEvaluated.get());
            return alreadyEvaluated.get();
        }
        return draw(contribution);
    }

    private JackpotEvaluation draw(JackpotContribution contribution) {
        UUID betId = contribution.getBetId();
        // Already locked by the contribution in this same transaction; re-reading keeps the lock.
        Jackpot jackpot = jackpotRepository.findByIdForUpdate(contribution.getJackpotId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Jackpot " + contribution.getJackpotId() + " no longer exists"));

        BigDecimal chance = rewardChanceStrategies.chanceFor(jackpot);
        BigDecimal drawn = BigDecimal.valueOf(randomProvider.drawPercentage());
        Instant now = Instant.now(clock);

        if (drawn.compareTo(chance) >= 0) {
            log.info("Bet {} did not win jackpot {} - drew {} against a {}% chance (pool stays {})",
                    betId, jackpot.getId(), drawn, chance, jackpot.getCurrentPoolAmount());
            return evaluationRepository.save(JackpotEvaluation.lost(betId, contribution.getUserId(),
                    jackpot.getId(), chance, drawn, now));
        }

        BigDecimal awarded = jackpot.awardPool();
        jackpotRepository.save(jackpot);
        rewardRepository.save(new JackpotReward(betId, contribution.getUserId(), jackpot.getId(),
                awarded, now));

        log.info("Bet {} WON jackpot {} - awarded {} (drew {} against {}%), pool reset to {}",
                betId, jackpot.getId(), awarded, drawn, chance, jackpot.getCurrentPoolAmount());
        return evaluationRepository.save(JackpotEvaluation.won(betId, contribution.getUserId(),
                jackpot.getId(), chance, drawn, awarded, now));
    }

    /**
     * Reads the outcome decided when the bet was processed.
     *
     * @throws ResourceNotFoundException if the bet has not been evaluated — it never contributed to
     *                                   a jackpot, or has not been consumed yet
     */
    @Transactional(readOnly = true)
    public JackpotRewardResult resultFor(UUID betId) {
        return evaluationRepository.findByBetId(betId)
                .map(JackpotRewardResult::of)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bet " + betId + " has not been evaluated for a jackpot reward"));
    }
}
