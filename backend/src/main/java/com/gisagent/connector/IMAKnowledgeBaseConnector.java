package com.gisagent.connector;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * IMA 知识库连接器接口。
 * Phase 1 MVP 使用 Mock 实现，真实 IMA MCP 接口就绪后切换。
 */
public interface IMAKnowledgeBaseConnector {

    /**
     * 验证知识库连接是否正常。
     *
     * @param kbId       知识库 ID
     * @param credential 访问凭证
     * @return true 连接成功
     */
    boolean testConnection(String kbId, String credential);

    /**
     * 检索知识库。
     *
     * @param kbId    知识库 ID
     * @param query   检索查询
     * @param options 检索选项（topK、相似度阈值等）
     * @return 检索结果列表
     */
    List<SearchResult> search(String kbId, String query, SearchOptions options);

    /**
     * 获取知识库元信息。
     *
     * @param kbId 知识库 ID
     * @return 知识库信息
     */
    KBInfo getKBInfo(String kbId);

    /**
     * 获取知识库更新事件（用于自动感知更新）。
     *
     * @param kbId  知识库 ID
     * @param since 起始时间
     * @return 更新事件列表
     */
    List<KBUpdateEvent> getUpdates(String kbId, Instant since);

    // ---- 内部数据类 ----

    record SearchOptions(int topK, double minScore, String purpose) {}

    record SearchResult(
            String docId,
            String title,
            String content,
            double score,
            String source,
            Instant updatedAt
    ) {}

    record KBInfo(
            String kbId,
            String kbName,
            String kbType,
            long docCount,
            Instant lastUpdated
    ) {}

    record KBUpdateEvent(
            String eventType,  // ADDED | MODIFIED | DELETED
            String docId,
            Instant timestamp
    ) {}
}
