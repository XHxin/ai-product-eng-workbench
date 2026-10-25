package com.aiworkbench.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workflow_records")
public class WorkflowRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Version public long revision;
    @Column(nullable = false, length = 40) public String category;
    public Long requirementId;
    public Long iterationId;
    public Long parentId;
    public int versionNumber;
    @Column(nullable = false, length = 60) public String status;
    @Column(nullable = false, columnDefinition = "LONGTEXT") public String payload;
    @Column(length = 200) public String actor;
    public Instant createdAt = Instant.now();
}
