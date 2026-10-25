package com.aiworkbench.backend.release;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "release_records", uniqueConstraints = @UniqueConstraint(columnNames = {"iteration_id", "version"}))
public class ReleaseRecordEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false) public Long iterationId;
    @Column(nullable = false, length = 100) public String version;
    @Column(nullable = false, length = 40) public String reminderStatus = "PENDING";
    @Column(nullable = false, length = 40) public String confirmationStatus = "PENDING";
    @Column(nullable = false, length = 40) public String releaseStatus = "PENDING";
    @Column(nullable = false, length = 40) public String writebackStatus = "PENDING";
    @Column(nullable = false, length = 40) public String notifyStatus = "PENDING";
    @Column(length = 10000) public String notes;
    @Column(length = 100) public String confirmedBy;
    @Column(length = 1000) public String lastError;
    @Column(nullable = false) public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();
}
