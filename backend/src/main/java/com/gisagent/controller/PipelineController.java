package com.gisagent.controller;

import com.gisagent.dto.ProjectDto;
import com.gisagent.entity.*;
import com.gisagent.export.ExportService;
import com.gisagent.pipeline.PipelineEngine;
import com.gisagent.pipeline.PipelineTool;
import com.gisagent.pipeline.ToolContext;
import com.gisagent.repository.*;
import com.gisagent.service.TeamService;
import com.gisagent.util.EncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/projects")
@Slf4j
public class PipelineController {

    private final ProjectRepository projectRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final ToolExecutionRepository toolExecutionRepository;
    private final LlmProviderRepository llmProviderRepository;
    private final PipelineEngine pipelineEngine;
    private final ExportService exportService;
    private final ExportRepository exportRepository;
    private final TeamService teamService;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PipelineController(ProjectRepository projectRepository,
                              PipelineRunRepository pipelineRunRepository,
                              ToolExecutionRepository toolExecutionRepository,
                              LlmProviderRepository llmProviderRepository,
                              PipelineEngine pipelineEngine,
                              ExportService exportService,
                              ExportRepository exportRepository,
                              TeamService teamService,
                              EncryptionService encryptionService) {
        this.projectRepository = projectRepository;
        this.pipelineRunRepository = pipelineRunRepository;
        this.toolExecutionRepository = toolExecutionRepository;
        this.llmProviderRepository = llmProviderRepository;
        this.pipelineEngine = pipelineEngine;
        this.exportService = exportService;
        this.exportRepository = exportRepository;
        this.teamService = teamService;
        this.encryptionService = encryptionService;
    }

