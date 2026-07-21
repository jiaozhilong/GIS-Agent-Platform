package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * 每个用户自己的 IMA 开放平台凭证（按用户隔离）。
 * client_id / api_key 加密存储（参照 LlmProvider.apiKeyEncrypted），不落明文、不进前端响应。
 * 一个用户一套 IMA 账号（决定能检索到该账号下订阅的笔记本），可挂载多个知识库配置（ima_kb_configs）。
 */
@Entity
@Table(name = "ima_credentials")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImaCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "client_id_encrypted", length = 512)
    private String clientIdEncrypted;

    @Column(name = "api_key_encrypted", length = 512)
    private String apiKeyEncrypted;

    /** IMA OpenAPI base url，可空（默认 https://ima.qq.com/openapi/note/v1） */
    @Column(name = "base_url", length = 512)
    private String baseUrl;

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
