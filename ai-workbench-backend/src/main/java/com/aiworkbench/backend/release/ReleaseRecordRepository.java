package com.aiworkbench.backend.release;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReleaseRecordRepository extends JpaRepository<ReleaseRecordEntity, Long> {
    Optional<ReleaseRecordEntity> findByIterationIdAndVersion(Long iterationId, String version);

    List<ReleaseRecordEntity> findByIterationIdOrderByIdDesc(Long iterationId);

    Page<ReleaseRecordEntity> findAllByOrderByIdDesc(Pageable pageable);

    long countByReleaseStatusNot(String releaseStatus);
}
