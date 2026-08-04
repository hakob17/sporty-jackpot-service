package com.sporty.jackpot.service;

import com.sporty.jackpot.dto.Bet;
import com.sporty.jackpot.messaging.BetPublisher;
import com.sporty.jackpot.repository.JackpotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Use case 1: hand a bet over to the {@code jackpot-bets} topic.
 *
 * <p>A bet is checked against the jackpots before it is published, so a client that names a jackpot
 * that does not exist gets a 404 instead of a 202 followed by silence. This is a fast-failure at the
 * edge, not a guarantee: the jackpot could still disappear between here and the consumer, and Kafka
 * can redeliver, so {@code JackpotContributionService} keeps its own "no matching jackpot" and
 * "already contributed" branches. The check makes the API honest; the consumer stays correct.
 */
@Service
public class BetService {

    private static final Logger log = LoggerFactory.getLogger(BetService.class);

    private final JackpotRepository jackpotRepository;
    private final BetPublisher betPublisher;

    public BetService(JackpotRepository jackpotRepository, BetPublisher betPublisher) {
        this.jackpotRepository = jackpotRepository;
        this.betPublisher = betPublisher;
    }

    /**
     * @throws ResourceNotFoundException if the bet names a jackpot that does not exist — nothing is
     *                                   published in that case
     */
    public void publish(Bet bet) {
        if (!jackpotRepository.existsById(bet.jackpotId())) {
            log.info("Rejecting bet {}: jackpot {} does not exist", bet.betId(), bet.jackpotId());
            throw new ResourceNotFoundException("Jackpot " + bet.jackpotId() + " does not exist");
        }
        betPublisher.publish(bet);
    }
}
