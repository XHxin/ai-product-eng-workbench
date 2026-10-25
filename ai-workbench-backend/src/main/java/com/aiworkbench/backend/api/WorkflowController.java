package com.aiworkbench.backend.api;

import com.aiworkbench.backend.service.WorkbenchService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/requirements/{requirementId}")
public class WorkflowController {
    private static final int MAX_PAGE_SIZE = 100;

    private final WorkbenchService workbenchService;

    public WorkflowController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @GetMapping
    public Views.Requirement get(@PathVariable Long requirementId) {
        return workbenchService.getRequirement(requirementId);
    }

    @PostMapping("/normalize")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public Views.Requirement normalize(@PathVariable Long requirementId,
                                       @Valid @RequestBody Commands.Normalize command,
                                       Principal principal) {
        return workbenchService.normalize(requirementId, command, principal.getName());
    }

    @PostMapping("/planning")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public Views.Requirement planning(@PathVariable Long requirementId,
                                      @Valid @RequestBody Commands.Planning command,
                                      Principal principal) {
        return workbenchService.plan(requirementId, command, principal.getName());
    }

    @PostMapping("/prd")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public org.springframework.http.ResponseEntity<Views.Record> prd(@PathVariable Long requirementId,
                                                                    @Valid @RequestBody Commands.Prd command,
                                                                    Principal principal) {
        return created(workbenchService.createPrd(requirementId, command, principal.getName()));
    }

    @PostMapping("/confirmations")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public org.springframework.http.ResponseEntity<Views.Record> confirmation(@PathVariable Long requirementId,
                                                                            @Valid @RequestBody Commands.Confirmation command,
                                                                            Principal principal) {
        return created(workbenchService.createConfirmation(requirementId, command, principal.getName()));
    }

    @GetMapping("/records")
    public Page<Views.Record> records(@PathVariable Long requirementId,
                                      @RequestParam String category,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return workbenchService.listRequirementRecords(requirementId, category,
            PageRequest.of(Math.max(0, page), normalizedSize));
    }

    @PostMapping("/ready-for-review")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public Views.Requirement ready(@PathVariable Long requirementId, Principal principal) {
        return workbenchService.readyForReview(requirementId, principal.getName());
    }

    @PostMapping("/reviews")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public Views.Record review(@PathVariable Long requirementId, @Valid @RequestBody Commands.Review command,
                               Principal principal) {
        return workbenchService.review(requirementId, command, principal.getName());
    }

    @PostMapping("/tasks")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public org.springframework.http.ResponseEntity<Views.Record> task(@PathVariable Long requirementId,
                                                                     @Valid @RequestBody Commands.Task command,
                                                                     Principal principal) {
        return created(workbenchService.createTask(requirementId, command, principal.getName()));
    }

    private org.springframework.http.ResponseEntity<Views.Record> created(Views.Record record) {
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(record);
    }
}
