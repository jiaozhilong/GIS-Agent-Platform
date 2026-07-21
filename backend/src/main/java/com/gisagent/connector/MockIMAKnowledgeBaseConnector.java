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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * IMA 知识库连接器的 Mock 实现。
 * 用于 Phase 1 MVP 开发阶段，模拟 IMA MCP 接口行为。
 * 当 ima.mock-enabled=true 时启用（默认）。
 */
@Component
@ConditionalOnProperty(name = "ima.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockIMAKnowledgeBaseConnector implements IMAKnowledgeBaseConnector {

    /** 模拟更新事件开关（仅测试/联调用，消费一次即复位） */
    private final AtomicBoolean simulateNextUpdate = new AtomicBoolean(false);

    private static final Logger log = LoggerFactory.getLogger(MockIMAKnowledgeBaseConnector.class);

    @Override
    public boolean testConnection(String kbId, ImaAuth auth) {
        log.info("[Mock] Testing IMA connection for kbId={}", kbId);
        return true;
    }

    @Override
    public List<SearchResult> search(String kbId, String query, SearchOptions options, ImaAuth auth) {
        log.info("[Mock] Searching kbId={}, query={}, topK={}, minScore={}, purpose={}",
                kbId, query, options.topK(), options.minScore(), options.purpose());
        // 返回多条模拟结果
        return List.of(
                new SearchResult(
                        "mock-" + UUID.randomUUID().toString().substring(0, 8),
                        "Mock Document: " + query,
                        "This is a mock search result for query: " + query + ". "
                                + "It simulates the response from IMA knowledge base " + kbId + ". "
                                + "Replace with real IMA MCP integration when available.",
                        0.92,
                        kbId,
                        Instant.now()
                ),
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
    public List<KBInfo> listKnowledgeBases(ImaAuth auth) {
        log.info("[Mock] 列出 IMA 知识库（模拟）");
        return List.of(
                new KBInfo("kb-supermap-products", "超图产品智答库", "subscribed", 320, Instant.now()),
                new KBInfo("kb-smart-city-cases", "智慧城市案例库", "subscribed", 86, Instant.now()),
                new KBInfo("kb-gis-standards", "GIS 行业标准库", "subscribed", 54, Instant.now()),
                new KBInfo("kb-my-collection", "我的自建笔记库", "owned", 12, Instant.now())
        );
    }

    @Override
    public List<KBUpdateEvent> getUpdates(String kbId, Instant since) {
        log.info("[Mock] Checking updates for kbId={} since={}", kbId, since);
        // 测试/联调用：若已通过 armSimulation 装填，则返回一条 MODIFIED 事件（仅消费一次）
        if (simulateNextUpdate.compareAndSet(true, false)) {
            log.info("[Mock] 返回模拟的 MODIFIED 事件 kbId={}", kbId);
            return List.of(new KBUpdateEvent("MODIFIED", "mock-doc-" + UUID.randomUUID().toString().substring(0, 8), Instant.now()));
        }
        return Collections.emptyList();
    }

    /** 装填一次模拟更新事件（供 dev/test 端点触发，验证知识库自动感知链路） */
    public void armSimulation() {
        simulateNextUpdate.set(true);
    }
}
