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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.datasource.url=jdbc:h2:mem:lifecycle;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "workbench.integrations.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
class FullLifecycleTest {
    @Autowired
    MockMvc client;
    @Autowired
    ObjectMapper json;

    @Test
    void requirementMovesFromIntakeToCompletedRelease() throws Exception {
        long requirementId = createRequirement("lifecycle-key");
        assertThat(createRequirement("lifecycle-key")).isEqualTo(requirementId);

        client.perform(post("/api/requirements/{id}/normalize", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"自定义看板指标\",\"normalizedText\":\"支持业务方自定义公式\",\"requirementType\":\"FEATURE\",\"priority\":\"P1\",\"rationale\":\"影响留存\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("POOL"));

        long iterationId = createIteration();
        client.perform(post("/api/requirements/{id}/planning", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"iterationId\":" + iterationId + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ITERATION"));

        client.perform(get("/api/iterations/{id}/requirements", iterationId).with(user("pm").roles("PM")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(requirementId))
            .andExpect(jsonPath("$[0].status").value("ITERATION"));

        client.perform(post("/api/requirements/{id}/prd", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"# PRD\",\"htmlContent\":\"<div>prototype</div>\"}"))
            .andExpect(status().isConflict());

        client.perform(post("/api/iterations/{id}/lock", iterationId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LOCKED"));

        long prdId = createdId(client.perform(post("/api/requirements/{id}/prd", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"# PRD\",\"htmlContent\":\"<div>prototype</div>\"}"))
            .andExpect(status().isCreated()).andReturn());

        long confirmationId = createdId(client.perform(post("/api/requirements/{id}/confirmations", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"是否复用OLAP\",\"channel\":\"DINGTALK\",\"sourceRef\":\"msg-1\",\"rawReply\":\"复用\"}"))
            .andExpect(status().isCreated()).andReturn());

        client.perform(post("/api/requirements/{id}/ready-for-review", requirementId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isConflict());

        client.perform(post("/api/confirmations/{id}/confirm", confirmationId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"conclusion\":\"复用现有OLAP\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        client.perform(post("/api/requirements/{id}/ready-for-review", requirementId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEW"));

        client.perform(post("/api/requirements/{id}/reviews", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"prdId\":" + prdId + ",\"decision\":\"APPROVED\",\"participants\":\"pm,dev\",\"meetingAt\":\"2026-10-24\",\"conclusion\":\"通过\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        long taskId = createdId(client.perform(post("/api/requirements/{id}/tasks", requirementId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"实现指标引擎\",\"scope\":\"指标定义\",\"acceptance\":\"计算准确\",\"dependencies\":\"OLAP\"}"))
            .andExpect(status().isCreated()).andReturn());

        client.perform(post("/api/tasks/{id}/confirm", taskId).with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        client.perform(post("/api/tasks/{id}/development", taskId)
                .with(user("dev").roles("DEV")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedHours\":5,\"technicalPlan\":\"复用计算服务\",\"subtasks\":[\"模型\",\"迁移\"],\"testPoints\":[\"准确率\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));

        client.perform(post("/api/tasks/{id}/progress", taskId)
                .with(user("dev").roles("DEV")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DONE\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));

        client.perform(post("/api/iterations/{id}/release-reminder", iterationId)
                .with(user("release").roles("RELEASE_MANAGER")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"v1.0.0\",\"notes\":\"迭代完成\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.reminderStatus").value("SENT"));

        client.perform(post("/api/iterations/{id}/release-confirm", iterationId)
                .with(user("release").roles("RELEASE_MANAGER")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"v1.0.0\",\"notes\":\"人工确认发布\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.releaseStatus").value("SUCCESS"))
            .andExpect(jsonPath("$.writebackStatus").value("MANUAL_REQUIRED"))
            .andExpect(jsonPath("$.notifyStatus").value("MANUAL_REQUIRED"));

        client.perform(post("/api/iterations/{id}/release-confirm", iterationId)
                .with(user("release").roles("RELEASE_MANAGER")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":\"v1.0.0\",\"notes\":\"重复\"}"))
            .andExpect(status().isConflict());

        client.perform(get("/api/requirements/{id}", requirementId).with(user("pm").roles("PM")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));

        client.perform(get("/api/audits").with(user("pm").roles("PM"))).andExpect(status().isForbidden());
        client.perform(get("/api/audits").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(5)));

        client.perform(get("/api/dashboard/summary").with(user("pm").roles("PM")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalRequirements").value(1));
    }

    private long createRequirement(String idempotencyKey) throws Exception {
        MvcResult result = client.perform(post("/api/requirements")
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"看板自定义指标\",\"rawText\":\"客户希望看板支持自定义指标\",\"sourceType\":\"MANUAL\",\"sourceRef\":\"manual-1\",\"idempotencyKey\":\"" + idempotencyKey + "\"}"))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private long createIteration() throws Exception {
        MvcResult result = client.perform(post("/api/iterations")
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Q4迭代二\",\"startDate\":\"2026-10-20\",\"endDate\":\"2026-11-02\"}"))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private long createdId(MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.path("id").asLong();
    }
}
