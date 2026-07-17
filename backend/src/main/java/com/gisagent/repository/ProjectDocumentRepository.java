package com.gisagent.repository;

import com.gisagent.entity.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {
    List<ProjectDocument> findByProjectId(Long projectId);
}
