package com.aiworkbench.backend;

import com.aiworkbench.backend.integration.IntegrationConfigurationRepository;
import com.aiworkbench.backend.integration.IntegrationConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.datasource.url=jdbc:h2:mem:aes;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "workbench.integrations.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class SecurityBoundaryTest {
    @Autowired
    IntegrationConfigurationService configurations;
    @Autowired
    IntegrationConfigurationRepository repository;

    @Test
    void secretIsStoredAsAuthenticatedCiphertextNotMask() {
        configurations.save("OPENAI", "https://example.com/v1/chat/completions", "sample-api-secret", "sample-model",
            "admin");
        String storedSecret = repository.findByProvider("OPENAI").orElseThrow().encryptedSecret;
        assertThat(storedSecret).startsWith("AESGCM:").doesNotContain("sample-api-secret");
    }

    @Test
    void unsupportedProviderAndHttpEndpointAreRejected() {
        assertThatThrownBy(() -> configurations.save("UNKNOWN_APP", "https://example.com", "secret", null, "admin"))
            .hasMessageContaining("不支持");
        assertThatThrownBy(() -> configurations.save("OPENAI", "http://example.com", "secret", null, "admin"))
            .hasMessageContaining("HTTPS");
    }
}
