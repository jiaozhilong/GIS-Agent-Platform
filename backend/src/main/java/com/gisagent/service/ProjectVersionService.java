package com.gisagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.entity.Project;
import com.gisagent.entity.ProjectVersion;
import com.gisagent.entity.PipelineRun;
import com.gisagent.repository.ProjectRepository;
import com.gisagent.repository.ProjectVersionRepository;
import com.gisagent.repository.PipelineRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 方案版本管理：快照生成、历史列表、详情查看、一键回退。
 */
@Service
@Slf4j
public class ProjectVersionService {

    private final ProjectVersionRepository versionRepo;
    private final ProjectRepository projectRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProjectVersionService(ProjectVersionRepository versionRepo,
                                 ProjectRepository projectRepository,
                                 PipelineRunRepository pipelineRunRepository) {
        this.versionRepo = versionRepo;
        this.projectRepository = projectRepository;
        this.pipelineRunRepository = pipelineRunRepository;
    }

    /** 触发类型对应的默认标题后缀 */
    private static String triggerLabel(String triggerType) {
        if ("KB_RERUN".equals(triggerType)) return "知识库重生成";
        if ("MANUAL".equals(triggerType)) return "手动保存";
        return "自动生成";
    }

    /**
     * 保存一次版本快照（自动或手动）。无有效 context 时返回 null。
     */
    @Transactional
    public ProjectVersion snapshot(Long projectId, String contextJson, String triggerType,
                                   String title, String note) {
        if (contextJson == null || contextJson.isBlank()) {
            log.warn("跳过空上下文的版本快照 projectId={}", projectId);
            return null;
        }
        Integer max = versionRepo.maxVersionNo(projectId);
        int next = (max == null ? 0 : max) + 1;
        String sol = extractSolutionText(contextJson);
        String t = (title != null && !title.isBlank()) ? title : "v" + next + " · " + triggerLabel(triggerType);
        ProjectVersion v = ProjectVersion.builder()
                .projectId(projectId)
                .versionNo(next)
                .title(t)
                .triggerType(triggerType)
                .contextJson(contextJson)
                .solutionText(sol)
                .note(note)
                .build();
        return versionRepo.save(v);
    }

    /** 历史列表（轻量：含方案正文预览，不含完整 contextJson） */
    public List<Map<String, Object>> list(Long projectId) {
        return versionRepo.findByProjectIdOrderByIdDesc(projectId).stream().map(v -> {
            String preview = v.getSolutionText();
            if (preview != null && preview.length() > 240) preview = preview.substring(0, 240) + "…";
            return Map.<String, Object>of(
                    "id", v.getId(),
                    "versionNo", v.getVersionNo(),
                    "title", v.getTitle(),
                    "triggerType", v.getTriggerType(),
                    "note", v.getNote(),
                    "createdAt", v.getCreatedAt() != null ? v.getCreatedAt().toString() : null,
                    "solutionPreview", preview
            );
        }).toList();
    }

    /** 版本详情（含完整 contextJson，供查看/对比） */
    public Map<String, Object> get(Long projectId, Long versionId) {
        return versionRepo.findByProjectIdAndId(projectId, versionId)
                .map(v -> Map.<String, Object>of(
                        "id", v.getId(),
                        "versionNo", v.getVersionNo(),
                        "title", v.getTitle(),
                        "triggerType", v.getTriggerType(),
                        "note", v.getNote(),
                        "createdAt", v.getCreatedAt() != null ? v.getCreatedAt().toString() : null,
                        "solutionText", v.getSolutionText(),
                        "contextJson", v.getContextJson()
                ))
                .orElse(null);
    }

    /**
     * 一键回退：把指定版本的 contextJson 写回项目与最新一次运行，
     * 使详情页预览与下载立即反映该历史版本（不重新调用 LLM）。
     */
    @Transactional
    public ProjectVersion restore(Long projectId, Long versionId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getUserId().equals(userId)).orElse(null);
        if (project == null) throw new IllegalArgumentException("项目不存在或无权限");

        ProjectVersion v = versionRepo.findByProjectIdAndId(projectId, versionId).orElse(null);
        if (v == null) throw new IllegalArgumentException("版本不存在");

        project.setContextJson(v.getContextJson());
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);

        // 同步到最新一次运行，详情页从 latestRun.contextJson 读取方案
        PipelineRun latest = pipelineRunRepository.findFirstByProjectIdOrderByIdDesc(projectId);
        if (latest != null) {
            latest.setContextJson(v.getContextJson());
            pipelineRunRepository.save(latest);
        }
        log.info("版本回退: projectId={}, 回到 v{}", projectId, v.getVersionNo());
        return v;
    }

    /** 从 Context Bus JSON 中提取方案正文（solutionText） */
    private String extractSolutionText(String contextJson) {
        try {
            JsonNode root = objectMapper.readTree(contextJson);
            JsonNode sol = root.path("solutionText");
            return sol.isMissingNode() || sol.isNull() ? null : sol.asText();
        } catch (Exception e) {
            return null;
        }
    }
}
