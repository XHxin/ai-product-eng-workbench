package com.aiworkbench.backend.integration;

import com.aiworkbench.backend.audit.AuditLogService;
import com.aiworkbench.backend.service.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Service
public class IntegrationConfigurationService {
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("OPENAI", "DINGTALK", "TINGJI", "SHEET", "TEAMBITION");
    private static final String MASKED_SECRET = "********";

    private final EndpointSafetyService endpointSafety;
    private final IntegrationConfigurationRepository repository;
    private final SecretEncryptionService encryption;
    private final AuditLogService auditLogService;

    public IntegrationConfigurationService(EndpointSafetyService endpointSafety, IntegrationConfigurationRepository repository,
                                           SecretEncryptionService encryption, AuditLogService auditLogService) {
        this.endpointSafety = endpointSafety;
        this.repository = repository;
        this.encryption = encryption;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void save(String provider, String endpointText, String secret, String model, String actor) {
        String normalizedProvider = normalizeProvider(provider);
        if (endpointText == null || endpointText.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "集成地址不能为空", HttpStatus.BAD_REQUEST);
        }
        URI endpoint = parseEndpoint(endpointText);
        endpointSafety.validate(endpoint);
        IntegrationConfigurationEntity configuration = repository.findByProvider(normalizedProvider)
            .orElseGet(IntegrationConfigurationEntity::new);
        configuration.provider = normalizedProvider;
        configuration.endpoint = endpointText;
        if (secret != null && !secret.isBlank()) {
            configuration.encryptedSecret = encryption.encrypt(secret);
        } else if (configuration.id == null) {
            configuration.encryptedSecret = "";
        }
        configuration.model = model;
        configuration.updatedAt = Instant.now();
        repository.save(configuration);
        auditLogService.record(new AuditLogService.AuditEntry("INTEGRATION_CONFIGURED", actor,
            "INTEGRATION", "provider=" + normalizedProvider));
    }

    @Transactional(readOnly = true)
    public boolean isConfigured(String provider) {
        return repository.findByProvider(normalizeProvider(provider))
            .map(configuration -> configuration.endpoint != null && !configuration.endpoint.isBlank())
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<IntegrationView> list() {
        return repository.findAll().stream()
            .map(configuration -> new IntegrationView(configuration.provider, configuration.endpoint,
                configuration.model, MASKED_SECRET, true))
            .toList();
    }

    @Transactional(readOnly = true)
    public IntegrationConfigurationEntity require(String provider) {
        return repository.findByProvider(normalizeProvider(provider))
            .orElseThrow(() -> new BusinessException("INTEGRATION_NOT_CONFIGURED", "集成未配置", HttpStatus.SERVICE_UNAVAILABLE));
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "集成类型不能为空", HttpStatus.BAD_REQUEST);
        }
        String normalized = provider.trim().toUpperCase();
        if (!SUPPORTED_PROVIDERS.contains(normalized)) {
            throw new BusinessException("VALIDATION_ERROR", "不支持的集成类型", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private URI parseEndpoint(String endpointText) {
        try {
            return new URI(endpointText.trim());
        } catch (URISyntaxException exception) {
            throw new BusinessException("UNSAFE_ENDPOINT", "集成地址格式无效", HttpStatus.BAD_REQUEST);
        }
    }

    public record IntegrationView(String provider, String endpoint, String model, String secret, boolean configured) {}
}
