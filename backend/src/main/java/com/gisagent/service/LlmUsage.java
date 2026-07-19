package com.gisagent.service;

import java.io.Serializable;

/**
 * LLM token 用量统计（P7-3 用量计费）。
 * 以不可变值对象语义提供累加能力，便于在并行工具执行中安全汇总。
 */
public class LlmUsage implements Serializable {

    private long promptTokens;
    private long completionTokens;
    private long totalTokens;

    public LlmUsage() {
    }

    public LlmUsage(long promptTokens, long completionTokens, long totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    /** 零用量常量 */
    public static final LlmUsage ZERO = new LlmUsage(0, 0, 0);

    public long getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(long promptTokens) {
        this.promptTokens = promptTokens;
    }

    public long getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(long completionTokens) {
        this.completionTokens = completionTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    /** 返回一个新的累加结果，不修改自身（线程安全） */
    public LlmUsage add(LlmUsage o) {
        if (o == null) return this;
        return new LlmUsage(
                this.promptTokens + o.promptTokens,
                this.completionTokens + o.completionTokens,
                this.totalTokens + o.totalTokens);
    }
}
