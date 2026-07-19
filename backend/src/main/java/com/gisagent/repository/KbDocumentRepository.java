package com.gisagent.repository;

import com.gisagent.entity.KbDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface KbDocumentRepository extends JpaRepository<KbDocument, Long> {

    List<KbDocument> findByProjectIdOrderByChunkIndex(Long projectId);

    @Modifying
    @Transactional
    @Query("DELETE FROM KbDocument d WHERE d.projectId = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    @Query(value = """
        SELECT d.*, 1 - (d.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
        FROM kb_documents d
        WHERE d.project_id = :projectId
          AND d.embedding IS NOT NULL
        ORDER BY d.embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<KbDocument> findSimilar(@Param("projectId") Long projectId,
                                  @Param("queryEmbedding") String queryEmbedding,
                                  @Param("limit") int limit);

    @Query(value = """
        SELECT d.*, 1 - (d.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
        FROM kb_documents d
        JOIN projects p ON p.id = d.project_id
        WHERE p.user_id = :userId
          AND d.embedding IS NOT NULL
        ORDER BY d.embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<KbDocument> findSimilarAcrossUserProjects(@Param("userId") Long userId,
                                                    @Param("queryEmbedding") String queryEmbedding,
                                                    @Param("limit") int limit);
}
