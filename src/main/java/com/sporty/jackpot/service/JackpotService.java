package com.sporty.jackpot.service;

import com.sporty.jackpot.dto.JackpotRequest;
import com.sporty.jackpot.dto.JackpotUpdateRequest;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.JackpotContribution;
import com.sporty.jackpot.repository.JackpotContributionRepository;
import com.sporty.jackpot.repository.JackpotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Administration of jackpots, and read access used to observe the pipeline.
 */
@Service
@Transactional(readOnly = true)
public class JackpotService {

    private static final Logger log = LoggerFactory.getLogger(JackpotService.class);

    private final JackpotRepository jackpotRepository;
    private final JackpotContributionRepository contributionRepository;

    public JackpotService(JackpotRepository jackpotRepository,
                          JackpotContributionRepository contributionRepository) {
        this.jackpotRepository = jackpotRepository;
        this.contributionRepository = contributionRepository;
    }

    public List<Jackpot> findAll() {
        return jackpotRepository.findAll();
    }

    public Jackpot findById(UUID jackpotId) {
        return jackpotRepository.findById(jackpotId)
                .orElseThrow(() -> new ResourceNotFoundException("Jackpot " + jackpotId + " does not exist"));
    }

    public List<JackpotContribution> findContributions(UUID jackpotId) {
        Jackpot jackpot = findById(jackpotId);
        return contributionRepository.findByJackpotIdOrderByCreatedAtDesc(jackpot.getId());
    }

    /**
     * @throws ConflictException if another jackpot already uses the name — names identify a jackpot
     *                           to humans, so two of them would make logs and admin screens
     *                           ambiguous. The unique index on the column is the backstop for two
     *                           creates racing past this check.
     */
    @Transactional
    public Jackpot create(JackpotRequest request) {
        if (jackpotRepository.existsByName(request.name())) {
            throw new ConflictException("A jackpot named '" + request.name() + "' already exists");
        }
        Jackpot jackpot = jackpotRepository.save(new Jackpot(UUID.randomUUID(), request.name(),
                request.initialPoolAmount(), request.contribution(), request.reward()));
        log.info("Created {}", jackpot);
        return jackpot;
    }

    /**
     * Renames and reconfigures a jackpot. The pool and the initial amount are untouched, and
     * contributions already made keep the amounts they were computed with — only future bets see
     * the new configuration.
     *
     * @throws ConflictException if a *different* jackpot already uses the requested name; keeping
     *                           its own name is always allowed
     */
    @Transactional
    public Jackpot update(UUID jackpotId, JackpotUpdateRequest request) {
        Jackpot jackpot = findById(jackpotId);
        if (jackpotRepository.existsByNameAndIdNot(request.name(), jackpotId)) {
            throw new ConflictException("A jackpot named '" + request.name() + "' already exists");
        }
        jackpot.rename(request.name());
        jackpot.reconfigure(request.contribution(), request.reward());
        Jackpot saved = jackpotRepository.save(jackpot);
        log.info("Updated {}", saved);
        return saved;
    }

    /**
     * @throws ConflictException if anything has contributed to the jackpot — those rows are the
     *                           record of real bets and must not be orphaned
     */
    @Transactional
    public void delete(UUID jackpotId) {
        Jackpot jackpot = findById(jackpotId);
        if (contributionRepository.existsByJackpotId(jackpot.getId())) {
            throw new ConflictException("Jackpot " + jackpotId
                    + " has contributions and cannot be deleted");
        }
        jackpotRepository.delete(jackpot);
        log.info("Deleted jackpot {} ({})", jackpotId, jackpot.getName());
    }
}
