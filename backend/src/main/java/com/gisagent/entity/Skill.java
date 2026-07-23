package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * 可编排 Skill（外部能力）。
 * 每个 Skill 绑定到一个流水线工具节点（toolType），运行时若该 toolType 存在已启用的
 * API_ENDPOINT 类型 Skill，则由 SkillTool 调用外部端点替代内置逻辑。
 *
 * 类型：
 *  - API_ENDPOINT：HTTP POST 调用外部技能服务（如社区 ppt-master、自建 API）。
 *  - GIT_REPO：从 Git 仓库拉取技能脚本执行（Phase 2 沙箱支持，当前仅配置）。
 */
@Entity
@Table(name = "skills")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 创建者（按用户隔离） */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    @Column(length = 1024)
    private String description;

    /** API_ENDPOINT | GIT_REPO */
    @Column(name = "type", nullable = false, length = 32)
    private String type;

    /** 该 Skill 替换的流水线工具节点（REQUIREMENT_ANALYSIS / PPT_OUTPUT ...） */
    @Column(name = "tool_type", nullable = false, length = 64)
    private String toolType;

    /** API_ENDPOINT：外部技能服务地址 */
    @Column(name = "endpoint_url", length = 512)
    private String endpointUrl;

    /** API_ENDPOINT：可选鉴权密钥（加密存储） */
    @Column(name = "api_key_encrypted", length = 512)
    private String apiKeyEncrypted;

    /** API_ENDPOINT：请求体模板 / 附加 Prompt（可选，{context} 占位由引擎替换） */
    @Column(name = "request_template", length = 4096)
    private String requestTemplate;

    /** GIT_REPO：仓库地址 */
    @Column(name = "git_repo_url", length = 512)
    private String gitRepoUrl;

    /** GIT_REPO：分支 / tag / commit */
    @Column(name = "git_ref", length = 128)
    private String gitRef;

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
