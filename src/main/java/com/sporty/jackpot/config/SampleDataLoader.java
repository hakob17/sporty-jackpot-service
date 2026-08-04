package com.sporty.jackpot.config;

import com.sporty.jackpot.model.FixedContribution;
import com.sporty.jackpot.model.FixedReward;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.VariableContribution;
import com.sporty.jackpot.model.VariableReward;
import com.sporty.jackpot.repository.JackpotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Seeds the in-memory database with one jackpot per configuration combination so the service is
 * usable straight after startup. H2 is in-memory, so this runs again on every restart.
 *
 * <p>The ids are fixed and deliberately memorable — jackpots are reference data, and the docs, the
 * Postman collection and the smoke test all address them by id.
 */
@Component
public class SampleDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);

    /** Flat 5% of every stake, flat 10% chance to win. */
    public static final UUID FIXED_JACKPOT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Contribution and chance both move as the pool grows. */
    public static final UUID VARIABLE_JACKPOT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** Small pool with a fast growing chance — pays out for certain at 200.00. */
    public static final UUID DEMO_JACKPOT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final JackpotRepository jackpotRepository;

    public SampleDataLoader(JackpotRepository jackpotRepository) {
        this.jackpotRepository = jackpotRepository;
    }

    @Override
    public void run(String... args) {
        if (jackpotRepository.count() > 0) {
            return;
        }

        List<Jackpot> jackpots = List.of(
                // Flat 5% of every stake, flat 10% chance to win.
                new Jackpot(FIXED_JACKPOT_ID, "Daily Fixed",
                        new BigDecimal("10000.00"),
                        new FixedContribution(new BigDecimal("5.00")),
                        new FixedReward(new BigDecimal("10.00"))),

                // Contribution starts at 10% and loses 1 point per 1000 of pool growth, floor 2%.
                // Chance starts at 1% and gains 2 points per 1000 of growth, guaranteed at 20000.
                new Jackpot(VARIABLE_JACKPOT_ID, "Progressive Weekly",
                        new BigDecimal("5000.00"),
                        new VariableContribution(new BigDecimal("10.00"), new BigDecimal("2.00"),
                                new BigDecimal("1.00"), new BigDecimal("1000.00")),
                        new VariableReward(new BigDecimal("1.00"), new BigDecimal("2.00"),
                                new BigDecimal("1000.00"), new BigDecimal("20000.00"))),

                // Small pool with a fast growing chance - handy to see a reward without much luck.
                new Jackpot(DEMO_JACKPOT_ID, "Demo",
                        new BigDecimal("100.00"),
                        new FixedContribution(new BigDecimal("10.00")),
                        new VariableReward(new BigDecimal("5.00"), new BigDecimal("10.00"),
                                new BigDecimal("50.00"), new BigDecimal("200.00"))));

        jackpotRepository.saveAll(jackpots);
        jackpots.forEach(jackpot -> log.info("Seeded {}", jackpot));
    }
}
