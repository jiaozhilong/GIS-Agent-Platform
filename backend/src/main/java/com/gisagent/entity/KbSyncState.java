package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * 知识库同步游标：记录每个用户每个知识库的"更新事件"读取位置（since 游标）。
 * 由 KbAwarenessScheduler 周期性调用 IMA 的 getUpdates(kbId, lastCursor) 拉取增量事件，
 * 发现新增/修改/删除时，将该用户下所有项目标记为 kbDirty，并推进游标。
 */
@Entity
@Table(name = "kb_sync_states")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KbSyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "kb_id", nullable = false, length = 128)
    private String kbId;

    /** 上次读取到的事件时间戳（游标）；下次从此刻之后拉取 */
    @Column(name = "last_cursor")
    private Instant lastCursor;

    /** 上次同步时间 */
    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PreUpdate
    @PrePersist
    protected void onTouch() {
        updatedAt = Instant.now();
    }
}
