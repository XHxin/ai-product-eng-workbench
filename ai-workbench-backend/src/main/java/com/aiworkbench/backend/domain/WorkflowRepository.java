package com.aiworkbench.backend.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface WorkflowRepository extends JpaRepository<WorkflowRecord, Long> {
    Page<WorkflowRecord> findByCategoryOrderByIdDesc(String category, Pageable pageable);
    Page<WorkflowRecord> findByRequirementIdAndCategoryOrderByIdDesc(Long requirementId, String category, Pageable pageable);
    List<WorkflowRecord> findByRequirementIdAndCategoryOrderByIdDesc(Long requirementId, String category);
    List<WorkflowRecord> findByIterationIdAndCategoryOrderByIdDesc(Long iterationId, String category);
}
