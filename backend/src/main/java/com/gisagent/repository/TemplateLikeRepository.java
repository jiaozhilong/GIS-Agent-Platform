package com.gisagent.repository;

import com.gisagent.entity.TemplateLike;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TemplateLikeRepository extends JpaRepository<TemplateLike, Long> {
    Optional<TemplateLike> findByTemplateIdAndUserId(Long templateId, Long userId);
    boolean existsByTemplateIdAndUserId(Long templateId, Long userId);
    long countByTemplateId(Long templateId);
    void deleteByTemplateIdAndUserId(Long templateId, Long userId);
}
