package com.gisagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.dto.TemplateSaveRequest;
import com.gisagent.entity.PipelineTemplate;
import com.gisagent.repository.PipelineTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/templates")
@Slf4j
public class PipelineTemplateController {

    /** 引擎已注册的工具类型（与 PipelineEngine.TOOL_BY_TYPE / 前端 TOOL_META 对齐） */
    private static final Set<String> ALLOWED_TOOL_TYPES = Set.of(
            "REQUIREMENT_ANALYSIS",
            "PRODUCT_MATCHING",
            "CASE_RECOMMEND",
            "COMPETITOR_ANALYSIS",
            "ARCHITECTURE_DIAGRAM",
            "SOLUTION_OUTLINE",
            "SOLUTION_QC",
            "SOLUTION_OUTPUT");

    private final PipelineTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    public PipelineTemplateController(PipelineTemplateRepository templateRepository,
                                      ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
    }

    /** 模板列表，可按 category(official/community/mine) 过滤；默认内置模板优先 */
    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String category) {
        List<PipelineTemplate> list;
        if (category != null && !category.isBlank()) {
            list = templateRepository.findByCategory(category);
        } else {
            list = templateRepository.findAllByOrderByBuiltinDescIdAsc();
        }
        return ResponseEntity.ok(list);
    }

    /** 模板详情（按 templateKey） */
    @GetMapping("/{key}")
    public ResponseEntity<?> getByKey(@PathVariable String key) {
        return templateRepository.findByTemplateKey(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 保存自定义模板（category=mine, builtin=false）。
     * 自动生成唯一 templateKey，校验名称与工具链合法性，工具链序列化为 JSONB 字符串。
     */
    @PostMapping
    public ResponseEntity<?> save(@RequestBody TemplateSaveRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "模板名称不能为空"));
        }
        if (req.getToolChain() == null || req.getToolChain().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "工具链不能为空，请至少添加一个工具节点"));
        }
        for (String type : req.getToolChain()) {
            if (!ALLOWED_TOOL_TYPES.contains(type)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "包含非法工具类型: " + type));
            }
        }
        String key = "mine_" + UUID.randomUUID().toString().replace("-", "");
        String chainJson;
        try {
            chainJson = objectMapper.writeValueAsString(req.getToolChain());
        } catch (Exception e) {
            log.warn("工具链序列化失败", e);
            return ResponseEntity.badRequest().body(Map.of("message", "工具链格式错误"));
        }
        PipelineTemplate tpl = PipelineTemplate.builder()
                .templateKey(key)
                .name(req.getName().trim())
                .category("mine")
                .description(req.getDescription() == null ? "" : req.getDescription())
                .toolChainJson(chainJson)
                .estimatedTime(req.getEstimatedTime())
                .builtin(false)
                .usageCount(0L)
                .build();
        PipelineTemplate saved = templateRepository.save(tpl);
        log.info("保存自定义模板成功: key={}, name={}, 工具数={}",
                key, saved.getName(), req.getToolChain().size());
        return ResponseEntity.status(201).body(saved);
    }

    /**
     * 删除自定义模板（仅允许 category=mine，防止误删内置模板）。
     */
    @DeleteMapping("/{key}")
    public ResponseEntity<?> remove(@PathVariable String key) {
        return templateRepository.findByTemplateKey(key)
                .map(tpl -> {
                    if (tpl.isBuiltin()) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("message", "内置模板不可删除"));
                    }
                    templateRepository.delete(tpl);
                    log.info("删除自定义模板: key={}", key);
                    return ResponseEntity.ok(Map.of("message", "已删除", "key", key));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
