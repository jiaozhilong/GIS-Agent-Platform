package com.gisagent.controller;

import com.gisagent.pipeline.PipelineTool;
import com.gisagent.pipeline.ToolCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台可编排 Skills（流水线工具）能力清单接口。
 * 数量与内容由实际注册到 Spring 容器的 PipelineTool bean 驱动，
 * 而非写死常量；目录元数据来自 ToolCatalog（与 Agent 自编排共用）。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillsController {

    private final List<PipelineTool> tools;
    private final ToolCatalog catalog;

    public SkillsController(List<PipelineTool> tools, ToolCatalog catalog) {
        this.tools = tools;
        this.catalog = catalog;
    }

    @GetMapping
    public Map<String, Object> list() {
        List<String> types = tools.stream().map(PipelineTool::getToolType).toList();
        List<Map<String, Object>> skills = catalog.toSkills(types);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", skills.size());
        resp.put("skills", skills);
        return resp;
    }
}
