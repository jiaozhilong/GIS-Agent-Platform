package com.gisagent.controller;

import com.gisagent.dto.ProjectDto;
import com.gisagent.entity.Project;
import com.gisagent.entity.ProjectDocument;
import com.gisagent.entity.PipelineRun;
import com.gisagent.entity.Role;
import com.gisagent.entity.TeamMember;
import com.gisagent.repository.ProjectDocumentRepository;
import com.gisagent.repository.ProjectRepository;
import com.gisagent.repository.PipelineRunRepository;
import com.gisagent.repository.TeamMemberRepository;
import com.gisagent.repository.ToolExecutionRepository;
import com.gisagent.service.TeamService;
import com.gisagent.service.AuditService;
import com.gisagent.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@Slf4j
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final ProjectDocumentRepository documentRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final ToolExecutionRepository toolExecutionRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamService teamService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Value("${storage.upload-dir:./data/uploads}")
    private String uploadDir;

    @PostConstruct
    public void init() {
        // 解析为绝对路径并确保目录存在：避免 MultipartFile.transferTo 对相对路径
        // 解析到 Tomcat 临时目录导致保存失败时目录不存在（FileNotFoundException）。
        this.uploadDir = new File(uploadDir).getAbsolutePath();
        new File(uploadDir).mkdirs();
    }

    public ProjectController(ProjectRepository projectRepository,
                             ProjectDocumentRepository documentRepository,
                             PipelineRunRepository pipelineRunRepository,
                             ToolExecutionRepository toolExecutionRepository,
                             TeamMemberRepository teamMemberRepository,
                             TeamService teamService,
                             AuditService auditService,
                             NotificationService notificationService) {
        this.projectRepository = projectRepository;
        this.documentRepository = documentRepository;
        this.pipelineRunRepository = pipelineRunRepository;
        this.toolExecutionRepository = toolExecutionRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamService = teamService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("templateId") String templateId,
            @RequestParam(value = "teamId", required = false) Long teamId,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {

        Long userId = (Long) auth.getPrincipal();

        // 若归属团队，需为该团队成员且具备 EDITOR 及以上角色方可创建项目
        if (teamId != null) {
            teamService.requireTeamRole(teamId, userId, Role.EDITOR);
        }

        // 1. 保存项目
        Project project = Project.builder()
                .userId(userId)
                .teamId(teamId)
                .organizationId(com.gisagent.config.TenantContext.getOrganizationId())
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

        auditService.log(userId, null, "CREATE_PROJECT", "PROJECT", project.getId(),
                "{\"name\":\"" + project.getName() + "\",\"templateId\":\"" + templateId + "\"}", null);

        return ResponseEntity.ok(toResponse(project));
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Long org = com.gisagent.config.TenantContext.getOrganizationId();
        List<Project> projects;
        if (org != null) {
            // 多租户：仅返回当前组织内、属于自己或所属团队的项目
            projects = new ArrayList<>(projectRepository.findByOrganizationIdAndUserId(org, userId));
            List<TeamMember> memberships = teamMemberRepository.findByUserId(userId);
            if (!memberships.isEmpty()) {
                List<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).toList();
                projects.addAll(projectRepository.findByOrganizationIdAndTeamIdIn(org, teamIds));
            }
        } else {
            projects = new ArrayList<>(projectRepository.findByUserId(userId));
            List<TeamMember> memberships = teamMemberRepository.findByUserId(userId);
            if (!memberships.isEmpty()) {
                List<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).toList();
                projects.addAll(projectRepository.findByTeamIdIn(teamIds));
            }
        }
        return ResponseEntity.ok(projects.stream().map(this::toResponse));
    }

    /** 项目详情：基本信息 + 文档列表 + 最近一次流水线运行 */
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        teamService.requireProjectRole(id, userId, Role.VIEWER);
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));

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
        r.setTeamId(p.getTeamId());
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

    // ---- P5-2 PPT 品牌模板上传 ----

    @Value("${storage.pptx-template-dir:./data/templates}")
    private String pptxTemplateDir;

    @PostMapping("/ppt-template")
    public ResponseEntity<?> uploadPptTemplate(@RequestParam("file") MultipartFile file, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pptx")) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 .pptx 格式的 PPT 模板"));
        }

        try {
            File dir = new File(pptxTemplateDir);
            if (!dir.isAbsolute()) {
                dir = new File(System.getProperty("user.dir"), pptxTemplateDir);
            }
            dir.mkdirs();
            File dest = new File(dir, "brand-template.pptx");
            file.transferTo(dest);
            log.info("PPT 品牌模板已上传: {}", dest.getAbsolutePath());
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "path", dest.getAbsolutePath(),
                    "size", file.getSize()
            ));
        } catch (Exception e) {
            log.error("PPT 模板上传失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }
}
