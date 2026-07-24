package com.gisagent.controller;

import com.gisagent.dto.PptTemplateDto;
import com.gisagent.entity.PptTemplate;
import com.gisagent.repository.PptTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ppt-templates")
@Slf4j
public class PptTemplateController {

    private final PptTemplateRepository pptTemplateRepository;

    @Value("${storage.pptx-template-dir:./data/ppt-templates}")
    private String pptxTemplateDir;

    public PptTemplateController(PptTemplateRepository pptTemplateRepository) {
        this.pptTemplateRepository = pptTemplateRepository;
    }

    /** 上传 PPT 模板 */
    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "name", required = false) String name,
                                    @RequestParam(value = "description", required = false) String description,
                                    Authentication auth) {
        Long userId = extractUserId(auth);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "未登录"));
        }
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pptx")) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 .pptx 格式的 PPT 模板"));
        }
        try {
            // 解析存储目录
            File dir = new File(pptxTemplateDir);
            if (!dir.isAbsolute()) {
                dir = new File(System.getProperty("user.dir"), pptxTemplateDir);
            }
            File userDir = new File(dir, String.valueOf(userId));
            userDir.mkdirs();

            // 唯一文件名
            String storedName = System.currentTimeMillis() + "_" + originalName.replaceAll("[^a-zA-Z0-9.\\-\\u4e00-\\u9fa5]", "_");
            File dest = new File(userDir, storedName);
            file.transferTo(dest);

            String templateName = (name != null && !name.isBlank()) ? name : originalName.replaceAll("\\.pptx$", "");
            boolean isFirst = pptTemplateRepository.countByUserId(userId) == 0;

            PptTemplate template = PptTemplate.builder()
                    .userId(userId)
                    .name(templateName)
                    .description(description != null ? description : "")
                    .filePath(dest.getAbsolutePath())
                    .fileSize(file.getSize())
                    .isDefault(isFirst) // 第一个模板自动设为默认
                    .build();
            template = pptTemplateRepository.save(template);

            log.info("PPT 模板已上传: userId={}, name={}, path={}", userId, templateName, dest.getAbsolutePath());
            return ResponseEntity.ok(toDto(template));
        } catch (IOException e) {
            log.error("PPT 模板上传失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }

    /** 列出当前用户的所有模板 */
    @GetMapping
    public ResponseEntity<?> list(Authentication auth) {
        Long userId = extractUserId(auth);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "未登录"));
        }
        List<PptTemplateDto> list = pptTemplateRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** 编辑模板信息 */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody Map<String, Object> body,
                                    Authentication auth) {
        Long userId = extractUserId(auth);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "未登录"));
        }
        PptTemplate template = pptTemplateRepository.findByIdAndUserId(id, userId).orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        if (body.containsKey("name")) {
            template.setName((String) body.get("name"));
        }
        if (body.containsKey("description")) {
            template.setDescription((String) body.get("description"));
        }
        if (Boolean.TRUE.equals(body.get("isDefault"))) {
            pptTemplateRepository.clearDefaultByUserId(userId);
            template.setIsDefault(true);
        }
        pptTemplateRepository.save(template);
        return ResponseEntity.ok(toDto(template));
    }

    /** 删除模板 */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        Long userId = extractUserId(auth);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "未登录"));
        }
        PptTemplate template = pptTemplateRepository.findByIdAndUserId(id, userId).orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        // 删除文件
        try {
            File f = new File(template.getFilePath());
            if (f.exists()) f.delete();
        } catch (Exception e) {
            log.warn("删除模板文件失败: {}", template.getFilePath(), e);
        }
        pptTemplateRepository.delete(template);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** 设为默认模板 */
    @PutMapping("/{id}/default")
    public ResponseEntity<?> setDefault(@PathVariable Long id, Authentication auth) {
        Long userId = extractUserId(auth);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "未登录"));
        }
        PptTemplate template = pptTemplateRepository.findByIdAndUserId(id, userId).orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        pptTemplateRepository.clearDefaultByUserId(userId);
        template.setIsDefault(true);
        pptTemplateRepository.save(template);
        return ResponseEntity.ok(toDto(template));
    }

    private PptTemplateDto toDto(PptTemplate t) {
        PptTemplateDto dto = new PptTemplateDto();
        dto.setId(t.getId());
        dto.setName(t.getName());
        dto.setDescription(t.getDescription());
        dto.setFileSize(t.getFileSize());
        dto.setIsDefault(t.getIsDefault());
        dto.setCreatedAt(t.getCreatedAt());
        dto.setUpdatedAt(t.getUpdatedAt());
        return dto;
    }

    private Long extractUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        if (auth.getPrincipal() instanceof Long id) return id;
        if (auth.getPrincipal() instanceof Number num) return num.longValue();
        return null;
    }
}
