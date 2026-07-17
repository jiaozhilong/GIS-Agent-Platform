package com.gisagent.controller;

import com.gisagent.dto.ProjectDto;
import com.gisagent.entity.Project;
import com.gisagent.entity.ProjectDocument;
import com.gisagent.repository.ProjectDocumentRepository;
import com.gisagent.repository.ProjectRepository;
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

    @Value("${storage.upload-dir:./data/uploads}")
    private String uploadDir;

    public ProjectController(ProjectRepository projectRepository,
                             ProjectDocumentRepository documentRepository) {
        this.projectRepository = projectRepository;
        this.documentRepository = documentRepository;
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
