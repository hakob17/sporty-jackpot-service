package com.sporty.jackpot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A jackpot pool: it starts at a configurable initial amount, grows with every contributing bet
 * and is reset to that initial amount whenever it is awarded.
 */
@Entity
@Table(name = "jackpots",
        uniqueConstraints = @UniqueConstraint(name = "uk_jackpots_name", columnNames = "name"))
public class Jackpot {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "initial_pool_amount", nullable = false)
    private BigDecimal initialPoolAmount;

    @Column(name = "current_pool_amount", nullable = false)
    private BigDecimal currentPoolAmount;

    /**
     * Stored as one JSON document rather than a column per field: the shapes have different fields,
     * and flattening them into the table would mean a column set that is mostly null for any given
     * jackpot. `jsonb` on PostgreSQL, `json` on H2.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "contribution_config", nullable = false)
    private ContributionConfig contributionConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reward_config", nullable = false)
    private RewardConfig rewardConfig;

    protected Jackpot() {
        // for JPA
    }

    public Jackpot(UUID id, String name, BigDecimal initialPoolAmount,
                   ContributionConfig contributionConfig, RewardConfig rewardConfig) {
        if (id == null) {
            throw new IllegalArgumentException("jackpot id must not be null");
        }
        if (initialPoolAmount == null || initialPoolAmount.signum() < 0) {
            throw new IllegalArgumentException("initialPoolAmount must not be negative");
        }
        this.id = id;
        this.name = requireName(name);
        this.initialPoolAmount = Money.scaled(initialPoolAmount);
        this.currentPoolAmount = this.initialPoolAmount;
        this.contributionConfig = requireConfig(contributionConfig, "contributionConfig");
        this.rewardConfig = requireConfig(rewardConfig, "rewardConfig");
    }

    private static <T> T requireConfig(T config, String name) {
        if (config == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return config;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("jackpot name must not be blank");
        }
        return name;
    }

    public void rename(String name) {
        this.name = requireName(name);
    }

    /**
     * Replaces both configurations. Contributions already made keep the amounts they were computed
     * with — this only changes what future bets do.
     */
    public void reconfigure(ContributionConfig contributionConfig, RewardConfig rewardConfig) {
        this.contributionConfig = requireConfig(contributionConfig, "contributionConfig");
        this.rewardConfig = requireConfig(rewardConfig, "rewardConfig");
    }

    /** Adds the contribution to the pool and returns the new pool amount. */
    public BigDecimal contribute(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("contribution amount must not be negative");
        }
        currentPoolAmount = Money.scaled(currentPoolAmount.add(amount));
        return currentPoolAmount;
    }

    /** Hands out the whole pool and resets it to the initial amount. Returns the awarded amount. */
    public BigDecimal awardPool() {
        BigDecimal awarded = currentPoolAmount;
        currentPoolAmount = initialPoolAmount;
        return awarded;
    }

    /** How much the pool has grown above its initial amount — the input of the variable configurations. */
    public BigDecimal poolGrowth() {
        BigDecimal growth = currentPoolAmount.subtract(initialPoolAmount);
        return growth.signum() < 0 ? BigDecimal.ZERO : growth;
    }

    public UUID getId() {
        return id;
    }

    /** Human-readable label — the id is a UUID, so this is what makes a jackpot recognisable. */
    public String getName() {
        return name;
    }

    public BigDecimal getInitialPoolAmount() {
        return initialPoolAmount;
    }

    public BigDecimal getCurrentPoolAmount() {
        return currentPoolAmount;
    }

    public ContributionConfig getContributionConfig() {
        return contributionConfig;
    }

    public RewardConfig getRewardConfig() {
        return rewardConfig;
    }

    @Override
    public String toString() {
        return "Jackpot{id=%s, name='%s', pool=%s, initial=%s, contribution=%s, reward=%s}"
                .formatted(id, name, currentPoolAmount, initialPoolAmount,
                        contributionConfig.getClass().getSimpleName(),
                        rewardConfig.getClass().getSimpleName());
    }
}
