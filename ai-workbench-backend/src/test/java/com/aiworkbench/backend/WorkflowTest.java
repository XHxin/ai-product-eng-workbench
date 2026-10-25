package com.aiworkbench.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"spring.profiles.active=test","spring.datasource.url=jdbc:h2:mem:workbench;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false"})
@AutoConfigureMockMvc
class WorkflowTest {
    @Autowired MockMvc client;

    @Test void anonymousCannotReadRequirements() throws Exception {
        client.perform(get("/api/requirements")).andExpect(status().isUnauthorized());
    }

    @Test void developerCannotCreateRequirement() throws Exception {
        client.perform(post("/api/requirements").with(user("dev").roles("DEV")).with(csrf())
            .contentType("application/json").content("{\"title\":\"example\",\"rawText\":\"original\",\"sourceType\":\"MANUAL\",\"sourceRef\":\"sample\",\"idempotencyKey\":\"example\"}"))
            .andExpect(status().isForbidden());
    }

    @Test void missingCsrfIsRejected() throws Exception {
        client.perform(post("/api/requirements").with(user("pm").roles("PM")))
            .andExpect(status().isForbidden());
    }

    @Test void invalidRequirementIsRejected() throws Exception {
        client.perform(post("/api/requirements").with(user("pm").roles("PM")).with(csrf())
            .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
    }
}
