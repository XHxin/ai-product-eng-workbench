package com.aiworkbench.backend.integration;

import com.aiworkbench.backend.service.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class OpenAiCompatibleService {
    private static final String PROVIDER = "OPENAI";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    private final IntegrationConfigurationService configurationService;
    private final EndpointSafetyService endpointSafety;
    private final SecretEncryptionService encryption;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleService(IntegrationConfigurationService configurationService,
                                   EndpointSafetyService endpointSafety,
                                   SecretEncryptionService encryption,
                                   ObjectMapper objectMapper) {
        this.configurationService = configurationService;
        this.endpointSafety = endpointSafety;
        this.encryption = encryption;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    public String generate(String prompt) {
        if (!configurationService.isConfigured(PROVIDER)) {
            throw new BusinessException("INTEGRATION_NOT_CONFIGURED", "模型接口未配置，可使用人工填写模式", HttpStatus.SERVICE_UNAVAILABLE);
        }
        IntegrationConfigurationEntity configuration = configurationService.require(PROVIDER);
        URI endpoint = URI.create(configuration.endpoint);
        endpointSafety.validate(endpoint);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
            .timeout(READ_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + encryption.decrypt(configuration.encryptedSecret))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody(configuration.model, prompt)))
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("INTEGRATION_FAILED", "模型接口调用失败，可重试", HttpStatus.BAD_GATEWAY);
            }
            return extractContent(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("INTEGRATION_FAILED", "模型接口调用被中断，可重试", HttpStatus.BAD_GATEWAY);
        } catch (IOException exception) {
            throw new BusinessException("INTEGRATION_FAILED", "模型接口调用失败，可重试", HttpStatus.BAD_GATEWAY);
        }
    }

    private String requestBody(String model, String prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model == null || model.isBlank() ? "default" : model);
        ArrayNode messages = body.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", prompt);
        body.put("temperature", 0.2);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (IOException exception) {
            throw new BusinessException("INTEGRATION_FAILED", "无法构造模型请求", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new BusinessException("INTEGRATION_FAILED", "模型返回内容为空，可重试", HttpStatus.BAD_GATEWAY);
            }
            return content.asText();
        } catch (IOException exception) {
            throw new BusinessException("INTEGRATION_FAILED", "模型返回格式无法解析，可重试", HttpStatus.BAD_GATEWAY);
        }
    }
}
