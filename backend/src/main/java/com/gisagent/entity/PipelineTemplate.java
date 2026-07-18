package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * 预置流程模板（模板市场底座）。
 * toolChainJson 存储工具节点序列（toolType 列表），由 PipelineEngine 解析为实际执行链。
 */
@Entity
@Table(name = "pipeline_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板唯一键，如 quick_selection / full_solution / bid_solution */
    @Column(name = "template_key", nullable = false, length = 64, unique = true)
    private String templateKey;

    @Column(nullable = false, length = 128)
    private String name;

    /** official / community / mine */
    @Column(length = 32)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 工具节点序列：JSON 数组，元素为 toolType，如 ["REQUIREMENT_ANALYSIS","PRODUCT_MATCHING"] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_chain", columnDefinition = "JSONB")
    @JsonProperty("toolChain")
    private String toolChainJson;

    @Column(name = "estimated_time", length = 32)
    private String estimatedTime;

    @Builder.Default
    @Column(name = "usage_count")
    private Long usageCount = 0L;

    /** 作者用户 ID；官方(builtin)模板为 null */
    @Column(name = "owner_id")
    private Long ownerId;

    @Builder.Default
    @Column(name = "like_count")
    private Long likeCount = 0L;

    @Builder.Default
    @Column(name = "favorite_count")
    private Long favoriteCount = 0L;

    /** 是否上架（社区模板可下架） */
    @Builder.Default
    @Column(name = "published")
    private boolean published = true;

    @Column(nullable = false)
    private boolean builtin;

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
