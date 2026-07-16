package com.gisagent.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IMA 知识库连接器的 Mock 实现。
 * 用于 Phase 1 MVP 开发阶段，模拟 IMA MCP 接口行为。
 * 当 ima.mock-enabled=true 时启用（默认）。
 */
@Component
@ConditionalOnProperty(name = "ima.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockIMAKnowledgeBaseConnector implements IMAKnowledgeBaseConnector {

    private static final Logger log = LoggerFactory.getLogger(MockIMAKnowledgeBaseConnector.class);

    @Override
    public boolean testConnection(String kbId, String credential) {
        log.info("[Mock] Testing IMA connection for kbId={}", kbId);
        return true;
    }

    @Override
    public SearchResult search(String kbId, String query, SearchOptions options) {
        log.info("[Mock] Searching kbId={}, query={}, topK={}, minScore={}, purpose={}",
                kbId, query, options.topK(), options.minScore(), options.purpose());

        // 返回模拟检索结果
        String mockDocId = "mock-" + UUID.randomUUID().toString().substring(0, 8);
        return new SearchResult(
                mockDocId,
                "Mock Document: " + query,
                "This is a mock search result for query: " + query + ". "
                        + "It simulates the response from IMA knowledge base " + kbId + ". "
                        + "Replace with real IMA MCP integration when available.",
                0.92,
                kbId,
                Instant.now()
        );
    }

    @Override
    public List<SearchResult> search(String kbId, String query, SearchOptions options) {
        // 返回多条模拟结果
        return List.of(
                search(kbId, query, options),
                new SearchResult(
                        "mock-" + UUID.randomUUID().toString().substring(0, 8),
                        "SuperMap GIS Product Overview",
                        "SuperMap GIS platform provides comprehensive spatial data management, "
                                + "2D/3D visualization, spatial analysis, and cloud services.",
                        0.85,
                        kbId,
                        Instant.now().minusSeconds(86400)
                ),
                new SearchResult(
                        "mock-" + UUID.randomUUID().toString().substring(0, 8),
                        "Smart City Solution Case Study",
                        "This case study demonstrates how SuperMap GIS was deployed "
                                + "in a smart city project covering 500 sq km urban area.",
                        0.78,
                        kbId,
                        Instant.now().minusSeconds(172800)
                )
        );
    }

    @Override
    public KBInfo getKBInfo(String kbId) {
        return new KBInfo(kbId, "Mock Knowledge Base", "subscribed", 150, Instant.now());
    }

    @Override
    public List<KBUpdateEvent> getUpdates(String kbId, Instant since) {
        log.info("[Mock] Checking updates for kbId={} since={}", kbId, since);
        return Collections.emptyList();
    }
}
