package com.gisagent.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.entity.PipelineTemplate;
import com.gisagent.repository.PipelineTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动时种子预置模板（仅当模板表为空时）。
 * 5 套 GIS 行业场景化流程模板，覆盖不同工具链组合。
 */
@Component
@Slf4j
public class TemplateSeedRunner implements CommandLineRunner {

    private final PipelineTemplateRepository templateRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TemplateSeedRunner(PipelineTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    public void run(String... args) {
        if (templateRepository.count() > 0) {
            log.info("预置模板已存在，跳过种子");
            return;
        }

        List<PipelineTemplate> seeds = List.of(
                tpl("quick_selection", "快速产品选型", "official",
                        "轻量两步骤：解析需求文档后直接匹配推荐产品，适合快速出产品选型清单。",
                        List.of("REQUIREMENT_ANALYSIS", "PRODUCT_MATCHING"), "≈5 分钟"),
                tpl("full_solution", "全套方案生成", "official",
                        "从需求分析到方案输出 + PPT 交付的完整流程，覆盖产品匹配、案例推荐、竞品对比、架构图、大纲、质检。",
                        List.of("REQUIREMENT_ANALYSIS", "PRODUCT_MATCHING", "CASE_RECOMMEND", "COMPETITOR_ANALYSIS",
                                "ARCHITECTURE_DIAGRAM", "SOLUTION_OUTLINE", "SOLUTION_QC", "SOLUTION_OUTPUT"), "≈15 分钟"),
                tpl("bid_solution", "投标技术方案", "official",
                        "面向技术标书：需求分析 → 产品匹配 → 架构图 → 方案大纲 → 方案输出，突出技术架构与实施路径。",
                        List.of("REQUIREMENT_ANALYSIS", "PRODUCT_MATCHING", "ARCHITECTURE_DIAGRAM",
                                "SOLUTION_OUTLINE", "SOLUTION_OUTPUT"), "≈10 分钟"),
                tpl("compete_analysis", "竞品对标分析", "official",
                        "面向投标前竞争态势研判：需求分析 → 产品匹配 → 竞品对比 → 方案输出。",
                        List.of("REQUIREMENT_ANALYSIS", "PRODUCT_MATCHING", "COMPETITOR_ANALYSIS",
                                "SOLUTION_OUTPUT"), "≈8 分钟"),
                tpl("case_driven", "案例借鉴方案", "community",
                        "社区贡献：以标杆案例驱动，需求分析 → 产品匹配 → 案例推荐 → 方案输出。",
                        List.of("REQUIREMENT_ANALYSIS", "PRODUCT_MATCHING", "CASE_RECOMMEND",
                                "SOLUTION_OUTPUT"), "≈9 分钟")
        );

        templateRepository.saveAll(seeds);
        log.info("已种子 {} 套预置模板", seeds.size());
    }

    private PipelineTemplate tpl(String key, String name, String category, String desc,
                                 List<String> chain, String eta) {
        try {
            String json = objectMapper.writeValueAsString(chain);
            return PipelineTemplate.builder()
                    .templateKey(key)
                    .name(name)
                    .category(category)
                    .description(desc)
                    .toolChainJson(json)
                    .estimatedTime(eta)
                    .builtin(true)
                    .usageCount(0L)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("模板工具链序列化失败: " + key, e);
        }
    }
}
