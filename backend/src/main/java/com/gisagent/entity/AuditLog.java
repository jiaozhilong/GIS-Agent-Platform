package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String username;
    @Column(nullable = false, length = 64)
    private String action;
    @Column(length = 64)
    private String targetType;
    private Long targetId;
    @Column(columnDefinition = "TEXT")
    private String detail;
    @Column(length = 45)
    private String ipAddress;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist void prePersist() { if (createdAt == null) createdAt = Instant.now(); }
}
