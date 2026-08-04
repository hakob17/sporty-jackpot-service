package com.sporty.jackpot.service.reward;

/**
 * The source of randomness for reward draws. Behind an interface so the reward evaluation can be
 * tested deterministically.
 */
public interface RandomProvider {

    /** A value in {@code [0, 100)} — the bet wins when this draw is below the jackpot's chance. */
    double drawPercentage();
}
