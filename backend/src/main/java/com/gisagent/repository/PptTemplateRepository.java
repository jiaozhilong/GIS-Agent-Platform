package com.gisagent.repository;

import com.gisagent.entity.PptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PptTemplateRepository extends JpaRepository<PptTemplate, Long> {

    List<PptTemplate> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PptTemplate> findByIdAndUserId(Long id, Long userId);

    Optional<PptTemplate> findByUserIdAndIsDefaultTrue(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE PptTemplate t SET t.isDefault = false WHERE t.userId = :userId AND t.isDefault = true")
    void clearDefaultByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);
}
