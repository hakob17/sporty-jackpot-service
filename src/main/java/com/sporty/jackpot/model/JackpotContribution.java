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
 * The record of one bet contributing to a jackpot pool. It also doubles as the proof that a bet
 * took part in a jackpot, which is what the reward evaluation looks up.
 */
@Entity
@Table(name = "jackpot_contributions")
public class JackpotContribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bet_id", nullable = false, unique = true)
    private UUID betId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "jackpot_id", nullable = false)
    private UUID jackpotId;

    @Column(name = "stake_amount", nullable = false)
    private BigDecimal stakeAmount;

    @Column(name = "contribution_amount", nullable = false)
    private BigDecimal contributionAmount;

    /** The pool amount right after this contribution was applied. */
    @Column(name = "current_jackpot_amount", nullable = false)
    private BigDecimal currentJackpotAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JackpotContribution() {
        // for JPA
    }

    public JackpotContribution(UUID betId, UUID userId, UUID jackpotId, BigDecimal stakeAmount,
                               BigDecimal contributionAmount, BigDecimal currentJackpotAmount, Instant createdAt) {
        this.betId = betId;
        this.userId = userId;
        this.jackpotId = jackpotId;
        this.stakeAmount = stakeAmount;
        this.contributionAmount = contributionAmount;
        this.currentJackpotAmount = currentJackpotAmount;
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

    public BigDecimal getStakeAmount() {
        return stakeAmount;
    }

    public BigDecimal getContributionAmount() {
        return contributionAmount;
    }

    public BigDecimal getCurrentJackpotAmount() {
        return currentJackpotAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "JackpotContribution{betId='%s', userId='%s', jackpotId='%s', stake=%s, contribution=%s, pool=%s}"
                .formatted(betId, userId, jackpotId, stakeAmount, contributionAmount, currentJackpotAmount);
    }
}
