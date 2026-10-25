package com.aiworkbench.backend.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class Commands {
    private Commands() {}
    public enum RequirementType { FEATURE, IMPROVEMENT, BUG }
    public enum Priority { P0, P1, P2, P3 }
    public enum ReviewDecision { APPROVED, REJECTED }
    public enum TaskState { IN_PROGRESS, DONE }
    public record Normalize(@NotBlank @Size(max=200) String title,
        @NotBlank @Size(max=20000) String normalizedText, @NotNull RequirementType requirementType,
        @NotNull Priority priority, @Size(max=2000) String rationale) {}
    public record Iteration(@NotBlank @Size(max=200) String name, @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {}
    public record Planning(Long iterationId) {}
    public record Prd(@NotBlank @Size(max=100000) String content,
        @NotBlank @Size(max=100000) String htmlContent) {}
    public record Confirmation(@NotBlank @Size(max=2000) String question,
        @NotBlank @Size(max=40) String channel, @NotBlank @Size(max=1000) String sourceRef,
        @NotBlank @Size(max=10000) String rawReply) {}
    public record Conclusion(@NotBlank @Size(max=10000) String conclusion) {}
    public record Review(@NotNull Long prdId, @NotNull ReviewDecision decision,
        @NotBlank @Size(max=1000) String participants, @NotBlank String meetingAt,
        @NotBlank @Size(max=10000) String conclusion) {}
    public record Task(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=10000) String scope,
        @NotBlank @Size(max=10000) String acceptance, @Size(max=2000) String dependencies) {}
    public record Development(@NotNull @DecimalMin("0.1") @DecimalMax("10000") BigDecimal confirmedHours,
        @NotBlank @Size(max=10000) String technicalPlan,
        @NotEmpty @Size(max=100) List<@NotBlank @Size(max=1000) String> subtasks,
        @NotEmpty @Size(max=100) List<@NotBlank @Size(max=1000) String> testPoints) {}
    public record TaskProgress(@NotNull TaskState status) {}
    public record Release(@NotBlank @Size(max=100) String version,
        @NotBlank @Size(max=10000) String notes) {}
    public record Evidence(@NotBlank @Size(max=10000) String evidence) {}
    public record Integration(@Size(max=1000) String endpoint, @Size(max=5000) String secret,
        @Size(max=200) String model) {}
}
