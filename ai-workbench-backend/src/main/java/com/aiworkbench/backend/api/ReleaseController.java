package com.aiworkbench.backend.api;

import com.aiworkbench.backend.release.ReleaseRecordEntity;
import com.aiworkbench.backend.release.ReleaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ReleaseController {
    private static final int MAX_PAGE_SIZE = 100;

    private final ReleaseService releaseService;

    public ReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping("/releases")
    public Page<ReleaseView> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return releaseService.list(PageRequest.of(Math.max(0, page), normalizedSize)).map(ReleaseView::from);
    }

    @GetMapping("/iterations/{iterationId}/releases")
    public List<ReleaseView> listByIteration(@PathVariable Long iterationId) {
        return releaseService.listByIteration(iterationId).stream().map(ReleaseView::from).toList();
    }

    @PostMapping("/iterations/{iterationId}/release-reminder")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER')")
    public ReleaseView remind(@PathVariable Long iterationId, @RequestBody(required = false) ReminderRequest request,
                              Principal principal) {
        ReminderRequest reminder = request == null ? new ReminderRequest("UNSPECIFIED", "") : request;
        return ReleaseView.from(releaseService.remind(iterationId, reminder.version(), reminder.notes(),
            principal.getName()));
    }

    @PostMapping("/iterations/{iterationId}/release-confirm")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER')")
    public ReleaseView confirm(@PathVariable Long iterationId, @Valid @RequestBody Commands.Release command,
                               Principal principal) {
        return ReleaseView.from(releaseService.confirm(iterationId, command, principal.getName()));
    }

    @PostMapping("/releases/{recordId}/manual-result")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER')")
    public ReleaseView manualResult(@PathVariable Long recordId, @Valid @RequestBody ManualResultRequest request,
                                    Principal principal) {
        return ReleaseView.from(releaseService.recordManualResult(recordId, request.writebackStatus(),
            request.notifyStatus(), request.evidence(), principal.getName()));
    }

    @PostMapping("/releases/{recordId}/retry")
    @PreAuthorize("hasAnyRole('ADMIN','RELEASE_MANAGER')")
    public ReleaseView retry(@PathVariable Long recordId, Principal principal) {
        return ReleaseView.from(releaseService.retry(recordId, principal.getName()));
    }

    public record ReminderRequest(@NotBlank @Size(max = 100) String version,
                                  @Size(max = 10000) String notes) { }

    public record ManualResultRequest(@NotBlank String writebackStatus, @NotBlank String notifyStatus,
                                      @Size(max = 1000) String evidence) { }

    public record ReleaseView(Long releaseId, Long iterationId, String version, String reminderStatus,
                              String confirmationStatus, String releaseStatus, String writebackStatus,
                              String notifyStatus, String notes, String confirmedBy, String lastError,
                              java.time.Instant updatedAt) {
        static ReleaseView from(ReleaseRecordEntity record) {
            return new ReleaseView(record.id, record.iterationId, record.version, record.reminderStatus,
                record.confirmationStatus, record.releaseStatus, record.writebackStatus, record.notifyStatus,
                record.notes, record.confirmedBy, record.lastError, record.updatedAt);
        }
    }
}
