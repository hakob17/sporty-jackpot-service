package com.sporty.jackpot.repository;

import com.sporty.jackpot.model.JackpotReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JackpotRewardRepository extends JpaRepository<JackpotReward, Long> {

    Optional<JackpotReward> findByBetId(UUID betId);
}
