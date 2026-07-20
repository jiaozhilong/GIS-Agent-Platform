package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * 账期账单（P8-1 计费纵深）。
 * 由超管按月结算生成：聚合该组织当月所有 pipeline_runs 的 token 用量与估算费用。
 * (organizationId, periodMonth) 唯一，保证重新生成时幂等覆盖。
 */
@Entity
@Table(name = "invoices", uniqueConstraints = {
    @UniqueConstraint(name = "uk_invoice_org_month", columnNames = {"organization_id", "period_month"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 账期 yyyy-MM */
    @Column(name = "period_month", nullable = false, length = 16)
    private String periodMonth;

    @Column(name = "run_count", nullable = false)
    @Builder.Default
    private Long runCount = 0L;

    @Column(name = "input_tokens", nullable = false)
    @Builder.Default
    private Long inputTokens = 0L;

    @Column(name = "output_tokens", nullable = false)
    @Builder.Default
    private Long outputTokens = 0L;

    @Column(name = "total_tokens", nullable = false)
    @Builder.Default
    private Long totalTokens = 0L;

    /** 估算费用（元） */
    @Column(name = "estimated_cost", nullable = false)
    @Builder.Default
    private Double estimatedCost = 0.0;

    /** 账单状态：DRAFT（已生成待结算）/ SETTLED（已结算） */
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
