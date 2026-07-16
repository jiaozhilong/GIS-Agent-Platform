package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "ima_kb_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImaKbConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "kb_id", nullable = false, length = 128)
    private String kbId;

    @Column(name = "kb_name", nullable = false, length = 256)
    private String kbName;

    @Column(name = "kb_type", nullable = false, length = 32)
    @Builder.Default
    private String kbType = "subscribed";

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String purpose = "general";

    @Column(name = "search_weight", nullable = false)
    @Builder.Default
    private Float searchWeight = 0.5f;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
