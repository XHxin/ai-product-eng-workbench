package com.aiworkbench.backend.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="requirements")
public class RequirementEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Version public long revision;
    @Column(nullable=false,length=200) public String title;
    @Column(nullable=false,columnDefinition="TEXT") public String rawText;
    @Column(nullable=false,length=40) public String sourceType;
    @Column(nullable=false,length=1000) public String sourceRef;
    @Column(nullable=false,unique=true,length=200) public String idempotencyKey;
    @Column(columnDefinition="TEXT") public String normalizedText="";
    public String requirementType="FEATURE";
    public String priority="P2";
    public String status="EXTRACT";
    public Long iterationId;
    public Instant createdAt=Instant.now();
}
