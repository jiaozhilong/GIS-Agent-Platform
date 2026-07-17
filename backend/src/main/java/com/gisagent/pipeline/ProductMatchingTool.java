package com.gisagent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.connector.IMAKnowledgeBaseConnector;
import com.gisagent.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool-3：产品匹配。
 * 在 IMA 产品知识库检索 → LLM 综合推荐 → 写入 context.productSelection
 */
@Component
@Slf4j
public class ProductMatchingTool implements PipelineTool {

    private final LlmService llmService;
    private final IMAKnowledgeBaseConnector imaConnector;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        你是一个 SuperMap GIS 产品专家。基于用户的需求清单和从知识库检索到的产品资料，
        推荐最合适的 GIS 产品组合。严格按照 JSON 格式输出。

        输出 JSON schema:
        {
          "products": [
            {
              "productName": "产品名称",
              "version": "版本号（如 11i）",
              "reason": "推荐理由（说明匹配的需求点）",
              "coverage": "需求覆盖率（如 95%）"
            }
          ]
        }
        """;

    public ProductMatchingTool(LlmService llmService, IMAKnowledgeBaseConnector imaConnector) {
        this.llmService = llmService;
        this.imaConnector = imaConnector;
    }

    @Override
    public String getToolType() {
        return "PRODUCT_MATCHING";
    }

    @Override
    public boolean execute(ToolContext context, LlmConfig llmConfig) {
        if (context.getRequirements() == null) {
            log.warn("需求清单为空，跳过产品匹配");
            return false;
        }

        // 1. 从 IMA 产品知识库检索（purpose=product_doc）
        String query = buildQuery(context);
        String retrieved = retrieveFromIma(query);

        // 2. 组装 LLM 提示词
        String userPrompt = """
            需求清单：
            - 功能需求：%s
            - 非功能需求：%s
            - 约束条件：%s
            - 行业场景：%s

            知识库检索到的产品资料：
            %s

            请基于以上信息推荐最合适的产品组合。
            """.formatted(
                context.getRequirements().getFunctional(),
                context.getRequirements().getNonFunctional(),
                context.getRequirements().getConstraints(),
                context.getRequirements().getIndustry(),
                retrieved
        );

        String raw = llmService.complete(
                llmConfig.endpoint, llmConfig.apiKey, llmConfig.model,
                SYSTEM_PROMPT, userPrompt, 0.2, 2048);

        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            JsonNode products = node.path("products");
            List<ToolContext.ProductSelection> selections = new ArrayList<>();
            if (products.isArray()) {
                for (JsonNode p : products) {
                    ToolContext.ProductSelection sel = new ToolContext.ProductSelection();
                    sel.setProductName(p.path("productName").asText());
                    sel.setVersion(p.path("version").asText());
                    sel.setReason(p.path("reason").asText());
                    sel.setCoverage(p.path("coverage").asText());
                    selections.add(sel);
                }
            }
            context.setProductSelection(selections);
            log.info("产品匹配完成，推荐 {} 个产品", selections.size());
            return !selections.isEmpty();
        } catch (Exception e) {
            log.error("解析产品匹配结果失败", e);
            return false;
        }
    }

    private String buildQuery(ToolContext context) {
        List<String> parts = new ArrayList<>();
        if (context.getRequirements().getFunctional() != null) {
            parts.addAll(context.getRequirements().getFunctional());
        }
        if (context.getRequirements().getIndustry() != null) {
            parts.add(context.getRequirements().getIndustry());
        }
        return String.join(", ", parts);
    }

    private String retrieveFromIma(String query) {
        try {
            // MVP：对所有已连接知识库检索 purpose=product_doc 的内容
            // 实际实现中需按用户配置的 KB 路由，这里用 Mock 返回
            var results = imaConnector.search("kb-product-doc", query,
                    new IMAKnowledgeBaseConnector.SearchOptions(5, 0.5, "product_doc"));
            StringBuilder sb = new StringBuilder();
            for (var r : results) {
                sb.append("- ").append(r.title()).append(": ").append(r.content()).append("\n");
            }
            return sb.toString().isBlank() ? "（无检索结果）" : sb.toString();
        } catch (Exception e) {
            log.warn("IMA 检索失败，使用空检索结果", e);
            return "（检索失败）";
        }
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            int lastBacktick = t.lastIndexOf("```");
            if (firstNewline > 0 && lastBacktick > firstNewline) {
                t = t.substring(firstNewline + 1, lastBacktick).trim();
            }
        }
        return t;
    }
}
