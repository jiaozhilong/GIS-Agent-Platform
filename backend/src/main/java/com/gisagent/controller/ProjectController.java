package com.gisagent.controller;

import com.gisagent.dto.ProjectDto;
import com.gisagent.entity.Project;
import com.gisagent.entity.ProjectDocument;
import com.gisagent.entity.PipelineRun;
import com.gisagent.repository.ProjectDocumentRepository;
import com.gisagent.repository.ProjectRepository;
import com.gisagent.repository.PipelineRunRepository;
import com.gisagent.repository.ToolExecutionRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository documentRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final ToolExecutionRepository toolExecutionRepository;

    @Value("${storage.upload-dir:./data/uploads}")
    private String uploadDir;

    public ProjectController(ProjectRepository projectRepository,
                             ProjectDocumentRepository documentRepository,
                             PipelineRunRepository pipelineRunRepository,
                             ToolExecutionRepository toolExecutionRepository) {
        this.projectRepository = projectRepository;
        this.documentRepository = documentRepository;
        this.pipelineRunRepository = pipelineRunRepository;
        this.toolExecutionRepository = toolExecutionRepository;
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("templateId") String templateId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        Long userId = (Long) auth.getPrincipal();

        // 1. 保存项目
        Project project = Project.builder()
                .userId(userId)
                .name(name)
                .description(description)
                .templateId(templateId)
                .status("DRAFT")
                .build();
        project = projectRepository.save(project);

        // 2. 保存上传文件
        if (file != null && !file.isEmpty()) {
            try {
                String dir = uploadDir + "/" + userId;
                new File(dir).mkdirs();
                String originalName = file.getOriginalFilename();
                String storedName = UUID.randomUUID() + "_" + originalName;
                File target = new File(dir, storedName);
                file.transferTo(target);

                String fileType = inferType(originalName);
                ProjectDocument doc = ProjectDocument.builder()
                        .projectId(project.getId())
                        .fileName(originalName)
                        .filePath(target.getAbsolutePath())
                        .fileType(fileType)
                        .fileSize(file.getSize())
                        .build();
                documentRepository.save(doc);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件保存失败: " + e.getMessage()));
            }
        }

        return ResponseEntity.ok(toResponse(project));
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(projectRepository.findByUserId(userId).stream().map(this::toResponse));
    }

    /** 项目详情：基本信息 + 文档列表 + 最近一次流水线运行 */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Project project = projectRepository.findById(id)
                .filter(p -> p.getUserId().equals(userId))
                .orElse(null);
        if (project == null) return ResponseEntity.notFound().build();

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", project.getId());
        result.put("name", project.getName());
        result.put("description", project.getDescription());
        result.put("templateId", project.getTemplateId());
        result.put("status", project.getStatus());
        result.put("createdAt", project.getCreatedAt() != null ? project.getCreatedAt().toString() : null);
        result.put("kbDirty", Boolean.TRUE.equals(project.getKbDirty()));
        result.put("kbDirtyNote", project.getKbDirtyNote());
        result.put("kbDirtySince", project.getKbDirtySince() != null ? project.getKbDirtySince().toString() : null);

        result.put("documents", documentRepository.findByProjectId(id).stream().map(d -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", d.getId());
            m.put("fileName", d.getFileName());
            m.put("fileType", d.getFileType());
            m.put("fileSize", d.getFileSize());
            return m;
        }).toList());

        PipelineRun run = pipelineRunRepository.findFirstByProjectIdOrderByIdDesc(id);
        if (run != null) {
            Map<String, Object> runMap = new java.util.HashMap<>();
            runMap.put("id", run.getId());
            runMap.put("status", run.getStatus());
            runMap.put("tools", toolExecutionRepository.findByPipelineRunIdOrderByToolOrder(run.getId()).stream().map(t -> {
                Map<String, Object> m = new java.util.HashMap<>();
                m.put("toolType", t.getToolType());
                m.put("status", t.getStatus());
                m.put("toolOrder", t.getToolOrder());
                return m;
            }).toList());
            result.put("latestRun", runMap);
        }

        return ResponseEntity.ok(result);
    }

    private ProjectDto.ProjectResponse toResponse(Project p) {
        ProjectDto.ProjectResponse r = new ProjectDto.ProjectResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setDescription(p.getDescription());
        r.setTemplateId(p.getTemplateId());
        r.setStatus(p.getStatus());
        r.setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        return r;
    }

    private String inferType(String fileName) {
        if (fileName == null) return "txt";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".docx")) return "DOCX";
        if (lower.endsWith(".doc")) return "DOC";
        return "TXT";
    }
}
