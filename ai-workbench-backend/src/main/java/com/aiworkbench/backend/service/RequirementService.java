package com.aiworkbench.backend.service;

import com.aiworkbench.backend.api.RequirementController.CreateRequirementRequest;
import com.aiworkbench.backend.api.Views;
import com.aiworkbench.backend.audit.AuditLogService;
import com.aiworkbench.backend.domain.RequirementEntity;
import com.aiworkbench.backend.domain.RequirementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RequirementService {
    private final RequirementRepository repository;
    private final AuditLogService auditLogService;

    public RequirementService(RequirementRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public RequirementEntity create(CreateRequirementRequest request, String actor) {
        RequirementEntity requirement = repository.findByIdempotencyKey(request.idempotencyKey())
            .orElseGet(() -> repository.save(newRequirement(request)));
        auditLogService.record(new AuditLogService.AuditEntry("REQUIREMENT_CREATED", actor,
            "REQUIREMENT", "requirementId=" + requirement.id + ",sourceType=" + requirement.sourceType));
        return requirement;
    }

    @Transactional(readOnly = true)
    public Page<Views.Requirement> list(Pageable pageable) {
        return repository.findAll(pageable).map(Views.Requirement::from);
    }

    @Transactional
    public void delete(Long requirementId, String actor) {
        RequirementEntity requirement = require(requirementId);
        boolean deletable = List.of("EXTRACT", "POOL").contains(requirement.status) && requirement.iterationId == null;
        if (!deletable) {
            throw BusinessException.conflict("仅需求池中未纳入迭代的需求可以删除");
        }
        repository.delete(requirement);
        auditLogService.record(new AuditLogService.AuditEntry("REQUIREMENT_DELETED", actor,
            "REQUIREMENT", "requirementId=" + requirementId + ",status=" + requirement.status));
    }

    @Transactional
    public RequirementEntity require(Long requirementId) {
        return repository.findById(requirementId)
            .orElseThrow(() -> BusinessException.missing("需求"));
    }

    private RequirementEntity newRequirement(CreateRequirementRequest request) {
        RequirementEntity requirement = new RequirementEntity();
        requirement.title = request.title();
        requirement.rawText = request.rawText();
        requirement.sourceType = request.sourceType();
        requirement.sourceRef = request.sourceRef();
        requirement.idempotencyKey = request.idempotencyKey();
        applyStructuredFields(requirement, request);
        return requirement;
    }

    private void applyStructuredFields(RequirementEntity requirement, CreateRequirementRequest request) {
        if (request.normalizedText() != null) {
            requirement.normalizedText = request.normalizedText();
        }
        if (request.requirementType() != null) {
            requirement.requirementType = request.requirementType().name();
        }
        if (request.priority() != null) {
            requirement.priority = request.priority().name();
        }
    }
}
