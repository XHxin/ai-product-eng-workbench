package com.aiworkbench.backend.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IterationRepository extends JpaRepository<IterationEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select iteration from IterationEntity iteration where iteration.id = :id")
    Optional<IterationEntity> lockById(@Param("id") Long id);

    long countByStatus(String status);
}
