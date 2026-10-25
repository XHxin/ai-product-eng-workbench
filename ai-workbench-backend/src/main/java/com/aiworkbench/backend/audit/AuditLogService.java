package com.aiworkbench.backend.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    private static final int MAX_PAGE_SIZE = 100;
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<AuditView> search(int page, int size) {
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return repository.findAll(PageRequest.of(Math.max(0, page), normalizedSize))
            .map(AuditView::from);
    }

    @Transactional
    public void record(AuditEntry entry) {
        AuditLogEntity audit = new AuditLogEntity();
        audit.action = entry.action();
        audit.actor = entry.actor();
        audit.resource = entry.resource();
        audit.details = entry.details();
        repository.save(audit);
    }

    public record AuditEntry(String action, String actor, String resource, String details) {}

    public record AuditView(Long id, String action, String actor, String resource,
                            String details, java.time.Instant createdAt) {
        public static AuditView from(AuditLogEntity entity) {
            return new AuditView(entity.id, entity.action, entity.actor, entity.resource,
                entity.details, entity.createdAt);
        }
    }
}
