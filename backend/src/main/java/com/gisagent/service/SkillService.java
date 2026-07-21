package com.gisagent.service;

import com.gisagent.entity.Skill;
import com.gisagent.pipeline.ToolContext;
import com.gisagent.pipeline.PipelineTool;
import com.gisagent.repository.SkillRepository;
import com.gisagent.util.EncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Skill 业务能力：CRUD 辅助 + API_ENDPOINT 类型 Skill 的远程调用。
 * GIT_REPO 类型暂仅支持配置（Phase 2 沙箱执行），executeSkill 会明确抛错提示。
 */
@Service
@Slf4j
public class SkillService {

    private static final String TYPE_API = "API_ENDPOINT";
    private static final String TYPE_GIT = "GIT_REPO";

    private final SkillRepository skillRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    public SkillService(SkillRepository skillRepository, EncryptionService encryptionService) {
        this.skillRepository = skillRepository;
        this.encryptionService = encryptionService;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(60000);
        this.restTemplate = new RestTemplate(factory);
    }

    public List<Skill> listByOwner(Long ownerId) {
        return skillRepository.findByOwnerId(ownerId);
    }

    public List<Skill> listEnabledByOwner(Long ownerId) {
        return skillRepository.findByOwnerIdAndEnabledTrue(ownerId);
    }

    /** 解析某工具节点当前生效的 Skill（已启用 + API 端点可用） */
    public Skill resolveForTool(String toolType) {
        return skillRepository
                .findFirstByToolTypeAndEnabledTrueAndType(toolType, TYPE_API)
                .orElse(null);
    }

    /**
     * 调用 API_ENDPOINT 类型 Skill，返回其产出 JSON 字符串（与对应工具节点的 outputJson 同构）。
     */
    public String executeSkill(Skill skill, String toolType, ToolContext context, PipelineTool.LlmConfig llmConfig) {
        if (skill == null || !TYPE_API.equals(skill.getType())) {
            throw new IllegalArgumentException("仅支持 API_ENDPOINT 类型 Skill 的运行时执行");
        }
        if (skill.getEndpointUrl() == null || skill.getEndpointUrl().isBlank()) {
            throw new IllegalArgumentException("Skill 未配置 endpoint_url");
        }
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("toolType", toolType);
            body.put("context", context.toMap());
            String ep = llmConfig.endpoint == null ? "" : llmConfig.endpoint;
            String md = llmConfig.model == null ? "" : llmConfig.model;
            body.put("llmConfig", Map.of("endpoint", ep, "model", md));
            if (skill.getRequestTemplate() != null && !skill.getRequestTemplate().isBlank()) {
                body.put("requestTemplate", skill.getRequestTemplate());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (skill.getApiKeyEncrypted() != null && !skill.getApiKeyEncrypted().isBlank()) {
                String key = encryptionService.decrypt(skill.getApiKeyEncrypted());
                headers.set("X-Skill-Api-Key", key);
            }
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
            String resp = restTemplate.postForEntity(skill.getEndpointUrl(), req, String.class).getBody();
            return resp != null ? resp : "";
        } catch (Exception e) {
            log.warn("[Skill] 调用失败 skillId={} toolType={}: {}", skill.getId(), toolType, e.getMessage());
            throw new RuntimeException("Skill 调用失败: " + e.getMessage(), e);
        }
    }

    /** 测试 Skill 连通性（API_ENDPOINT 发一次最小请求） */
    public boolean testSkill(Skill skill) {
        if (TYPE_GIT.equals(skill.getType())) {
            return skill.getGitRepoUrl() != null && !skill.getGitRepoUrl().isBlank();
        }
        if (skill.getEndpointUrl() == null || skill.getEndpointUrl().isBlank()) return false;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (skill.getApiKeyEncrypted() != null && !skill.getApiKeyEncrypted().isBlank()) {
                headers.set("X-Skill-Api-Key", encryptionService.decrypt(skill.getApiKeyEncrypted()));
            }
            HttpEntity<String> req = new HttpEntity<>("{\"toolType\":\"__ping__\"}", headers);
            restTemplate.postForEntity(skill.getEndpointUrl(), req, String.class);
            return true;
        } catch (Exception e) {
            log.warn("[Skill] 测试失败 skillId={}: {}", skill.getId(), e.getMessage());
            return false;
        }
    }
}
