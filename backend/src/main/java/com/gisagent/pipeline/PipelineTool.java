package com.gisagent.pipeline;

/**
 * 流水线工具接口。每个工具（需求分析、产品匹配等）实现此接口。
 */
public interface PipelineTool {

    /** 工具类型标识，对应 ToolExecution.toolType */
    String getToolType();

    /**
     * 执行工具。
     *
     * @param context 共享上下文（输入 + 输出）
     * @param llmConfig 工具使用的 LLM 配置
     * @return 是否执行成功
     */
    boolean execute(ToolContext context, LlmConfig llmConfig);

    /** LLM 配置传输对象 */
    class LlmConfig {
        public String endpoint;
        public String apiKey;
        public String model;
        public Double temperature;
        public Integer maxTokens;

        public static LlmConfig of(String endpoint, String apiKey, String model) {
            LlmConfig c = new LlmConfig();
            c.endpoint = endpoint;
            c.apiKey = apiKey;
            c.model = model;
            c.temperature = 0.3;
            c.maxTokens = 2048;
            return c;
        }
    }
}
