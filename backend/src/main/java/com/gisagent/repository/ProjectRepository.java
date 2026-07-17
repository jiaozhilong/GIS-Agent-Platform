package com.gisagent.repository;

import com.gisagent.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByUserId(Long userId);
    List<Project> findByUserIdAndStatus(Long userId, String status);
}
