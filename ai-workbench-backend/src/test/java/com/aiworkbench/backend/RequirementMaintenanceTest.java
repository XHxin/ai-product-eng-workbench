package com.aiworkbench.backend;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.datasource.url=jdbc:h2:mem:maintenance;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "workbench.integrations.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
class RequirementMaintenanceTest {
    @Autowired
    MockMvc client;
    @Autowired
    ObjectMapper json;

    @Test
    void removesFromPlanningIterationAndDeletesPoolRequirements() throws Exception {
        long requirementId = createRequirement("maintenance-1");
        long iterationId = createIteration();
        assign(iterationId, requirementId);

        client.perform(delete("/api/iterations/{id}/requirements/{requirementId}", iterationId, requirementId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isNoContent());

        client.perform(get("/api/requirements/{id}", requirementId).with(user("pm").roles("PM")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("POOL"))
            .andExpect(jsonPath("$.iterationId").value(org.hamcrest.Matchers.nullValue()));

        assign(iterationId, requirementId);
        client.perform(post("/api/iterations/{id}/lock", iterationId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isOk());

        client.perform(delete("/api/iterations/{id}/requirements/{requirementId}", iterationId, requirementId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isConflict());

        client.perform(delete("/api/requirements/{id}", requirementId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isConflict());

        long poolRequirementId = createRequirement("maintenance-2");
        client.perform(delete("/api/requirements/{id}", poolRequirementId)
                .with(user("pm").roles("PM")).with(csrf()))
            .andExpect(status().isNoContent());

        client.perform(get("/api/requirements/{id}", poolRequirementId).with(user("pm").roles("PM")))
            .andExpect(status().isNotFound());

        long protectedRequirementId = createRequirement("maintenance-3");
        client.perform(delete("/api/requirements/{id}", protectedRequirementId)
                .with(user("dev").roles("DEV")).with(csrf()))
            .andExpect(status().isForbidden());

        long optionalRationaleId = createRequirement("maintenance-4");
        client.perform(post("/api/requirements/{id}/normalize", optionalRationaleId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"可选备注需求\",\"normalizedText\":\"需求原文内容\",\"requirementType\":\"FEATURE\",\"priority\":\"P2\",\"rationale\":\"\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("POOL"));

        client.perform(get("/api/audits").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].action",
                org.hamcrest.Matchers.hasItem("REQUIREMENT_REMOVED_FROM_ITERATION")))
            .andExpect(jsonPath("$.content[*].action",
                org.hamcrest.Matchers.hasItem("REQUIREMENT_DELETED")));
    }

    private void assign(long iterationId, long requirementId) throws Exception {
        client.perform(post("/api/iterations/{id}/requirements", iterationId)
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirementId\":" + requirementId + "}"))
            .andExpect(status().isOk());
    }

    private long createRequirement(String idempotencyKey) throws Exception {
        MvcResult result = client.perform(post("/api/requirements")
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"维护测试需求\",\"rawText\":\"验证删除与移除\",\"sourceType\":\"MANUAL\",\"sourceRef\":\"manual-1\",\"idempotencyKey\":\"" + idempotencyKey + "\"}"))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private long createIteration() throws Exception {
        MvcResult result = client.perform(post("/api/iterations")
                .with(user("pm").roles("PM")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"维护测试迭代\",\"startDate\":\"2026-10-20\",\"endDate\":\"2026-11-02\"}"))
            .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }
}
