package com.sporty.jackpot.service.reward;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class ThreadLocalRandomProvider implements RandomProvider {

    @Override
    public double drawPercentage() {
        return ThreadLocalRandom.current().nextDouble(100.0);
    }
}
