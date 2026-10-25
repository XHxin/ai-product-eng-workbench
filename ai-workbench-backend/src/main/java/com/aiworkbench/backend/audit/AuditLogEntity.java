package com.aiworkbench.backend.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, length = 100) public String action;
    @Column(nullable = false, length = 100) public String actor;
    @Column(nullable = false, length = 100) public String resource;
    @Column(nullable = false, columnDefinition = "LONGTEXT") public String details;
    @Column(nullable = false) public Instant createdAt = Instant.now();
}
