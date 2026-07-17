package com.gisagent.repository;

import com.gisagent.entity.PipelineTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PipelineTemplateRepository extends JpaRepository<PipelineTemplate, Long> {
    Optional<PipelineTemplate> findByTemplateKey(String templateKey);

    List<PipelineTemplate> findByCategory(String category);

    List<PipelineTemplate> findByBuiltinTrue();

    boolean existsByTemplateKey(String templateKey);

    List<PipelineTemplate> findAllByOrderByBuiltinDescIdAsc();
}
