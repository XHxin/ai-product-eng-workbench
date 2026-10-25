package com.aiworkbench.backend;

import com.aiworkbench.backend.integration.IntegrationConfigurationService;
import com.aiworkbench.backend.integration.IntegrationConfigurationRepository;
import com.aiworkbench.backend.integration.SecretEncryptionService;
import com.aiworkbench.backend.service.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.datasource.url=jdbc:h2:mem:security;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "workbench.integrations.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class IntegrationSecurityTest {
    @Autowired
    IntegrationConfigurationService configurations;
    @Autowired
    IntegrationConfigurationRepository repository;
    @Autowired
    SecretEncryptionService encryption;

    @Test
    void savedConfigurationIsPersistentAndEncrypted() {
        configurations.save("OPENAI", "https://example.com/v1/chat/completions", "sample-api-secret", "sample-model",
            "admin");
        assertThat(configurations.isConfigured("OPENAI")).isTrue();
        String storedSecret = repository.findByProvider("OPENAI").orElseThrow().encryptedSecret;
        assertThat(storedSecret).startsWith("AESGCM:").doesNotContain("sample-api-secret");
        assertThat(encryption.decrypt(storedSecret)).isEqualTo("sample-api-secret");
    }

    @Test
    void maskedViewNeverReturnsSecret() {
        configurations.save("TINGJI", "https://example.com/minutes", "tingji-secret", null, "admin");
        assertThat(configurations.list())
            .filteredOn(view -> "TINGJI".equals(view.provider()))
            .allMatch(view -> "********".equals(view.secret()));
    }

    @Test
    void malformedEndpointIsRejectedAsBusinessValidation() {
        assertThatThrownBy(() -> configurations.save("OPENAI", "https://[invalid", "key", "model", "admin"))
            .isInstanceOf(BusinessException.class).hasMessageContaining("地址");
    }

    @Test
    void endpointWithoutHostIsRejected() {
        assertThatThrownBy(() -> configurations.save("OPENAI", "https:///path", "key", "model", "admin"))
            .isInstanceOf(BusinessException.class).hasMessageContaining("地址");
    }

    @Test
    void privateEndpointIsRejected() {
        assertThatThrownBy(() -> configurations.save("OPENAI", "https://127.0.0.1/v1/chat", "key", "model", "admin"))
            .isInstanceOf(BusinessException.class).hasMessageContaining("内网");
    }
}
