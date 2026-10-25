package com.aiworkbench.backend.api;

import com.aiworkbench.backend.service.IterationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
@RequestMapping("/api/iterations")
public class IterationController {
    private static final int MAX_PAGE_SIZE = 100;

    private final IterationService iterationService;

    public IterationController(IterationService iterationService) {
        this.iterationService = iterationService;
    }

    @GetMapping
    public Page<Views.Iteration> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return iterationService.list(PageRequest.of(Math.max(0, page), normalizedSize));
    }

    @GetMapping("/{id}")
    public Views.Iteration get(@PathVariable Long id) {
        return iterationService.get(id);
    }

    @GetMapping("/{id}/requirements")
    public java.util.List<Views.Requirement> requirements(@PathVariable Long id) {
        return iterationService.listRequirements(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public org.springframework.http.ResponseEntity<Views.Iteration> create(@Valid @RequestBody Commands.Iteration command,
                                                                           Principal principal) {
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
            .body(Views.Iteration.from(iterationService.create(command, principal.getName())));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public Views.Iteration lock(@PathVariable Long id, Principal principal) {
        return Views.Iteration.from(iterationService.lock(id, principal.getName()));
    }

    @PostMapping("/{id}/release")
    @PreAuthorize("hasAnyRole('ADMIN','PM','RELEASE_MANAGER')")
    public Views.Iteration release(@PathVariable Long id, Principal principal) {
        return Views.Iteration.from(iterationService.release(id, principal.getName()));
    }

    @PostMapping("/{id}/requirements")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public Views.Requirement assign(@PathVariable Long id, @Valid @RequestBody AssignmentRequest request,
                                    Principal principal) {
        return Views.Requirement.from(iterationService.assign(id, request.requirementId(), principal.getName()));
    }

    @DeleteMapping("/{id}/requirements/{requirementId}")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public org.springframework.http.ResponseEntity<Void> remove(@PathVariable Long id, @PathVariable Long requirementId,
                                                                Principal principal) {
        iterationService.remove(id, requirementId, principal.getName());
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    public record AssignmentRequest(@jakarta.validation.constraints.NotNull Long requirementId) { }
}
