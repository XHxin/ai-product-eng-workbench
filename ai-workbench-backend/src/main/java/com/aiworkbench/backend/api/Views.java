package com.aiworkbench.backend.api;

import com.aiworkbench.backend.domain.IterationEntity;
import com.aiworkbench.backend.domain.RequirementEntity;
import com.aiworkbench.backend.domain.WorkflowRecord;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public final class Views {
    private Views() {}
    public record Requirement(Long id, String requirementNumber, String title, String rawText,
        String sourceType, String sourceRef, String normalizedText, String requirementType,
        String priority, String status, Long iterationId, Instant createdAt) {
        public static Requirement from(RequirementEntity entity) {
            return new Requirement(entity.id,"REQ-"+entity.id,entity.title,entity.rawText,entity.sourceType,
                entity.sourceRef,entity.normalizedText,entity.requirementType,entity.priority,
                entity.status,entity.iterationId,entity.createdAt);
        }
    }
    public record Iteration(Long id,String name,String startDate,String endDate,String status) {
        public static Iteration from(IterationEntity entity) {
            return new Iteration(entity.id,entity.name,entity.startDate,entity.endDate,entity.status);
        }
    }
    public record Record(Long id,String category,Long requirementId,Long iterationId,Long parentId,
        int versionNumber,String status,JsonNode payload,String actor,Instant createdAt) {}
    public record Integration(Long id,String provider,String endpoint,String model,String secretStatus) {}
    public record User(String username, java.util.List<String> roles) {}
}
