package com.aiworkbench.backend.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequirementRepository extends JpaRepository<RequirementEntity, Long> {
    Optional<RequirementEntity> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(String status);

    List<RequirementEntity> findTop5ByOrderByIdDesc();

    List<RequirementEntity> findByIterationId(Long iterationId);
}
