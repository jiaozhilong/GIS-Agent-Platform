package com.gisagent.repository;

import com.gisagent.entity.PipelineRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {
    List<PipelineRun> findByProjectId(Long projectId);
    PipelineRun findFirstByProjectIdOrderByIdDesc(Long projectId);

    // P4-4 使用数据看板聚合
    List<PipelineRun> findByProjectIdIn(List<Long> projectIds);
    long countByProjectIdIn(List<Long> projectIds);
    long countByProjectIdInAndStatus(List<Long> projectIds, String status);
}
