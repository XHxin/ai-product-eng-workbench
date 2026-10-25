package com.aiworkbench.backend.service;

import com.aiworkbench.backend.api.Commands;
import com.aiworkbench.backend.audit.AuditLogService;
import com.aiworkbench.backend.domain.IterationEntity;
import com.aiworkbench.backend.domain.IterationRepository;
import com.aiworkbench.backend.domain.RequirementEntity;
import com.aiworkbench.backend.domain.RequirementRepository;
import com.aiworkbench.backend.domain.WorkflowRecord;
import com.aiworkbench.backend.domain.WorkflowRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class WorkflowService {
    public static final String TEMPLATE_VERSION = "prd-mvp-1";
    private static final String CATEGORY_PRD = "PRD";
    private static final String CATEGORY_CONFIRMATION = "CONFIRMATION";
    private static final String CATEGORY_TASK = "TASK";

    private final WorkflowRepository records;
    private final RequirementRepository requirements;
    private final IterationRepository iterations;
    private final AuditLogService auditLogService;
    private final ObjectMapper json;

    public WorkflowService(WorkflowRepository records, RequirementRepository requirements,
                           IterationRepository iterations, AuditLogService auditLogService, ObjectMapper json) {
        this.records = records;
        this.requirements = requirements;
        this.iterations = iterations;
        this.auditLogService = auditLogService;
        this.json = json;
    }

    public RequirementEntity updateNormalized(Long requirementId, Commands.Normalize command, String actor) {
        RequirementEntity requirement = require(requirementId);
        assertState(requirement, List.of("EXTRACT", "POOL"));
        requirement.title = command.title();
        requirement.normalizedText = command.normalizedText();
        requirement.requirementType = command.requirementType().name();
        requirement.priority = command.priority().name();
        requirement.status = "POOL";
        RequirementEntity saved = requirements.save(requirement);
        audit("REQUIREMENT_NORMALIZED", actor, requirementId,
            "type=" + saved.requirementType + ",priority=" + saved.priority + ",rationale=" + command.rationale());
        return saved;
    }

    public RequirementEntity plan(Long requirementId, Commands.Planning command, String actor) {
        RequirementEntity requirement = require(requirementId);
        IterationEntity iteration = iterations.findById(command.iterationId())
            .orElseThrow(() -> BusinessException.missing("迭代"));
        if (!"POOL".equals(requirement.status)) {
            throw BusinessException.conflict("需求必须先确认入池");
        }
        if (!"PLANNING".equals(iteration.status)) {
            throw BusinessException.conflict("迭代范围已锁定");
        }
        requirement.iterationId = iteration.id;
        requirement.status = "ITERATION";
        RequirementEntity saved = requirements.save(requirement);
        audit("REQUIREMENT_PLANNED", actor, requirementId, "iterationId=" + iteration.id);
        return saved;
    }

    public WorkflowRecord prd(Long requirementId, Commands.Prd command, String actor) {
        RequirementEntity requirement = require(requirementId);
        if (requirement.iterationId == null) {
            throw BusinessException.conflict("需求尚未纳入迭代");
        }
        IterationEntity iteration = iterations.findById(requirement.iterationId)
            .orElseThrow(() -> BusinessException.missing("迭代"));
        if (!"LOCKED".equals(iteration.status)) {
            throw BusinessException.conflict("必须先锁定迭代");
        }
        assertState(requirement, List.of("ITERATION", "CONFIRMATION", "REVIEW"));
        int versionNumber = records.findByRequirementIdAndCategoryOrderByIdDesc(requirementId, CATEGORY_PRD).size() + 1;
        WorkflowRecord record = save(new RecordInput(CATEGORY_PRD, requirementId, requirement.iterationId, "DRAFT",
            new PrdPayload(command.content(), command.htmlContent(), TEMPLATE_VERSION, requirement.normalizedText), actor));
        record.versionNumber = versionNumber;
        records.save(record);
        requirement.status = "CONFIRMATION";
        audit("PRD_CREATED", actor, requirementId, "prdId=" + record.id + ",version=" + versionNumber);
        return record;
    }

    public WorkflowRecord confirmation(Long requirementId, Commands.Confirmation command, String actor) {
        RequirementEntity requirement = require(requirementId);
        assertState(requirement, List.of("CONFIRMATION"));
        WorkflowRecord record = save(new RecordInput(CATEGORY_CONFIRMATION, requirementId, requirement.iterationId,
            "OPEN", command, actor));
        audit("CONFIRMATION_OPENED", actor, requirementId, "confirmationId=" + record.id + ",channel=" + command.channel());
        return record;
    }

    public WorkflowRecord conclude(Long confirmationId, Commands.Conclusion command, String actor) {
        WorkflowRecord record = record(confirmationId, CATEGORY_CONFIRMATION);
        if (!"OPEN".equals(record.status)) {
            throw BusinessException.conflict("确认问题已经处理");
        }
        record.status = "CONFIRMED";
        records.save(record);
        save(new RecordInput("CONCLUSION", record.requirementId, record.iterationId, "CONFIRMED", command, actor));
        audit("CONFIRMATION_CONCLUDED", actor, record.requirementId, "confirmationId=" + confirmationId);
        return record;
    }

    public RequirementEntity ready(Long requirementId, String actor) {
        RequirementEntity requirement = require(requirementId);
        assertState(requirement, List.of("CONFIRMATION"));
        boolean hasOpenQuestion = records.findByRequirementIdAndCategoryOrderByIdDesc(requirementId, CATEGORY_CONFIRMATION)
            .stream().anyMatch(record -> !"CONFIRMED".equals(record.status));
        if (hasOpenQuestion) {
            throw BusinessException.conflict("仍有未确认问题");
        }
        requirement.status = "REVIEW";
        RequirementEntity saved = requirements.save(requirement);
        audit("REQUIREMENT_READY_FOR_REVIEW", actor, requirementId, "status=REVIEW");
        return saved;
    }

    public WorkflowRecord review(Long requirementId, Commands.Review command, String actor) {
        RequirementEntity requirement = require(requirementId);
        assertState(requirement, List.of("REVIEW"));
        WorkflowRecord prd = record(command.prdId(), CATEGORY_PRD);
        if (!requirementId.equals(prd.requirementId)) {
            throw BusinessException.conflict("PRD不属于需求");
        }
        WorkflowRecord review = save(new RecordInput("REVIEW", requirementId, requirement.iterationId,
            command.decision().name(), command, actor));
        boolean approved = command.decision() == Commands.ReviewDecision.APPROVED;
        requirement.status = approved ? "PRD_CONFIRMED" : "CONFIRMATION";
        prd.status = approved ? "FINAL" : "REJECTED";
        requirements.save(requirement);
        records.save(prd);
        audit("REVIEW_REGISTERED", actor, requirementId,
            "reviewId=" + review.id + ",decision=" + command.decision().name());
        return review;
    }

    public WorkflowRecord task(Long requirementId, Commands.Task command, String actor) {
        RequirementEntity requirement = require(requirementId);
        assertState(requirement, List.of("PRD_CONFIRMED", "DEVELOPMENT"));
        WorkflowRecord finalPrd = records.findByRequirementIdAndCategoryOrderByIdDesc(requirementId, CATEGORY_PRD)
            .stream().filter(record -> "FINAL".equals(record.status)).findFirst()
            .orElseThrow(() -> BusinessException.conflict("缺少最终PRD"));
        WorkflowRecord task = save(new RecordInput(CATEGORY_TASK, requirementId, requirement.iterationId,
            "DRAFT", command, actor));
        task.parentId = finalPrd.id;
        records.save(task);
        audit("TASK_CREATED", actor, requirementId, "taskId=" + task.id);
        return task;
    }

    public WorkflowRecord confirmTask(Long taskId, String actor) {
        WorkflowRecord task = record(taskId, CATEGORY_TASK);
        if (!"DRAFT".equals(task.status)) {
            throw BusinessException.conflict("任务不处于待确认状态");
        }
        task.status = "CONFIRMED";
        records.save(task);
        RequirementEntity requirement = require(task.requirementId);
        requirement.status = "DEVELOPMENT";
        requirements.save(requirement);
        audit("TASK_CONFIRMED", actor, task.requirementId, "taskId=" + taskId);
        return task;
    }

    public WorkflowRecord development(Long taskId, Commands.Development command, String actor) {
        WorkflowRecord task = record(taskId, CATEGORY_TASK);
        if (!List.of("CONFIRMED", "IN_PROGRESS").contains(task.status)) {
            throw BusinessException.conflict("任务未确认或已完成");
        }
        WorkflowRecord detail = save(new RecordInput("DEVELOPMENT", task.requirementId, task.iterationId,
            "CONFIRMED", command, actor));
        detail.parentId = task.id;
        records.save(detail);
        task.status = "IN_PROGRESS";
        records.save(task);
        audit("DEVELOPMENT_UPDATED", actor, task.requirementId, "taskId=" + taskId);
        return detail;
    }

    public WorkflowRecord progress(Long taskId, Commands.TaskProgress command, String actor) {
        WorkflowRecord task = record(taskId, CATEGORY_TASK);
        if (!"IN_PROGRESS".equals(task.status)) {
            throw BusinessException.conflict("请先确认工时和技术方案");
        }
        task.status = command.status().name();
        records.save(task);
        audit("TASK_PROGRESS_UPDATED", actor, task.requirementId, "taskId=" + taskId + ",status=" + task.status);
        if ("DONE".equals(task.status)) {
            markIterationReadyForRelease(task.iterationId, actor);
        }
        return task;
    }

    private void markIterationReadyForRelease(Long iterationId, String actor) {
        if (iterationId == null) {
            return;
        }
        IterationEntity iteration = iterations.findById(iterationId).orElse(null);
        if (iteration == null || !"LOCKED".equals(iteration.status)) {
            return;
        }
        List<RequirementEntity> iterationRequirements = requirements.findByIterationId(iterationId);
        if (iterationRequirements.isEmpty() || !iterationRequirements.stream().allMatch(this::allTasksDone)) {
            return;
        }
        iteration.status = "READY_FOR_RELEASE";
        iterations.save(iteration);
        auditLogService.record(new AuditLogService.AuditEntry("ITERATION_READY_FOR_RELEASE", actor, "ITERATION",
            "iterationId=" + iterationId));
    }

    private boolean allTasksDone(RequirementEntity requirement) {
        List<WorkflowRecord> tasks = records.findByRequirementIdAndCategoryOrderByIdDesc(requirement.id, CATEGORY_TASK);
        return !tasks.isEmpty() && tasks.stream().allMatch(task -> "DONE".equals(task.status));
    }

    public RequirementEntity require(Long id) {
        return requirements.findById(id).orElseThrow(() -> BusinessException.missing("需求"));
    }

    public WorkflowRecord record(Long id, String category) {
        return records.findById(id)
            .filter(record -> category.equals(record.category))
            .orElseThrow(() -> BusinessException.missing(category));
    }

    @Transactional(readOnly = true)
    public Page<WorkflowRecord> listRecords(String category, Pageable pageable) {
        return records.findByCategoryOrderByIdDesc(category, pageable);
    }

    @Transactional(readOnly = true)
    public Page<WorkflowRecord> recordsPage(Long requirementId, String category, Pageable pageable) {
        return records.findByRequirementIdAndCategoryOrderByIdDesc(requirementId, category, pageable);
    }

    public WorkflowRecord save(RecordInput input) {
        WorkflowRecord record = new WorkflowRecord();
        record.category = input.category();
        record.requirementId = input.requirementId();
        record.iterationId = input.iterationId();
        record.status = input.status();
        record.actor = input.actor();
        record.payload = serialize(input.payload());
        return records.save(record);
    }

    public String serialize(Object payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("SERIALIZATION_FAILED", "无法保存业务记录", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void audit(String action, String actor, Long requirementId, String details) {
        auditLogService.record(new AuditLogService.AuditEntry(action, actor, "REQUIREMENT",
            "requirementId=" + requirementId + "," + details));
    }

    private void assertState(RequirementEntity requirement, List<String> allowed) {
        if (!allowed.contains(requirement.status)) {
            throw BusinessException.conflict("需求当前状态不允许此操作：" + requirement.status);
        }
    }

    public record RecordInput(String category, Long requirementId, Long iterationId, String status,
                              Object payload, String actor) { }

    public record PrdPayload(String content, String htmlContent, String templateVersion, String inputSnapshot) { }
}
