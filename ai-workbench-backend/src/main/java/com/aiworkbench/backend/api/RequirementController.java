package com.aiworkbench.backend.api;

import com.aiworkbench.backend.service.RequirementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/requirements")
public class RequirementController {
    private static final int MAX_PAGE_SIZE = 100;

    private final RequirementService requirementService;

    public RequirementController(RequirementService requirementService) {
        this.requirementService = requirementService;
    }

    @GetMapping
    public Page<Views.Requirement> list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return requirementService.list(PageRequest.of(Math.max(0, page), normalizedSize));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public ResponseEntity<Views.Requirement> create(@Valid @RequestBody CreateRequirementRequest request,
                                                    Principal principal) {
        return ResponseEntity.status(201)
            .body(Views.Requirement.from(requirementService.create(request, principal.getName())));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public ResponseEntity<Views.Requirement> importRequirement(@Valid @RequestBody CreateRequirementRequest request,
                                                               Principal principal) {
        return ResponseEntity.status(201)
            .body(Views.Requirement.from(requirementService.create(request, principal.getName())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Principal principal) {
        requirementService.delete(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    public record CreateRequirementRequest(@NotBlank @Size(max = 200) String title,
                                           @NotBlank @Size(max = 20000) String rawText,
                                           @NotBlank @Size(max = 40) String sourceType,
                                           @NotBlank @Size(max = 1000) String sourceRef,
                                           @NotBlank @Size(max = 200) String idempotencyKey,
                                           @Size(max = 20000) String normalizedText,
                                           Commands.RequirementType requirementType,
                                           Commands.Priority priority) { }
}
