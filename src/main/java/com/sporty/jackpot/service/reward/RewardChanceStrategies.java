package com.sporty.jackpot.service.reward;

import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.RewardConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the reward strategy for a jackpot's configuration and applies it.
 *
 * <p>Every {@link RewardChanceStrategy} bean registers itself here, keyed by the configuration
 * record it declares, so a new configuration needs no change in this class.
 */
@Component
public class RewardChanceStrategies {

    private final Map<Class<? extends RewardConfig>, RewardChanceStrategy<?>> byConfig = new HashMap<>();

    public RewardChanceStrategies(List<RewardChanceStrategy<?>> strategies) {
        for (RewardChanceStrategy<?> strategy : strategies) {
            RewardChanceStrategy<?> previous = byConfig.put(strategy.configType(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Two reward strategies for " + strategy.configType());
            }
        }
    }

    /** The jackpot's current win chance, as a percentage between 0 and 100. */
    public BigDecimal chanceFor(Jackpot jackpot) {
        RewardConfig config = jackpot.getRewardConfig();
        return strategyFor(config).chanceFor(jackpot, config);
    }

    /**
     * The cast is safe by construction: the map is keyed by the very class the strategy declared it
     * handles, so a strategy is only ever handed a config of its own type.
     */
    @SuppressWarnings("unchecked")
    private RewardChanceStrategy<RewardConfig> strategyFor(RewardConfig config) {
        RewardChanceStrategy<?> strategy = byConfig.get(config.getClass());
        if (strategy == null) {
            throw new IllegalStateException("No reward strategy for " + config.getClass().getSimpleName());
        }
        return (RewardChanceStrategy<RewardConfig>) strategy;
    }
}
