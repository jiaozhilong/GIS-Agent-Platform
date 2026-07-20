package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * 组织月度 token 配额（P8-1 计费纵深）。
 * 每个组织仅一条生效配额：tokenLimit 为该组织按月滚动的 token 预算，
 * warnThreshold 为告警触发百分比（0-100）。当前月用量由 pipeline_runs 实时聚合，
 * 达到阈值即触发审计 + 站内通知。alertedMonth 用于同月去重，避免每次运行重复告警。
 */
@Entity
@Table(name = "usage_quotas", uniqueConstraints = {
    @UniqueConstraint(name = "uk_quota_org", columnNames = "organization_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 该组织每月 token 预算（合计 token） */
    @Column(name = "token_limit", nullable = false)
    private Long tokenLimit;

    /** 告警阈值百分比（0-100），用量达到预算的该比例即告警 */
    @Column(name = "warn_threshold", nullable = false)
    @Builder.Default
    private Integer warnThreshold = 80;

    /** 已触发告警的账期（yyyy-MM），用于同月去重 */
    @Column(name = "alerted_month", length = 16)
    private String alertedMonth;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
