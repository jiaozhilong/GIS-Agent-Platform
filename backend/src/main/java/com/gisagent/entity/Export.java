package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "exports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Export {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "file_type", nullable = false, length = 16)
    private String fileType;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    @Builder.Default
    private Long fileSize = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
