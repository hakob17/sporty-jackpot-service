package com.sporty.jackpot.repository;

import com.sporty.jackpot.model.Jackpot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JackpotRepository extends JpaRepository<Jackpot, UUID> {

    /** A name is taken when any jackpot already uses it. */
    boolean existsByName(String name);

    /** A name is taken on update when a *different* jackpot already uses it. */
    boolean existsByNameAndIdNot(String name, UUID id);

    /**
     * Locks the jackpot row for the duration of the transaction. Both the contribution and the
     * reward flow read-modify-write the pool, so they must not interleave.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Jackpot j where j.id = :id")
    Optional<Jackpot> findByIdForUpdate(@Param("id") UUID id);
}
