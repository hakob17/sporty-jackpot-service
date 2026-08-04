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
 * The record that a bet was evaluated for the jackpot reward — win <em>or</em> loss.
 *
 * <p>This is what makes the evaluation happen exactly once. A bet is evaluated by the pipeline
 * immediately after it contributes, and {@code betId} is unique here, so there is no second draw to
 * be had: asking again returns this row. Without it, a loss left no trace and could simply be
 * re-drawn until it won.
 *
 * <p>It also records <em>how</em> the decision was made — the chance the draw was made against and
 * the value that came up — so a specific outcome can be explained after the fact rather than taken
 * on trust. {@link JackpotReward} remains the payout record, written only for winners.
 */
@Entity
@Table(name = "jackpot_evaluations")
public class JackpotEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bet_id", nullable = false, unique = true)
    private UUID betId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "jackpot_id", nullable = false)
    private UUID jackpotId;

    /** The chance, as a percentage, that this draw was made against. */
    @Column(name = "chance_percentage", nullable = false)
    private BigDecimal chancePercentage;

    /** The value that came up, in [0, 100). The bet won when this is below the chance. */
    @Column(name = "drawn_value", nullable = false)
    private BigDecimal drawnValue;

    @Column(name = "won", nullable = false)
    private boolean won;

    /** The pool paid out, or zero when the bet did not win. */
    @Column(name = "awarded_amount", nullable = false)
    private BigDecimal awardedAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JackpotEvaluation() {
        // for JPA
    }

    private JackpotEvaluation(UUID betId, UUID userId, UUID jackpotId, BigDecimal chancePercentage,
                              BigDecimal drawnValue, boolean won, BigDecimal awardedAmount,
                              Instant createdAt) {
        this.betId = betId;
        this.userId = userId;
        this.jackpotId = jackpotId;
        this.chancePercentage = chancePercentage;
        this.drawnValue = drawnValue;
        this.won = won;
        this.awardedAmount = awardedAmount;
        this.createdAt = createdAt;
    }

    public static JackpotEvaluation won(UUID betId, UUID userId, UUID jackpotId, BigDecimal chance,
                                        BigDecimal drawnValue, BigDecimal awardedAmount, Instant at) {
        return new JackpotEvaluation(betId, userId, jackpotId, chance, drawnValue, true, awardedAmount, at);
    }

    public static JackpotEvaluation lost(UUID betId, UUID userId, UUID jackpotId, BigDecimal chance,
                                         BigDecimal drawnValue, Instant at) {
        return new JackpotEvaluation(betId, userId, jackpotId, chance, drawnValue, false,
                BigDecimal.ZERO, at);
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

    public BigDecimal getChancePercentage() {
        return chancePercentage;
    }

    public BigDecimal getDrawnValue() {
        return drawnValue;
    }

    public boolean isWon() {
        return won;
    }

    public BigDecimal getAwardedAmount() {
        return awardedAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "JackpotEvaluation{betId=%s, jackpotId=%s, drew %s against %s%% -> %s}"
                .formatted(betId, jackpotId, drawnValue, chancePercentage, won ? "WON " + awardedAmount : "lost");
    }
}
