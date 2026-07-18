package com.gisagent.repository;

import com.gisagent.entity.TemplateFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TemplateFavoriteRepository extends JpaRepository<TemplateFavorite, Long> {
    Optional<TemplateFavorite> findByTemplateIdAndUserId(Long templateId, Long userId);
    boolean existsByTemplateIdAndUserId(Long templateId, Long userId);
    long countByTemplateId(Long templateId);
    void deleteByTemplateIdAndUserId(Long templateId, Long userId);
}
