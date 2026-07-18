package com.gisagent.controller;

import com.gisagent.entity.Project;
import com.gisagent.entity.PipelineRun;
import com.gisagent.entity.ProjectVersion;
import com.gisagent.repository.ProjectRepository;
import com.gisagent.repository.PipelineRunRepository;
import com.gisagent.service.ProjectVersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 方案版本管理接口。
 * 路径前缀 /api/projects/{id}/versions
 *  - POST   ""            手动保存当前方案为新版本
 *  - GET    ""            历史版本列表
 *  - GET    "/{vid}"      版本详情（含完整 contextJson）
 *  - POST   "/{vid}/restore" 一键回退到该版本
 */
@RestController
@RequestMapping("/api/projects/{id}/versions")
public class ProjectVersionController {

    private final ProjectVersionService versionService;
    private final ProjectRepository projectRepository;
    private final PipelineRunRepository pipelineRunRepository;

    public ProjectVersionController(ProjectVersionService versionService,
                                    ProjectRepository projectRepository,
                                    PipelineRunRepository pipelineRunRepository) {
        this.versionService = versionService;
        this.projectRepository = projectRepository;
        this.pipelineRunRepository = pipelineRunRepository;
    }

    private boolean owned(Long projectId, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return projectRepository.findById(projectId)
                .filter(p -> p.getUserId().equals(userId)).isPresent();
    }

    /** 手动保存当前方案为新版本 */
    @PostMapping
    public ResponseEntity<?> save(@PathVariable Long id,
                                  @RequestBody SaveRequest req,
                                  Authentication auth) {
        if (!owned(id, auth)) return ResponseEntity.notFound().build();
        // 当前方案来源：最新一次运行的 contextJson，回退到 project.contextJson
        PipelineRun latestRun = pipelineRunRepository.findFirstByProjectIdOrderByIdDesc(id);
        String ctx = latestRun != null ? latestRun.getContextJson() : null;
        if (ctx == null || ctx.isBlank()) {
            Project p = projectRepository.findById(id).orElse(null);
            ctx = p != null ? p.getContextJson() : null;
        }
        if (ctx == null || ctx.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "暂无可保存的方案，请先运行流水线生成"));
        }
        ProjectVersion v = versionService.snapshot(id, ctx, "MANUAL", req.getTitle(), req.getNote());
        if (v == null) return ResponseEntity.badRequest().body(Map.of("error", "保存版本失败：上下文为空"));
        return ResponseEntity.ok(Map.of(
                "id", v.getId(), "versionNo", v.getVersionNo(), "title", v.getTitle()));
    }

    /** 历史版本列表 */
    @GetMapping
    public ResponseEntity<?> list(@PathVariable Long id, Authentication auth) {
        if (!owned(id, auth)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(versionService.list(id));
    }

    /** 版本详情 */
    @GetMapping("/{vid}")
    public ResponseEntity<?> get(@PathVariable Long id, @PathVariable Long vid, Authentication auth) {
        if (!owned(id, auth)) return ResponseEntity.notFound().build();
        Map<String, Object> detail = versionService.get(id, vid);
        if (detail == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(detail);
    }

    /** 一键回退 */
    @PostMapping("/{vid}/restore")
    public ResponseEntity<?> restore(@PathVariable Long id, @PathVariable Long vid, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        if (!owned(id, auth)) return ResponseEntity.notFound().build();
        try {
            ProjectVersion v = versionService.restore(id, vid, userId);
            return ResponseEntity.ok(Map.of("message", "已恢复至版本 v" + v.getVersionNo(), "versionNo", v.getVersionNo()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 手动保存请求体 */
    public static class SaveRequest {
        private String title;
        private String note;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}
