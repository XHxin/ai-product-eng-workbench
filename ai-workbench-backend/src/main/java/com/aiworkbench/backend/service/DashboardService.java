package com.aiworkbench.backend.service;

import com.aiworkbench.backend.api.Views;
import com.aiworkbench.backend.domain.IterationRepository;
import com.aiworkbench.backend.domain.RequirementRepository;
import com.aiworkbench.backend.release.ReleaseRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private static final List<String> TRACKED_STATUSES = List.of(
        "EXTRACT", "POOL", "ITERATION", "CONFIRMATION", "REVIEW", "PRD_CONFIRMED", "DEVELOPMENT", "COMPLETED");

    private final RequirementRepository requirements;
    private final IterationRepository iterations;
    private final ReleaseRecordRepository releases;

    public DashboardService(RequirementRepository requirements, IterationRepository iterations,
                            ReleaseRecordRepository releases) {
        this.requirements = requirements;
        this.iterations = iterations;
        this.releases = releases;
    }

    public DashboardSummary summarize() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : TRACKED_STATUSES) {
            counts.put(status, requirements.countByStatus(status));
        }
        return new DashboardSummary(requirements.count(), counts, iterations.count(),
            iterations.countByStatus("LOCKED"), releases.countByReleaseStatusNot("SUCCESS"),
            requirements.findTop5ByOrderByIdDesc().stream().map(Views.Requirement::from).toList(),
            iterations.findAll(PageRequest.of(0, 5)).getContent().stream().map(Views.Iteration::from).toList(),
            releases.findAllByOrderByIdDesc(PageRequest.of(0, 5)).getContent().stream()
                .map(record -> new ReleaseSummary(record.id, record.iterationId, record.version,
                    record.releaseStatus, record.writebackStatus, record.notifyStatus)).toList());
    }

    public record DashboardSummary(long totalRequirements, Map<String, Long> statusCounts, long totalIterations,
                                   long lockedIterations, long pendingReleases,
                                   List<Views.Requirement> recentRequirements, List<Views.Iteration> iterations,
                                   List<ReleaseSummary> recentReleases) { }

    public record ReleaseSummary(Long releaseId, Long iterationId, String version, String releaseStatus,
                                 String writebackStatus, String notifyStatus) { }
}
