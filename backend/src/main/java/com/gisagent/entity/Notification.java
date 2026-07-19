package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.Instant;

@Entity
@Table(name = "notifications")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false, length = 32)
    private String type;   // TEAM_INVITE / COMMENT / VERSION_RESTORE / KB_UPDATE
    @Column(nullable = false, length = 256)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String body;
    @Column(length = 512)
    private String link;
    @Column(nullable = false)
    private Boolean isRead;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (isRead == null) isRead = false;
    }
}
