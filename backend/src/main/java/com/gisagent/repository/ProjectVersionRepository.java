package com.gisagent.repository;

import com.gisagent.entity.ProjectVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectVersionRepository extends JpaRepository<ProjectVersion, Long> {

    /** 某项目的全部版本，按 id 倒序（即版本号倒序，最新在前） */
    List<ProjectVersion> findByProjectIdOrderByIdDesc(Long projectId);

    /** 按项目 + 版本 id 查询（用于详情/回退，配合项目归属校验） */
    Optional<ProjectVersion> findByProjectIdAndId(Long projectId, Long id);

    /** 当前最大版本号（无版本时返回 null） */
    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM ProjectVersion v WHERE v.projectId = :pid")
    Integer maxVersionNo(@Param("pid") Long projectId);
}
