package com.gisagent.repository;

import com.gisagent.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByUserId(Long userId);
    List<Project> findByUserIdAndStatus(Long userId, String status);
    List<Project> findByTeamIdIn(List<Long> teamIds);

    // 多租户隔离查询（P7-1）
    List<Project> findByOrganizationId(Long organizationId);
    List<Project> findByOrganizationIdAndUserId(Long organizationId, Long userId);
    List<Project> findByOrganizationIdAndTeamIdIn(Long organizationId, List<Long> teamIds);
}
