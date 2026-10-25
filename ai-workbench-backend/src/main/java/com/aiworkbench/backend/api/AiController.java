package com.aiworkbench.backend.api;

import com.aiworkbench.backend.service.WorkbenchService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final WorkbenchService workbenchService;

    public AiController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @PostMapping("/requirements/{id}/extract")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public Views.Requirement extract(@PathVariable Long id, @Valid @RequestBody Commands.Normalize command,
                                     Principal principal) {
        return workbenchService.normalize(id, command, principal.getName());
    }

    @PostMapping("/requirements/{id}/prd")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public Views.Record manualPrd(@PathVariable Long id, @Valid @RequestBody Commands.Prd command,
                                  Principal principal) {
        return workbenchService.createPrd(id, command, principal.getName());
    }
}
