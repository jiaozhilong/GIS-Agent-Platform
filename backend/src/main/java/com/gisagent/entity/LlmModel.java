package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "llm_models")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "context_window", nullable = false)
    @Builder.Default
    private Integer contextWindow = 8192;

    @Column(name = "max_tokens", nullable = false)
    @Builder.Default
    private Integer maxTokens = 4096;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
