package com.aiworkbench.backend.service;

import com.aiworkbench.backend.api.Commands;
import com.aiworkbench.backend.api.Views;
import com.aiworkbench.backend.domain.WorkflowRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class WorkbenchServiceImpl implements WorkbenchService {
    private final WorkflowService workflow;
    private final ObjectMapper json;

    public WorkbenchServiceImpl(WorkflowService workflow, ObjectMapper json) {
        this.workflow = workflow;
        this.json = json;
    }

    @Override
    public Views.Requirement getRequirement(Long requirementId) {
        return Views.Requirement.from(workflow.require(requirementId));
    }

    @Override
    public Views.Requirement normalize(Long requirementId, Commands.Normalize command, String actor) {
        return Views.Requirement.from(workflow.updateNormalized(requirementId, command, actor));
    }

    @Override
    public Views.Requirement plan(Long requirementId, Commands.Planning command, String actor) {
        return Views.Requirement.from(workflow.plan(requirementId, command, actor));
    }

    @Override
    public Views.Record createPrd(Long requirementId, Commands.Prd command, String actor) {
        return view(workflow.prd(requirementId, command, actor));
    }

    @Override
    public Views.Record createConfirmation(Long requirementId, Commands.Confirmation command, String actor) {
        return view(workflow.confirmation(requirementId, command, actor));
    }

    @Override
    public Views.Record conclude(Long confirmationId, Commands.Conclusion command, String actor) {
        return view(workflow.conclude(confirmationId, command, actor));
    }

    @Override
    public Views.Requirement readyForReview(Long requirementId, String actor) {
        return Views.Requirement.from(workflow.ready(requirementId, actor));
    }

    @Override
    public Views.Record review(Long requirementId, Commands.Review command, String actor) {
        return view(workflow.review(requirementId, command, actor));
    }

    @Override
    public Views.Record createTask(Long requirementId, Commands.Task command, String actor) {
        return view(workflow.task(requirementId, command, actor));
    }

    @Override
    public Views.Record confirmTask(Long taskId, String actor) {
        return view(workflow.confirmTask(taskId, actor));
    }

    @Override
    public Views.Record developTask(Long taskId, Commands.Development command, String actor) {
        return view(workflow.development(taskId, command, actor));
    }

    @Override
    public Views.Record progressTask(Long taskId, Commands.TaskProgress command, String actor) {
        return view(workflow.progress(taskId, command, actor));
    }

    @Override
    public Page<Views.Record> listRecords(String category, Pageable pageable) {
        return workflow.listRecords(category, pageable).map(this::view);
    }

    @Override
    public Page<Views.Record> listRequirementRecords(Long requirementId, String category, Pageable pageable) {
        return workflow.recordsPage(requirementId, category, pageable).map(this::view);
    }

    private Views.Record view(WorkflowRecord record) {
        return new Views.Record(record.id, record.category, record.requirementId, record.iterationId,
            record.parentId, record.versionNumber, record.status, node(record.payload), record.actor, record.createdAt);
    }

    private JsonNode node(String payload) {
        try {
            return json.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("记录格式损坏", exception);
        }
    }
}
