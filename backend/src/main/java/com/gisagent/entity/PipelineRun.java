package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "pipeline_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "template_id", length = 64)
    private String templateId;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", columnDefinition = "JSONB")
    private String contextJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** LLM token 用量（P7-3 计费）：输入/输出/合计 */
    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

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
