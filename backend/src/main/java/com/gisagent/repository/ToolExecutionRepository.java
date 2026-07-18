package com.gisagent.repository;

import com.gisagent.entity.ToolExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ToolExecutionRepository extends JpaRepository<ToolExecution, Long> {
    List<ToolExecution> findByPipelineRunIdOrderByToolOrder(Long pipelineRunId);

    Optional<ToolExecution> findByPipelineRunIdAndToolOrder(Long pipelineRunId, Integer toolOrder);

    // P4-4 使用数据看板聚合
    List<ToolExecution> findByPipelineRunIdIn(List<Long> pipelineRunIds);
}
