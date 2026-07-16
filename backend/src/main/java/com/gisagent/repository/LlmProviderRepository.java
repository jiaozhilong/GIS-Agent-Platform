package com.gisagent.repository;

import com.gisagent.entity.LlmProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LlmProviderRepository extends JpaRepository<LlmProvider, Long> {
    List<LlmProvider> findByUserId(Long userId);
    Optional<LlmProvider> findByIdAndUserId(Long id, Long userId);
    List<LlmProvider> findByUserIdAndIsDefaultTrue(Long userId);
}
