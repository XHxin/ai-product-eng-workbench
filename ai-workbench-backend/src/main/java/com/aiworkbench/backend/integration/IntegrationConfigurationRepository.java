package com.aiworkbench.backend.integration;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IntegrationConfigurationRepository extends JpaRepository<IntegrationConfigurationEntity, Long> {
    Optional<IntegrationConfigurationEntity> findByProvider(String provider);
}
