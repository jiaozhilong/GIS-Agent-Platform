package com.gisagent.repository;

import com.gisagent.entity.Export;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExportRepository extends JpaRepository<Export, Long> {
    List<Export> findByProjectId(Long projectId);
    List<Export> findByProjectIdAndFileType(Long projectId, String fileType);
}
