package com.aiworkbench.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.datasource.url=jdbc:h2:mem:release-flow;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "workbench.integrations.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
class IterationReleaseTest {
    @Autowired
    MockMvc client;
    @Autowired
    ObjectMapper json;

    @Test
    void iterationBecomesReadyWhenAllTasksDoneAndReleaseCompletesRequirements() throws Exception {
        long requirementId = createRequirement();
        long iterationId = createIteration();
        prepareRequirement(requirementId, iterationId);
        long firstTask = createTask(requirementId, "实现指标定义");
        long secondTask = createTask(requirementId, "实现指标计算");
        prepareTask(firstTask);
        prepareTask(secondTask);

        progress(firstTask);
        iterationStatus(iterationId, "LOCKED");
        client.perform(get("/api/dashboard/summary").with(user("pm").roles("PM")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingReleases").value(0));

        progress(secondTask);
        iterationStatus(iterationId, "READY_FOR_RELEASE");
        client.perform(get("/api/dashboard/summary").with(user("pm").roles("PM")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingReleases").value(1));

        client.perform(post("/api/iterations/{id}/release", iterationId)
                .with(user("dev").roles("DEV")).with(csrf()))
            .andExpect(status().isForbidden());

        client.perform(post("/api/iterations/{id}/release", iterationId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RELEASED"));

        client.perform(get("/api/requirements/{id}", requirementId).with(user("pm").roles("PM")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"));

        client.perform(get("/api/dashboard/summary").with(user("pm").roles("PM")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingReleases").value(0))
            .andExpect(jsonPath("$.statusCounts.COMPLETED").value(1));

        client.perform(post("/api/iterations/{id}/release", iterationId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isConflict());

        client.perform(get("/api/iterations/{id}", iterationId).with(user("pm").roles("PM")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    private long createRequirement() throws Exception {
        MvcResult result = client.perform(post("/api/requirements")
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"发布流程需求\",\"rawText\":\"验证任务完成自动待发布\","
                    + "\"sourceType\":\"MANUAL\",\"sourceRef\":\"manual-release\",\"idempotencyKey\":\"release-flow-1\"}"))
            .andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    private long createIteration() throws Exception {
        MvcResult result = client.perform(post("/api/iterations")
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"发布流程迭代\",\"startDate\":\"2026-10-20\",\"endDate\":\"2026-11-02\"}"))
            .andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    private long prepareRequirement(long requirementId, long iterationId) throws Exception {
        client.perform(post("/api/requirements/{id}/normalize", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"发布流程需求\",\"normalizedText\":\"背景：验证发布流程\","
                    + "\"requirementType\":\"FEATURE\",\"priority\":\"P1\",\"rationale\":\"流程验证\"}"))
            .andExpect(status().isOk());
        client.perform(post("/api/requirements/{id}/planning", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"iterationId\":" + iterationId + "}"))
            .andExpect(status().isOk());
        client.perform(post("/api/iterations/{id}/lock", iterationId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isOk());

        MvcResult prd = client.perform(post("/api/requirements/{id}/prd", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"# PRD\",\"htmlContent\":\"<div>prototype</div>\"}"))
            .andExpect(status().isCreated()).andReturn();
        long prdId = idOf(prd);
        MvcResult confirmation = client.perform(post("/api/requirements/{id}/confirmations", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"是否复用 OLAP\",\"channel\":\"DINGTALK\",\"sourceRef\":\"msg-1\","
                    + "\"rawReply\":\"复用\"}"))
            .andExpect(status().isCreated()).andReturn();
        client.perform(post("/api/confirmations/{id}/confirm", idOf(confirmation))
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conclusion\":\"复用现有 OLAP\"}"))
            .andExpect(status().isOk());
        client.perform(post("/api/requirements/{id}/ready-for-review", requirementId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isOk());
        client.perform(post("/api/requirements/{id}/reviews", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"prdId\":" + prdId + ",\"decision\":\"APPROVED\",\"participants\":\"pm,dev\","
                    + "\"meetingAt\":\"2026-10-24\",\"conclusion\":\"通过\"}"))
            .andExpect(status().isOk());
        return prdId;
    }

    private long createTask(long requirementId, String title) throws Exception {
        MvcResult result = client.perform(post("/api/requirements/{id}/tasks", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"scope\":\"范围\",\"acceptance\":\"验收\","
                    + "\"dependencies\":\"无\"}"))
            .andExpect(status().isCreated()).andReturn();
        return idOf(result);
    }

    private void prepareTask(long taskId) throws Exception {
        client.perform(post("/api/tasks/{id}/confirm", taskId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isOk());
        client.perform(post("/api/tasks/{id}/development", taskId)
                .with(user("dev").roles("DEV")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedHours\":3,\"technicalPlan\":\"复用计算服务\","
                    + "\"subtasks\":[\"模型\"],\"testPoints\":[\"准确率\"]}"))
            .andExpect(status().isOk());
    }

    private void progress(long taskId) throws Exception {
        client.perform(post("/api/tasks/{id}/progress", taskId)
                .with(user("dev").roles("DEV")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
            .andExpect(status().isOk());
    }

    private void iterationStatus(long iterationId, String expected) throws Exception {
        client.perform(get("/api/iterations/{id}", iterationId).with(user("pm").roles("PM")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(expected));
    }

    private long idOf(MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.path("id").asLong();
    }
}
