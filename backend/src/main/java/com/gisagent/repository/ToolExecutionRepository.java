package com.gisagent.repository;

import com.gisagent.entity.ToolExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ToolExecutionRepository extends JpaRepository<ToolExecution, Long> {
    List<ToolExecution> findByPipelineRunIdOrderByToolOrder(Long pipelineRunId);
}
