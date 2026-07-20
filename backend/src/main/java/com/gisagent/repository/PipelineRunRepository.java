package com.gisagent.repository;

import com.gisagent.entity.PipelineRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {
    List<PipelineRun> findByProjectId(Long projectId);
    PipelineRun findFirstByProjectIdOrderByIdDesc(Long projectId);

    // P4-4 使用数据看板聚合
    List<PipelineRun> findByProjectIdIn(List<Long> projectIds);
    long countByProjectIdIn(List<Long> projectIds);
    long countByProjectIdInAndStatus(List<Long> projectIds, String status);

    // P8-1 计费纵深：按月（UTC 时间窗）聚合某批项目的 token 用量
    @Query("SELECT COALESCE(SUM(r.inputTokens),0), COALESCE(SUM(r.outputTokens),0), " +
           "COALESCE(SUM(r.totalTokens),0), COUNT(r) " +
           "FROM PipelineRun r WHERE r.projectId IN :pids " +
           "AND r.finishedAt IS NOT NULL AND r.finishedAt >= :from AND r.finishedAt < :to")
    List<Object[]> aggregateByProjectsInWindow(@Param("pids") List<Long> pids,
                                               @Param("from") java.time.Instant from,
                                               @Param("to") java.time.Instant to);
}
