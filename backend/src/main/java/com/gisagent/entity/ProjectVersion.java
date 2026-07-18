package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * 方案版本快照。
 * 每次流水线生成（自动）或用户手动保存时，把当时完整的 Context Bus
 * （含方案正文、架构图、大纲、质检等产物）序列化为 contextJson 留存，
 * 支持历史列表与一键回退。
 */
@Entity
@Table(name = "project_versions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 项目内自增版本号，从 1 开始 */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    /** 版本标题（自动生成时按触发类型给默认值，手动可自定义） */
    @Column(length = 128)
    private String title;

    /** 触发来源：AUTO_RUN（流水线生成）/ KB_RERUN（知识库重生成）/ MANUAL（手动保存） */
    @Column(name = "trigger_type", length = 32)
    private String triggerType;

    /** 完整 Context Bus 快照（ToolContext.toMap() 的 JSON） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", columnDefinition = "JSONB")
    private String contextJson;

    /** 方案正文冗余副本，便于列表预览与对比（不存 contextJson 整体） */
    @Column(name = "solution_text", columnDefinition = "TEXT")
    private String solutionText;

    /** 手动保存时的备注 */
    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
