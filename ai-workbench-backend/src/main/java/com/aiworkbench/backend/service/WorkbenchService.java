package com.aiworkbench.backend.service;

import com.aiworkbench.backend.api.Commands;
import com.aiworkbench.backend.api.Views;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WorkbenchService {
    Views.Requirement getRequirement(Long requirementId);

    Views.Requirement normalize(Long requirementId, Commands.Normalize command, String actor);

    Views.Requirement plan(Long requirementId, Commands.Planning command, String actor);

    Views.Record createPrd(Long requirementId, Commands.Prd command, String actor);

    Views.Record createConfirmation(Long requirementId, Commands.Confirmation command, String actor);

    Views.Record conclude(Long confirmationId, Commands.Conclusion command, String actor);

    Views.Requirement readyForReview(Long requirementId, String actor);

    Views.Record review(Long requirementId, Commands.Review command, String actor);

    Views.Record createTask(Long requirementId, Commands.Task command, String actor);

    Views.Record confirmTask(Long taskId, String actor);

    Views.Record developTask(Long taskId, Commands.Development command, String actor);

    Views.Record progressTask(Long taskId, Commands.TaskProgress command, String actor);

    Page<Views.Record> listRecords(String category, Pageable pageable);

    Page<Views.Record> listRequirementRecords(Long requirementId, String category, Pageable pageable);
}
