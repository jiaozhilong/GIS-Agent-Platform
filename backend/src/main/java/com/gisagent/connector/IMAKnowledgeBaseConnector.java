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
     * IMA 访问凭证（按用户隔离）。实际检索/连通性测试均以此为准，连接器保持无状态。
     */
    record ImaAuth(String clientId, String apiKey, String baseUrl) {}

    /**
     * 验证知识库连接是否正常。
     *
     * @param kbId 知识库 ID（仅作结果标签）
     * @param auth 访问凭证（按用户）
     * @return true 连接成功
     */
    boolean testConnection(String kbId, ImaAuth auth);

    /**
     * 检索知识库。
     *
     * @param kbId    知识库 ID（仅作结果标签）
     * @param query   检索查询
     * @param options 检索选项（topK、相似度阈值等）
     * @param auth    访问凭证（按用户）
     * @return 检索结果列表
     */
    List<SearchResult> search(String kbId, String query, SearchOptions options, ImaAuth auth);

    /**
     * 获取知识库元信息。
     *
     * @param kbId 知识库 ID
     * @return 知识库信息
     */
    KBInfo getKBInfo(String kbId);

    /**
     * 拉取该用户 IMA 账号下可访问的知识库列表（订阅 + 自建）。
     * 用于前端「从 IMA 拉取」后由用户勾选启用哪些库。
     *
     * @param auth 访问凭证（按用户）
     * @return 知识库信息列表（可能为空）
     */
    List<KBInfo> listKnowledgeBases(ImaAuth auth);

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
