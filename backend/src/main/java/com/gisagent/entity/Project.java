package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 归属团队（团队空间）；null 表示个人项目（仅创建者可见可改） */
    @Column(name = "team_id")
    private Long teamId;

    /** 所属组织（租户），用于跨组织数据隔离 */
    @Column(name = "organization_id")
    private Long organizationId;

    @Column(nullable = false, length = 256)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "template_id", length = 64)
    private String templateId;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = "DRAFT";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", columnDefinition = "JSONB")
    private String contextJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 知识库是否检测到更新（需基于最新知识库重生成） */
    @Column(name = "kb_dirty", nullable = false)
    @Builder.Default
    private Boolean kbDirty = false;

    /** 知识库更新说明（如：哪些知识库有新增/修改文档） */
    @Column(name = "kb_dirty_note", columnDefinition = "TEXT")
    private String kbDirtyNote;

    /** 知识库标记脏的时间 */
    @Column(name = "kb_dirty_since")
    private Instant kbDirtySince;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (kbDirty == null) kbDirty = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
