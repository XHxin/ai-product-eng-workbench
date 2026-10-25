package com.aiworkbench.backend.service;

import com.aiworkbench.backend.api.Commands;
import com.aiworkbench.backend.api.Views;
import com.aiworkbench.backend.audit.AuditLogService;
import com.aiworkbench.backend.domain.IterationEntity;
import com.aiworkbench.backend.domain.IterationRepository;
import com.aiworkbench.backend.domain.RequirementEntity;
import com.aiworkbench.backend.domain.RequirementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IterationService {
    private final IterationRepository iterationRepository;
    private final RequirementRepository requirementRepository;
    private final AuditLogService auditLogService;

    public IterationService(IterationRepository iterationRepository, RequirementRepository requirementRepository,
                            AuditLogService auditLogService) {
        this.iterationRepository = iterationRepository;
        this.requirementRepository = requirementRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public IterationEntity create(Commands.Iteration command, String actor) {
        if (command.endDate().isBefore(command.startDate())) {
            throw BusinessException.conflict("迭代结束日期不能早于开始日期");
        }
        IterationEntity entity = new IterationEntity();
        entity.name = command.name();
        entity.startDate = command.startDate().toString();
        entity.endDate = command.endDate().toString();
        IterationEntity saved = iterationRepository.save(entity);
        auditLogService.record(new AuditLogService.AuditEntry("ITERATION_CREATED", actor, "ITERATION",
            "iterationId=" + saved.id));
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Views.Iteration> list(Pageable pageable) {
        return iterationRepository.findAll(pageable).map(Views.Iteration::from);
    }

    @Transactional(readOnly = true)
    public Views.Iteration get(Long iterationId) {
        return Views.Iteration.from(require(iterationId));
    }

    @Transactional(readOnly = true)
    public List<Views.Requirement> listRequirements(Long iterationId) {
        require(iterationId);
        return requirementRepository.findByIterationId(iterationId).stream()
            .map(Views.Requirement::from)
            .toList();
    }

    @Transactional
    public IterationEntity lock(Long id, String actor) {
        IterationEntity entity = iterationRepository.lockById(id)
            .orElseThrow(() -> BusinessException.missing("迭代"));
        if (!"PLANNING".equals(entity.status)) {
            throw BusinessException.conflict("迭代当前状态不能锁定");
        }
        entity.status = "LOCKED";
        IterationEntity saved = iterationRepository.save(entity);
        auditLogService.record(new AuditLogService.AuditEntry("ITERATION_LOCKED", actor, "ITERATION",
            "iterationId=" + id));
        return saved;
    }

    @Transactional
    public RequirementEntity assign(Long iterationId, Long requirementId, String actor) {
        IterationEntity iteration = require(iterationId);
        RequirementEntity requirement = requirementRepository.findById(requirementId)
            .orElseThrow(() -> BusinessException.missing("需求"));
        if (!"POOL".equals(requirement.status) && !"EXTRACT".equals(requirement.status)) {
            throw BusinessException.conflict("当前需求不能纳入迭代");
        }
        if (!"PLANNING".equals(iteration.status)) {
            throw BusinessException.conflict("迭代范围已锁定");
        }
        requirement.iterationId = iteration.id;
        requirement.status = "ITERATION";
        RequirementEntity saved = requirementRepository.save(requirement);
        auditLogService.record(new AuditLogService.AuditEntry("REQUIREMENT_ASSIGNED", actor, "ITERATION",
            "iterationId=" + iterationId + ",requirementId=" + requirementId));
        return saved;
    }

    @Transactional
    public RequirementEntity remove(Long iterationId, Long requirementId, String actor) {
        IterationEntity iteration = require(iterationId);
        RequirementEntity requirement = requirementRepository.findById(requirementId)
            .orElseThrow(() -> BusinessException.missing("需求"));
        if (!iterationId.equals(requirement.iterationId)) {
            throw BusinessException.conflict("需求不属于该迭代");
        }
        if (!"PLANNING".equals(iteration.status)) {
            throw BusinessException.conflict("迭代范围已锁定，不能移除需求");
        }
        if (!"ITERATION".equals(requirement.status)) {
            throw BusinessException.conflict("当前需求状态不能移出迭代");
        }
        requirement.iterationId = null;
        requirement.status = "POOL";
        RequirementEntity saved = requirementRepository.save(requirement);
        auditLogService.record(new AuditLogService.AuditEntry("REQUIREMENT_REMOVED_FROM_ITERATION", actor,
            "ITERATION", "iterationId=" + iterationId + ",requirementId=" + requirementId));
        return saved;
    }

    private IterationEntity require(Long iterationId) {
        return iterationRepository.findById(iterationId)
            .orElseThrow(() -> BusinessException.missing("迭代"));
    }
}
