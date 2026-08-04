package com.sporty.jackpot.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The record of a bet that won a jackpot pool. A bet can win at most once, so {@code betId} is unique.
 */
@Entity
@Table(name = "jackpot_rewards")
public class JackpotReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bet_id", nullable = false, unique = true)
    private UUID betId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "jackpot_id", nullable = false)
    private UUID jackpotId;

    @Column(name = "jackpot_reward_amount", nullable = false)
    private BigDecimal jackpotRewardAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JackpotReward() {
        // for JPA
    }

    public JackpotReward(UUID betId, UUID userId, UUID jackpotId, BigDecimal jackpotRewardAmount,
                         Instant createdAt) {
        this.betId = betId;
        this.userId = userId;
        this.jackpotId = jackpotId;
        this.jackpotRewardAmount = jackpotRewardAmount;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getBetId() {
        return betId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getJackpotId() {
        return jackpotId;
    }

    public BigDecimal getJackpotRewardAmount() {
        return jackpotRewardAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "JackpotReward{betId='%s', userId='%s', jackpotId='%s', amount=%s}"
                .formatted(betId, userId, jackpotId, jackpotRewardAmount);
    }
}
