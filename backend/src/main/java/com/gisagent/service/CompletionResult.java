package com.gisagent.service;

/**
 * 一次补全的返回：文本内容 + token 用量（P7-3 计费需要）。
 * 独立成文件，因为 public 顶层 record 须与文件名一致。
 */
public record CompletionResult(String content, LlmUsage usage) {
}
