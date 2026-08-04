package com.sporty.jackpot.repository;

import com.sporty.jackpot.model.JackpotEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JackpotEvaluationRepository extends JpaRepository<JackpotEvaluation, Long> {

    Optional<JackpotEvaluation> findByBetId(UUID betId);
}
