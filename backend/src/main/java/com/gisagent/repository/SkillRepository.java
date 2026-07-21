package com.gisagent.repository;

import com.gisagent.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {

    List<Skill> findByOwnerId(Long ownerId);

    List<Skill> findByOwnerIdAndEnabledTrue(Long ownerId);

    /** 查找某工具节点下已启用、且可立即执行（API 端点已配置）的 Skill（取第一个） */
    Optional<Skill> findFirstByToolTypeAndEnabledTrueAndType(String toolType, String type);

    List<Skill> findByToolType(String toolType);
}
