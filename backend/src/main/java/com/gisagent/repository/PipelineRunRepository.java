package com.gisagent.repository;

import com.gisagent.entity.PipelineRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {
    List<PipelineRun> findByProjectId(Long projectId);
    PipelineRun findFirstByProjectIdOrderByIdDesc(Long projectId);
}
