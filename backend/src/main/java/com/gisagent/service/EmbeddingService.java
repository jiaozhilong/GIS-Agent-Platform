package com.gisagent.service;

import com.gisagent.entity.KbDocument;
import com.gisagent.entity.LlmProvider;
import com.gisagent.repository.KbDocumentRepository;
import com.gisagent.repository.LlmProviderRepository;
import com.gisagent.util.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文本向量化服务。
 * - 文本分块（按段落 + 重叠滑动窗口）
 * - 调用 LLM embedding API 生成向量
 * - 写入 kb_documents（含 pgvector embedding 列）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final LlmService llmService;
    private final LlmProviderRepository llmProviderRepository;
    private final EncryptionService encryptionService;
    private final KbDocumentRepository kbDocumentRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 100;

    /**
     * 将文本分块、向量化后写入 kb_documents。
     */
    @Transactional
    public int indexText(Long projectId, String source, String text, String metadata) {
        LlmProvider provider = resolveDefaultProvider();
        if (provider == null) {
            log.warn("没有配置 LLM Provider，跳过向量化");
            return 0;
        }
        String apiKey = encryptionService.decrypt(provider.getApiKeyEncrypted());
        String endpoint = provider.getEndpoint();

        List<String> chunks = chunkText(text);

        int written = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            float[] vec = llmService.embedding(endpoint, apiKey, chunk);
            if (vec == null) {
                log.warn("Embedding 失败，跳过 chunk {} (project={})", i, projectId);
                continue;
            }

            KbDocument doc = KbDocument.builder()
                    .projectId(projectId)
                    .source(source)
                    .content(chunk)
                    .chunkIndex(i)
                    .metadataJson(metadata)
                    .build();
            doc = kbDocumentRepository.save(doc);

            updateEmbedding(doc.getId(), vec);
            written++;
        }

        log.info("向量化完成: project={}, source={}, chunks={}, written={}",
                projectId, source, chunks.size(), written);
        return written;
    }

    /**
     * 混合检索：向量相似度 + 关键词回退。
     */
    public List<Map<String, Object>> hybridSearch(Long projectId, String query, int topK) {
        LlmProvider provider = resolveDefaultProvider();
        if (provider == null) {
            return keywordOnly(projectId, query, topK);
        }

        String apiKey = encryptionService.decrypt(provider.getApiKeyEncrypted());
        float[] queryVec = llmService.embedding(provider.getEndpoint(), apiKey, query);
        if (queryVec == null) {
            return keywordOnly(projectId, query, topK);
        }

        String vecStr = floatArrayToPgVector(queryVec);
        List<KbDocument> docs = kbDocumentRepository.findSimilar(projectId, vecStr, topK);

        return docs.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("content", d.getContent());
            m.put("source", d.getSource());
            m.put("chunkIndex", d.getChunkIndex());
            m.put("createdAt", d.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void clearProject(Long projectId) {
        kbDocumentRepository.deleteByProjectId(projectId);
        log.info("已清除 project={} 的向量文档", projectId);
    }

    // ---- internal ----

    private LlmProvider resolveDefaultProvider() {
        List<LlmProvider> all = llmProviderRepository.findAll();
        return all.isEmpty() ? null : all.get(0);
    }

    private String floatArrayToPgVector(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(Locale.US, "%.8f", vec[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    private void updateEmbedding(Long docId, float[] vec) {
        jdbcTemplate.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "UPDATE kb_documents SET embedding = ?::vector WHERE id = ?");
            ps.setObject(1, vec);
            ps.setLong(2, docId);
            return ps;
        });
    }

    List<String> chunkText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            if (end < text.length()) {
                int br = text.lastIndexOf('\n', end);
                if (br > start + CHUNK_SIZE / 2) {
                    end = br;
                } else {
                    int dot = text.lastIndexOf('。', end);
                    if (dot > start + CHUNK_SIZE / 2) {
                        end = dot + 1;
                    }
                }
            }
            chunks.add(text.substring(start, end).trim());
            start = end - CHUNK_OVERLAP;
            if (start < 0) start = 0;
            if (start >= end) start = end;
        }
        return chunks;
    }

    private List<Map<String, Object>> keywordOnly(Long projectId, String query, int topK) {
        List<KbDocument> docs = kbDocumentRepository.findByProjectIdOrderByChunkIndex(projectId);
        String lower = query.toLowerCase();
        return docs.stream()
                .filter(d -> d.getContent() != null && d.getContent().toLowerCase().contains(lower))
                .limit(topK)
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", d.getId());
                    m.put("content", d.getContent());
                    m.put("source", d.getSource());
                    m.put("chunkIndex", d.getChunkIndex());
                    m.put("createdAt", d.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }
}