    /** 启动流水线执行 */
    @PostMapping("/{id}/run")
    public ResponseEntity<?> run(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        teamService.requireProjectRole(id, userId, Role.EDITOR);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));

        // 获取用户的默认 LLM Provider
        Optional<LlmProvider> providerOpt = llmProviderRepository.findByUserIdAndIsDefaultTrue(userId).stream().findFirst();
        if (providerOpt.isEmpty()) {
            providerOpt = llmProviderRepository.findByUserId(userId).stream().findFirst();
        }
        if (providerOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请先配置 LLM Provider"));
        }
        LlmProvider provider = providerOpt.get();

        // 创建流水线运行记录
        PipelineRun run = PipelineRun.builder()
                .projectId(id)
                .templateId(project.getTemplateId())
                .status("PENDING")
                .build();
        run = pipelineRunRepository.save(run);

        // 异步执行（MVP 用独立线程，后续用 CompletableFuture / 消息队列）
        PipelineTool.LlmConfig llmConfig = PipelineTool.LlmConfig.of(
                provider.getEndpoint(), encryptionService.decrypt(provider.getApiKeyEncrypted()), defaultModel(provider));
        Long runId = run.getId();
        Long projectId = id;
        String templateId = project.getTemplateId();
        final PipelineRun finalRun = run;
        new Thread(() -> {
            try {
                pipelineEngine.run(runId, projectId, templateId, llmConfig, "AUTO_RUN");
            } catch (Exception e) {
                log.error("流水线执行异常", e);
                finalRun.setStatus("FAILED");
                finalRun.setErrorMessage(e.getMessage());
                finalRun.setFinishedAt(Instant.now());
                pipelineRunRepository.save(finalRun);
            }
        }).start();

        ProjectDto.PipelineStartResponse resp = new ProjectDto.PipelineStartResponse();
        resp.setPipelineRunId(run.getId());
        resp.setProjectId(id);
        resp.setStatus("PENDING");
        return ResponseEntity.ok(resp);
    }

    /** 编辑某个中间产物（更新其 outputJson，并同步回注到本次运行的 contextJson） */
    @PutMapping("/{id}/tools/{execId}")
    public ResponseEntity<?> updateToolOutput(@PathVariable Long id, @PathVariable Long execId,
                                              @RequestBody ProjectDto.ToolOutputUpdateRequest req,
                                              Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        teamService.requireProjectRole(id, userId, Role.EDITOR);

        ToolExecution exec = toolExecutionRepository.findById(execId).orElse(null);
        if (exec == null) return ResponseEntity.notFound().build();
        PipelineRun run = pipelineRunRepository.findById(exec.getPipelineRunId()).orElse(null);
        if (run == null || !run.getProjectId().equals(id)) return ResponseEntity.notFound().build();

        if (req.getOutput() == null || req.getOutput().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "产物内容不能为空"));
        }
        // 解析为合法 JSON（对象/数组）或裸文本（SOLUTION_OUTPUT 的 Markdown）
        String outputJson;
        String trimmed = req.getOutput().trim();
        try {
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                objectMapper.readValue(trimmed, Object.class); // 仅校验
                outputJson = trimmed;
            } else {
                outputJson = objectMapper.writeValueAsString(trimmed); // 裸文本 → JSON 字符串
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "产物内容不是合法 JSON"));
        }

        exec.setOutputJson(outputJson);
        toolExecutionRepository.save(exec);

        // 同步回注到本次运行的 contextJson，使方案预览/下载立即反映编辑
        if (run.getContextJson() != null) {
            ToolContext ctx = parseContext(run.getContextJson());
            pipelineEngine.injectOutput(ctx, exec.getToolType(), outputJson);
            try {
                run.setContextJson(objectMapper.writeValueAsString(ctx.toMap()));
            } catch (Exception e) {
                log.warn("context 序列化失败", e);
            }
            pipelineRunRepository.save(run);
        }

        return ResponseEntity.ok(Map.of("message", "已保存", "execId", execId, "toolType", exec.getToolType()));
    }

    /** 重跑下游：从指定节点的下一节点开始重新执行（该节点本身不重跑，使用其已编辑产物） */
    @PostMapping("/{id}/runs/{runId}/rerun")
    public ResponseEntity<?> rerunDownstream(@PathVariable Long id, @PathVariable Long runId,
                                             @RequestParam int fromOrder, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        teamService.requireProjectRole(id, userId, Role.EDITOR);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));

        PipelineRun run = pipelineRunRepository.findById(runId)
                .filter(r -> r.getProjectId().equals(id)).orElse(null);
        if (run == null) return ResponseEntity.notFound().build();

        Optional<LlmProvider> providerOpt = llmProviderRepository.findByUserIdAndIsDefaultTrue(userId).stream().findFirst();
        if (providerOpt.isEmpty()) providerOpt = llmProviderRepository.findByUserId(userId).stream().findFirst();
        if (providerOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请先配置 LLM Provider"));
        }
        LlmProvider provider = providerOpt.get();
        PipelineTool.LlmConfig llmConfig = PipelineTool.LlmConfig.of(
                provider.getEndpoint(), encryptionService.decrypt(provider.getApiKeyEncrypted()), defaultModel(provider));

        Long pid = id;
        String templateId = project.getTemplateId();
        final PipelineRun finalRun = run;
        new Thread(() -> {
            try {
                pipelineEngine.rerunDownstream(runId, pid, templateId, fromOrder, llmConfig, "AUTO_RUN");
            } catch (Exception e) {
                log.error("下游重跑异常", e);
                finalRun.setStatus("FAILED");
                finalRun.setErrorMessage(e.getMessage());
                finalRun.setFinishedAt(Instant.now());
                pipelineRunRepository.save(finalRun);
            }
        }).start();

        return ResponseEntity.ok(Map.of("message", "下游重跑已启动", "fromOrder", fromOrder, "runId", runId));
    }

    /** 查询流水线状态 */
    @GetMapping("/{id}/status")
    public ResponseEntity<?> status(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        teamService.requireProjectRole(id, userId, Role.VIEWER);

        PipelineRun run = pipelineRunRepository.findFirstByProjectIdOrderByIdDesc(id);
        if (run == null) {
            return ResponseEntity.ok(Map.of("status", "NO_RUN"));
        }

        ProjectDto.PipelineStatusDto dto = new ProjectDto.PipelineStatusDto();
        dto.setPipelineRunId(run.getId());
        dto.setProjectId(id);
        dto.setStatus(run.getStatus());

        List<ProjectDto.ToolStatusDto> tools = new ArrayList<>();
        for (ToolExecution exec : toolExecutionRepository.findByPipelineRunIdOrderByToolOrder(run.getId())) {
            ProjectDto.ToolStatusDto t = new ProjectDto.ToolStatusDto();
            t.setExecId(exec.getId());
            t.setToolType(exec.getToolType());
            t.setToolOrder(exec.getToolOrder());
            t.setStatus(exec.getStatus());
            t.setErrorMessage(exec.getErrorMessage());
            t.setOutput(parseOutput(exec.getOutputJson()));
            tools.add(t);
        }
        dto.setTools(tools);

        if (run.getContextJson() != null) {
            dto.setContext(parseContext(run.getContextJson()));
        }

        return ResponseEntity.ok(dto);
    }

    /** 下载 Markdown */
    @GetMapping("/{id}/download/md")
    public ResponseEntity<?> downloadMd(@PathVariable Long id, Authentication auth) {
        return download(id, "MD", auth);
    }

    /** 下载 Word */
    @GetMapping("/{id}/download/docx")
    public ResponseEntity<?> downloadDocx(@PathVariable Long id, Authentication auth) {
        return download(id, "DOCX", auth);
    }

    /** 下载 PPT（Tool-9 方案输出，导出为后处理，与 MD/DOCX 一致） */
    @GetMapping("/{id}/download/pptx")
    public ResponseEntity<?> downloadPptx(@PathVariable Long id, Authentication auth) {
        return download(id, "PPTX", auth);
    }

    private ResponseEntity<?> download(Long id, String type, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        teamService.requireProjectRole(id, userId, Role.VIEWER);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));

        PipelineRun run = pipelineRunRepository.findFirstByProjectIdOrderByIdDesc(id);
        if (run == null || run.getContextJson() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "请先执行流水线生成方案"));
        }

        ToolContext context = parseContext(run.getContextJson());
        String filePath;
        String fileName;
        if ("MD".equals(type)) {
            filePath = exportService.exportMarkdown(id, project.getName(), context);
            fileName = new File(filePath).getName();
        } else if ("PPTX".equals(type)) {
            filePath = exportService.exportPptx(id, project.getName(), context);
            fileName = new File(filePath).getName();
        } else {
            filePath = exportService.exportDocx(id, project.getName(), context);
            fileName = new File(filePath).getName();
        }

        // 记录导出
        File f = new File(filePath);
        exportRepository.save(Export.builder()
                .projectId(id)
                .fileType(type)
                .fileName(fileName)
                .filePath(filePath)
                .fileSize(f.length())
                .build());

        try {
            byte[] data = Files.readAllBytes(f.toPath());
            String contentType;
            if ("MD".equals(type)) {
                contentType = "text/markdown; charset=utf-8";
            } else if ("PPTX".equals(type)) {
                contentType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            } else {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "文件读取失败"));
        }
    }

    private String defaultModel(LlmProvider provider) {
        if (provider.getModel() != null && !provider.getModel().isBlank()) {
            return provider.getModel();
        }
        // 兜底：根据 endpoint 推断常见默认模型
        String ep = provider.getEndpoint() == null ? "" : provider.getEndpoint().toLowerCase();
        if (ep.contains("deepseek")) return "deepseek-chat";
        if (ep.contains("openai.com")) return "gpt-4o";
        if (ep.contains("qwen") || ep.contains("dashscope")) return "qwen-max";
        return "gpt-4o";
    }

    private Object parseOutput(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private ToolContext parseContext(String json) {
        try {
            return objectMapper.readValue(json, ToolContext.class);
        } catch (Exception e) {
            return ToolContext.empty();
        }
    }
}
