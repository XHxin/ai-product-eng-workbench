package com.aiworkbench.backend.integration;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "integration_configurations")
public class IntegrationConfigurationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 60) public String provider;
    @Column(nullable = false, length = 1000) public String endpoint;
    @Column(nullable = false, columnDefinition = "LONGTEXT") public String encryptedSecret;
    @Column(length = 200) public String model;
    @Column(nullable = false) public Instant updatedAt = Instant.now();
}
