package com.gisagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "project_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "file_type", nullable = false, length = 32)
    private String fileType;

    @Column(name = "file_size", nullable = false)
    @Builder.Default
    private Long fileSize = 0L;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = Instant.now();
    }
}
