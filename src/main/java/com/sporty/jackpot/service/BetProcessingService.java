package com.sporty.jackpot.service;

import com.sporty.jackpot.dto.Bet;
import com.sporty.jackpot.model.JackpotContribution;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * What happens to a bet once it comes off the topic: it contributes to its jackpot, and is then
 * evaluated for the reward — in that order, in one transaction.
 *
 * <p>Both steps run under a single transaction so the jackpot row is locked once and the bet is
 * drawn against the pool it just contributed to, with nothing able to slip in between. It also means
 * a bet is evaluated at a moment the system chooses rather than one a client chooses, which is what
 * makes the outcome fair.
 *
 * <p>A bet that matched no jackpot contributes nothing and is not evaluated — there is no pool for
 * it to win.
 */
@Service
public class BetProcessingService {

    private final JackpotContributionService contributionService;
    private final JackpotRewardService rewardService;

    public BetProcessingService(JackpotContributionService contributionService,
                                JackpotRewardService rewardService) {
        this.contributionService = contributionService;
        this.rewardService = rewardService;
    }

    @Transactional
    public void process(Bet bet) {
        Optional<JackpotContribution> contribution = contributionService.contribute(bet);
        contribution.ifPresent(rewardService::evaluate);
    }
}
