package com.gisagent.service;

import com.gisagent.connector.IMAKnowledgeBaseConnector;
import com.gisagent.connector.IMAKnowledgeBaseConnector.ImaAuth;
import com.gisagent.connector.IMAKnowledgeBaseConnector.SearchOptions;
import com.gisagent.connector.IMAKnowledgeBaseConnector.SearchResult;
import com.gisagent.entity.ImaCredential;
import com.gisagent.entity.ImaKbConfig;
import com.gisagent.repository.ImaCredentialRepository;
import com.gisagent.repository.ImaKbConfigRepository;
import com.gisagent.util.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 按用户隔离的 IMA 检索编排。
 * 给定 userId + purpose：加载该用户的加密凭证与启用中的知识库配置，使用用户自己的凭证检索，
 * 聚合多个知识库结果。无凭证时返回空（不报错），保证流水线其它节点不受影响。
 */
@Service
public class ImaSearchService {

    private static final Logger log = LoggerFactory.getLogger(ImaSearchService.class);

    private final ImaCredentialRepository credentialRepository;
    private final ImaKbConfigRepository kbConfigRepository;
    private final IMAKnowledgeBaseConnector imaConnector;
    private final EncryptionService encryptionService;

    public ImaSearchService(ImaCredentialRepository credentialRepository,
                            ImaKbConfigRepository kbConfigRepository,
                            IMAKnowledgeBaseConnector imaConnector,
                            EncryptionService encryptionService) {
        this.credentialRepository = credentialRepository;
        this.kbConfigRepository = kbConfigRepository;
        this.imaConnector = imaConnector;
        this.encryptionService = encryptionService;
    }

    private ImaAuth toAuth(ImaCredential c) {
        if (c == null) return null;
        String cid = c.getClientIdEncrypted() != null ? encryptionService.decrypt(c.getClientIdEncrypted()) : null;
        String key = c.getApiKeyEncrypted() != null ? encryptionService.decrypt(c.getApiKeyEncrypted()) : null;
        return new ImaAuth(cid, key, c.getBaseUrl());
    }

    /** 测试某用户的 IMA 连接（用该用户自己的凭证） */
    public boolean testConnection(Long userId, String kbId) {
        if (userId == null) return false;
        ImaAuth auth = toAuth(credentialRepository.findByUserId(userId).orElse(null));
        if (auth == null) {
            log.warn("[IMA] 用户 {} 未配置 IMA 凭证，无法测试连接", userId);
            return false;
        }
        return imaConnector.testConnection(kbId != null ? kbId : "kb-default", auth);
    }

    /**
     * 按用户 + 用途检索 IMA。
     *
     * @param userId  当前用户
     * @param purpose 用途过滤（product_doc / case_lib / competitor / ...），null 表示不过滤
     * @param query   查询
     * @param topK    每个知识库取前 N 条
     */
    public String retrieve(Long userId, String purpose, String query, int topK) {
        if (userId == null) {
            log.warn("[IMA] ToolContext 缺少 userId，跳过检索");
            return "（未配置 IMA 凭证）";
        }
        ImaAuth auth = toAuth(credentialRepository.findByUserId(userId).orElse(null));
        if (auth == null) {
            log.warn("[IMA] 用户 {} 未配置 IMA 凭证，跳过检索", userId);
            return "（未配置 IMA 凭证）";
        }
        List<ImaKbConfig> configs = kbConfigRepository.findByUserIdAndEnabledTrue(userId);
        if (purpose != null) {
            configs = configs.stream().filter(c -> purpose.equals(c.getPurpose())).toList();
        }
        if (configs.isEmpty()) {
            return "（无启用的 IMA 知识库）";
        }
        StringBuilder sb = new StringBuilder();
        int perKb = Math.max(1, topK);
        for (ImaKbConfig cfg : configs) {
            try {
                List<SearchResult> results = imaConnector.search(cfg.getKbId(), query,
                        new SearchOptions(perKb, 0.5, cfg.getPurpose()), auth);
                for (SearchResult r : results) {
                    sb.append("- ").append(r.title()).append(": ").append(r.content()).append("\n");
                }
            } catch (Exception e) {
                log.warn("[IMA] 知识库 {} 检索失败: {}", cfg.getKbId(), e.getMessage());
            }
        }
        return sb.toString().isBlank() ? "（无检索结果）" : sb.toString();
    }

    /** 检索该用户全部启用的知识库（不过滤用途） */
    public String retrieveAll(Long userId, String query, int topK) {
        return retrieve(userId, null, query, topK);
    }
}
