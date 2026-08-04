package com.sporty.jackpot.repository;

import com.sporty.jackpot.model.JackpotContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JackpotContributionRepository extends JpaRepository<JackpotContribution, Long> {

    Optional<JackpotContribution> findByBetId(UUID betId);

    boolean existsByBetId(UUID betId);

    boolean existsByJackpotId(UUID jackpotId);

    List<JackpotContribution> findByJackpotIdOrderByCreatedAtDesc(UUID jackpotId);
}
