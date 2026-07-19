package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 256)
    private String password;

    /** 平台全局角色：SUPER_ADMIN / ADMIN / USER（区别于团队级 Role） */
    @Column(nullable = false, length = 16)
    private String role = "USER";

    /** 账号是否启用（禁用后无法登录） */
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(length = 128)
    private String email;

    @Column(name = "display_name", length = 64)
    private String displayName;

    /** 所属组织（租户）；多租户隔离单位。null 表示未分配（历史数据回填前） */
    @Column(name = "organization_id")
    private Long organizationId;

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
