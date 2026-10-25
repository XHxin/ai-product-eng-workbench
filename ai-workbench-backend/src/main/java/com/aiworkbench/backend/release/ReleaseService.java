package com.aiworkbench.backend.release;

import com.aiworkbench.backend.api.Commands;
import com.aiworkbench.backend.audit.AuditLogService;
import com.aiworkbench.backend.domain.IterationEntity;
import com.aiworkbench.backend.domain.IterationRepository;
import com.aiworkbench.backend.domain.RequirementEntity;
import com.aiworkbench.backend.domain.RequirementRepository;
import com.aiworkbench.backend.service.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ReleaseService {
    private static final String REMINDER_SENT = "SENT";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String SUCCESS = "SUCCESS";
    private static final String MANUAL_REQUIRED = "MANUAL_REQUIRED";
    private static final String FAILED = "FAILED";

    private final IterationRepository iterations;
    private final ReleaseRecordRepository records;
    private final RequirementRepository requirements;
    private final AuditLogService auditLogService;

    public ReleaseService(IterationRepository iterations, ReleaseRecordRepository records,
                          RequirementRepository requirements, AuditLogService auditLogService) {
        this.iterations = iterations;
        this.records = records;
        this.requirements = requirements;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ReleaseRecordEntity remind(Long iterationId, String version, String notes, String actor) {
        requireIteration(iterationId);
        String normalizedVersion = normalizeVersion(version);
        ReleaseRecordEntity record = records.findByIterationIdAndVersion(iterationId, normalizedVersion)
            .orElseGet(ReleaseRecordEntity::new);
        if (SUCCESS.equals(record.releaseStatus)) {
            throw BusinessException.conflict("该迭代版本已完成发布");
        }
        record.iterationId = iterationId;
        record.version = normalizedVersion;
        record.notes = notes;
        record.reminderStatus = REMINDER_SENT;
        record.updatedAt = Instant.now();
        ReleaseRecordEntity saved = records.save(record);
        audit("RELEASE_REMINDER_SENT", actor, iterationId, "releaseId=" + saved.id + ",version=" + normalizedVersion);
        return saved;
    }

    @Transactional
    public ReleaseRecordEntity confirm(Long iterationId, Commands.Release command, String actor) {
        requireIteration(iterationId);
        ReleaseRecordEntity record = records.findByIterationIdAndVersion(iterationId, normalizeVersion(command.version()))
            .orElseThrow(() -> BusinessException.missing("发布记录"));
        if (!REMINDER_SENT.equals(record.reminderStatus)) {
            throw BusinessException.conflict("发布提醒尚未发送");
        }
        if (SUCCESS.equals(record.releaseStatus)) {
            throw BusinessException.conflict("发布已确认，请勿重复提交");
        }
        record.confirmationStatus = CONFIRMED;
        record.confirmedBy = actor;
        record.releaseStatus = SUCCESS;
        record.writebackStatus = MANUAL_REQUIRED;
        record.notifyStatus = MANUAL_REQUIRED;
        record.notes = command.notes();
        record.updatedAt = Instant.now();
        ReleaseRecordEntity saved = records.save(record);
        completeRequirements(iterationId);
        audit("RELEASE_CONFIRMED", actor, iterationId, "releaseId=" + saved.id + ",version=" + saved.version);
        return saved;
    }

    private void completeRequirements(Long iterationId) {
        for (RequirementEntity requirement : requirements.findByIterationId(iterationId)) {
            if ("DEVELOPMENT".equals(requirement.status) || "PRD_CONFIRMED".equals(requirement.status)) {
                requirement.status = "COMPLETED";
                requirements.save(requirement);
            }
        }
    }

    @Transactional
    public ReleaseRecordEntity recordManualResult(Long recordId, String writebackStatus, String notifyStatus,
                                                  String evidence, String actor) {
        ReleaseRecordEntity record = records.findById(recordId)
            .orElseThrow(() -> BusinessException.missing("发布记录"));
        if (!SUCCESS.equals(record.releaseStatus)) {
            throw BusinessException.conflict("发布尚未确认，不能登记回写结果");
        }
        record.writebackStatus = normalizeStatus(writebackStatus, "回写");
        record.notifyStatus = normalizeStatus(notifyStatus, "通知");
        record.lastError = evidence;
        record.updatedAt = Instant.now();
        ReleaseRecordEntity saved = records.save(record);
        audit("RELEASE_MANUAL_RESULT_RECORDED", actor, record.iterationId, "releaseId=" + recordId);
        return saved;
    }

    @Transactional
    public ReleaseRecordEntity retry(Long recordId, String actor) {
        ReleaseRecordEntity record = records.findById(recordId)
            .orElseThrow(() -> BusinessException.missing("发布记录"));
        if (!FAILED.equals(record.writebackStatus) && !FAILED.equals(record.notifyStatus)) {
            throw BusinessException.conflict("仅失败的回写或通知需要重试");
        }
        if (FAILED.equals(record.writebackStatus)) {
            record.writebackStatus = MANUAL_REQUIRED;
        }
        if (FAILED.equals(record.notifyStatus)) {
            record.notifyStatus = MANUAL_REQUIRED;
        }
        record.updatedAt = Instant.now();
        ReleaseRecordEntity saved = records.save(record);
        audit("RELEASE_RETRY_REQUESTED", actor, record.iterationId, "releaseId=" + recordId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ReleaseRecordEntity> list(Pageable pageable) {
        return records.findAllByOrderByIdDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<ReleaseRecordEntity> listByIteration(Long iterationId) {
        requireIteration(iterationId);
        return records.findByIterationIdOrderByIdDesc(iterationId);
    }

    private IterationEntity requireIteration(Long iterationId) {
        return iterations.findById(iterationId)
            .orElseThrow(() -> BusinessException.missing("迭代"));
    }

    private String normalizeVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "发布版本不能为空", HttpStatus.BAD_REQUEST);
        }
        return version.trim();
    }

    private String normalizeStatus(String status, String label) {
        if ("DONE".equals(status) || "MANUAL_REQUIRED".equals(status) || "FAILED".equals(status)) {
            return status;
        }
        throw new BusinessException("VALIDATION_ERROR", label + "状态不合法", HttpStatus.BAD_REQUEST);
    }

    private void audit(String action, String actor, Long iterationId, String details) {
        auditLogService.record(new AuditLogService.AuditEntry(action, actor, "ITERATION",
            "iterationId=" + iterationId + "," + details));
    }
}
