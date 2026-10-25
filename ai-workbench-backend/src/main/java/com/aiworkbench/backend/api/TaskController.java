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
@RequestMapping("/api")
public class TaskController {
    private static final int MAX_PAGE_SIZE = 100;

    private final WorkbenchService workbenchService;

    public TaskController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @GetMapping("/records")
    public Page<Views.Record> records(@RequestParam String category,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        int normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return workbenchService.listRecords(category, PageRequest.of(Math.max(0, page), normalizedSize));
    }

    @PostMapping("/confirmations/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public Views.Record conclude(@PathVariable Long id, @Valid @RequestBody Commands.Conclusion command,
                                 Principal principal) {
        return workbenchService.conclude(id, command, principal.getName());
    }

    @PostMapping("/tasks/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public Views.Record confirm(@PathVariable Long id, Principal principal) {
        return workbenchService.confirmTask(id, principal.getName());
    }

    @PostMapping("/tasks/{id}/development")
    @PreAuthorize("hasAnyRole('ADMIN','DEV')")
    public Views.Record development(@PathVariable Long id, @Valid @RequestBody Commands.Development command,
                                    Principal principal) {
        return workbenchService.developTask(id, command, principal.getName());
    }

    @PostMapping("/tasks/{id}/progress")
    @PreAuthorize("hasAnyRole('ADMIN','DEV')")
    public Views.Record progress(@PathVariable Long id, @Valid @RequestBody Commands.TaskProgress command,
                                 Principal principal) {
        return workbenchService.progressTask(id, command, principal.getName());
    }
}
