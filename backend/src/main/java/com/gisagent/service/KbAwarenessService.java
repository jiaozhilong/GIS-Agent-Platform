package com.gisagent.service;

import com.gisagent.connector.IMAKnowledgeBaseConnector;
import com.gisagent.entity.*;
import com.gisagent.pipeline.PipelineEngine;
import com.gisagent.pipeline.PipelineTool;
import com.gisagent.util.EncryptionService;
import com.gisagent.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库自动感知服务（P3-1）。
 * 周期性调用 IMA 的 getUpdates(kbId, since) 拉取增量事件；
 * 一旦发现新增/修改/删除，便将该用户下所有项目标记为 kbDirty（知识库有更新），
 * 并推进读取游标。前端据此提示"知识库已更新，可重生成"，重生成时清除脏标记并重新跑流水线。
 */
@Service
@Slf4j
public class KbAwarenessService {

    private final ImaKbConfigRepository imaKbConfigRepository;
    private final ProjectRepository projectRepository;
    private final KbSyncStateRepository kbSyncStateRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final LlmProviderRepository llmProviderRepository;
    private final PipelineEngine pipelineEngine;
    private final IMAKnowledgeBaseConnector imaConnector;
    private final EncryptionService encryptionService;

    public KbAwarenessService(ImaKbConfigRepository imaKbConfigRepository,
                              ProjectRepository projectRepository,
                              KbSyncStateRepository kbSyncStateRepository,
                              PipelineRunRepository pipelineRunRepository,
                              LlmProviderRepository llmProviderRepository,
                              PipelineEngine pipelineEngine,
                              IMAKnowledgeBaseConnector imaConnector,
                              EncryptionService encryptionService) {
        this.imaKbConfigRepository = imaKbConfigRepository;
        this.projectRepository = projectRepository;
        this.kbSyncStateRepository = kbSyncStateRepository;
        this.pipelineRunRepository = pipelineRunRepository;
        this.llmProviderRepository = llmProviderRepository;
        this.pipelineEngine = pipelineEngine;
        this.imaConnector = imaConnector;
        this.encryptionService = encryptionService;
    }

    /** 全量同步：遍历所有配置了 IMA 知识库的用户 */
    public SyncResult syncAll() {
        List<Long> userIds = imaKbConfigRepository.findAll().stream()
                .map(ImaKbConfig::getUserId).distinct().collect(Collectors.toList());
        int totalEvents = 0, touchedUsers = 0, dirtyProjects = 0;
        for (Long userId : userIds) {
            SyncResult r = syncUser(userId);
            touchedUsers++;
            totalEvents += r.eventCount;
            dirtyProjects += r.dirtyProjectCount;
        }
        return new SyncResult(touchedUsers, totalEvents, dirtyProjects);
    }

    /** 同步单个用户的所有知识库；有更新则标脏其全部项目 */
    @Transactional
    public SyncResult syncUser(Long userId) {
        List<ImaKbConfig> configs = imaKbConfigRepository.findByUserId(userId).stream()
                .filter(ImaKbConfig::getEnabled).collect(Collectors.toList());
        int eventCount = 0;
        Map<String, List<String>> changedByKb = new LinkedHashMap<>();
        for (ImaKbConfig cfg : configs) {
            KbSyncState state = kbSyncStateRepository.findByUserIdAndKbId(userId, cfg.getKbId())
                    .orElseGet(() -> KbSyncState.builder().userId(userId).kbId(cfg.getKbId())
                            .lastCursor(Instant.EPOCH).build());
            Instant since = state.getLastCursor() == null ? Instant.EPOCH : state.getLastCursor();
            List<IMAKnowledgeBaseConnector.KBUpdateEvent> events;
            try {
                events = imaConnector.getUpdates(cfg.getKbId(), since);
            } catch (Exception e) {
                log.warn("拉取知识库更新失败 kbId={}: {}", cfg.getKbId(), e.getMessage());
                events = List.of();
            }
            if (events != null && !events.isEmpty()) {
                eventCount += events.size();
                Instant maxTs = events.stream().map(IMAKnowledgeBaseConnector.KBUpdateEvent::timestamp)
                        .max(Instant::compareTo).orElse(Instant.now());
                state.setLastCursor(maxTs);
                List<String> descs = events.stream().map(e -> e.eventType() + ":" + e.docId()).collect(Collectors.toList());
                changedByKb.put(cfg.getKbName() + "(" + cfg.getKbId() + ")", descs);
            }
            state.setLastSyncAt(Instant.now());
            kbSyncStateRepository.save(state);
        }

        int dirtyProjectCount = 0;
        if (!changedByKb.isEmpty()) {
            String note = "知识库更新：" + changedByKb.entrySet().stream()
                    .map(e -> e.getKey() + " [" + String.join(", ", e.getValue()) + "]")
                    .collect(Collectors.joining("；"));
            List<Project> projects = projectRepository.findByUserId(userId);
            for (Project p : projects) {
                if (Boolean.FALSE.equals(p.getKbDirty())) {
                    p.setKbDirty(true);
                    p.setKbDirtyNote(note);
                    p.setKbDirtySince(Instant.now());
                    projectRepository.save(p);
                    dirtyProjectCount++;
                }
            }
            log.info("用户 {} 知识库有更新，标记 {} 个项目待重生成", userId, dirtyProjectCount);
        }
        return new SyncResult(1, eventCount, dirtyProjectCount);
    }

    /** 用最新知识库重生成：清除脏标记并重新跑流水线 */
    public void regenWithLatestKb(Long projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return;
        project.setKbDirty(false);
        project.setKbDirtyNote(null);
        project.setKbDirtySince(null);
        projectRepository.save(project);

        Optional<LlmProvider> providerOpt = llmProviderRepository.findByUserIdAndIsDefaultTrue(project.getUserId()).stream().findFirst();
        if (providerOpt.isEmpty()) providerOpt = llmProviderRepository.findByUserId(project.getUserId()).stream().findFirst();
        if (providerOpt.isEmpty()) {
            log.warn("未配置 LLM Provider，无法重生成 project={}", projectId);
            return;
        }
        LlmProvider provider = providerOpt.get();
        PipelineTool.LlmConfig llmConfig = PipelineTool.LlmConfig.of(
                provider.getEndpoint(), encryptionService.decrypt(provider.getApiKeyEncrypted()), defaultModel(provider));

        PipelineRun run = PipelineRun.builder()
                .projectId(projectId).templateId(project.getTemplateId()).status("PENDING").build();
        run = pipelineRunRepository.save(run);
        Long runId = run.getId();
        String templateId = project.getTemplateId();
        new Thread(() -> {
            try {
                pipelineEngine.run(runId, projectId, templateId, llmConfig, "KB_RERUN", null);
            } catch (Exception e) {
                log.error("知识库重生成执行异常", e);
            }
        }).start();
        log.info("已用最新知识库触发重生成 project={}, run={}", projectId, runId);
    }

    private String defaultModel(LlmProvider provider) {
        if (provider.getModel() != null && !provider.getModel().isBlank()) return provider.getModel();
        String ep = provider.getEndpoint() == null ? "" : provider.getEndpoint().toLowerCase();
        if (ep.contains("deepseek")) return "deepseek-chat";
        if (ep.contains("openai.com")) return "gpt-4o";
        if (ep.contains("qwen") || ep.contains("dashscope")) return "qwen-max";
        return "gpt-4o";
    }

    public record SyncResult(int userCount, int eventCount, int dirtyProjectCount) {}

    /** 查询项目（供控制器做归属校验） */
    public Project findProject(Long projectId) {
        return projectRepository.findById(projectId).orElse(null);
    }
}
