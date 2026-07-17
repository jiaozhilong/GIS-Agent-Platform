package com.gisagent.controller;

import com.gisagent.entity.PipelineTemplate;
import com.gisagent.repository.PipelineTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@Slf4j
public class PipelineTemplateController {

    private final PipelineTemplateRepository templateRepository;

    public PipelineTemplateController(PipelineTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
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
}
