package com.aiworkbench.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.profiles.active=test","spring.datasource.url=jdbc:h2:mem:gaps;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop","spring.flyway.enabled=false","workbench.integrations.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc
class MissingCapabilitiesTest {
    @Autowired MockMvc client;

    @Test void unconfiguredAiReturnsExplicitError() throws Exception {
        client.perform(post("/api/ai/generate").with(user("pm").roles("PM")).with(csrf())
            .contentType("application/json").content("{\"prompt\":\"生成需求\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("INTEGRATION_NOT_CONFIGURED"));
    }

    @Test void configurationNeverReturnsSecretAndRejectsPrivateEndpoint() throws Exception {
        client.perform(put("/api/integrations/OPENAI").with(user("admin").roles("ADMIN")).with(csrf())
            .contentType("application/json").content("{\"provider\":\"OPENAI\",\"endpoint\":\"http://127.0.0.1:1234\",\"secret\":\"never-expose-this\",\"model\":\"sample\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNSAFE_ENDPOINT"));
    }

    @Test void unavailableImportsHaveExplicitManualFallback() throws Exception {
        client.perform(post("/api/integrations/TINGJI/read").with(user("pm").roles("PM")).with(csrf())
            .contentType("application/json").content("{\"sourceRef\":\"minutes-reference\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MANUAL_REQUIRED"));
    }

    @Test void auditWorkspaceRequiresAdmin() throws Exception {
        client.perform(get("/api/audits").with(user("dev").roles("DEV"))).andExpect(status().isForbidden());
        client.perform(get("/api/audits").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
    }

    @Test void releaseNeedsExistingIteration() throws Exception {
        client.perform(post("/api/iterations/999999/release-reminder").with(user("release").roles("RELEASE_MANAGER")).with(csrf()))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
