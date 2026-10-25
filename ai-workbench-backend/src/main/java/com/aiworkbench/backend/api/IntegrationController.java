package com.aiworkbench.backend.api;

import com.aiworkbench.backend.integration.IntegrationConfigurationService;
import com.aiworkbench.backend.integration.OpenAiCompatibleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class IntegrationController {
    private final IntegrationConfigurationService configurations;
    private final OpenAiCompatibleService generation;

    public IntegrationController(IntegrationConfigurationService configurations, OpenAiCompatibleService generation) {
        this.configurations = configurations;
        this.generation = generation;
    }

    @GetMapping("/integrations")
    @PreAuthorize("hasAnyRole('ADMIN','PM','DEV','RELEASE_MANAGER')")
    public List<IntegrationConfigurationService.IntegrationView> list() {
        return configurations.list();
    }

    @PutMapping("/integrations/{provider}")
    @PreAuthorize("hasRole('ADMIN')")
    public ConfigurationView save(@PathVariable String provider, @Valid @RequestBody Commands.Integration command,
                                  java.security.Principal principal) {
        configurations.save(provider, command.endpoint(), command.secret(), command.model(), principal.getName());
        return new ConfigurationView(provider.toUpperCase(), "CONFIGURED", "********");
    }

    @PostMapping("/integrations/{provider}/read")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public FallbackView read(@PathVariable String provider, @Valid @RequestBody SourceReference reference) {
        return new FallbackView(provider, "MANUAL_REQUIRED",
            "该集成在MVP中采用人工导入模式，请粘贴原文或上传文本；系统未执行外部读取。", reference.sourceRef());
    }

    @PostMapping("/ai/generate")
    @PreAuthorize("hasAnyRole('ADMIN','PM')")
    public GenerationView generate(@Valid @RequestBody GenerationRequest request) {
        return new GenerationView(generation.generate(request.prompt()));
    }

    public record SourceReference(@NotBlank @Size(max = 1000) String sourceRef) { }

    public record GenerationRequest(@NotBlank @Size(max = 20000) String prompt) { }

    public record GenerationView(String content) { }

    public record ConfigurationView(String provider, String status, String secret) { }

    public record FallbackView(String provider, String status, String message, String sourceRef) { }
}
