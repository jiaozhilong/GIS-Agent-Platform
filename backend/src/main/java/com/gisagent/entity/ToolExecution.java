package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "tool_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pipeline_run_id", nullable = false)
    private Long pipelineRunId;

    @Column(name = "tool_type", nullable = false, length = 64)
    private String toolType;

    @Column(name = "tool_order", nullable = false)
    @Builder.Default
    private Integer toolOrder = 0;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "llm_provider_id")
    private Long llmProviderId;

    @Column(name = "llm_model", length = 128)
    private String llmModel;

    @Column(name = "input_json", columnDefinition = "JSONB")
    private String inputJson;

    @Column(name = "output_json", columnDefinition = "JSONB")
    private String outputJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
