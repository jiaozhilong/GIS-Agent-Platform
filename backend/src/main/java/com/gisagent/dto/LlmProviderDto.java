package com.gisagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class LlmProviderDto {

    @Data
    public static class CreateRequest {
        @NotBlank(message = "Provider 名称不能为空")
        private String name;

        @NotBlank(message = "Provider 类型不能为空")
        private String providerType;

        @NotBlank(message = "API 地址不能为空")
        private String endpoint;

        private String apiKey;
        private Boolean isDefault;
    }

    @Data
    public static class UpdateRequest {
        private String name;
        private String endpoint;
        private String apiKey;
        private Boolean isDefault;
    }

    @Data
    public static class TestResponse {
        private boolean success;
        private long responseTimeMs;
        private String message;
    }

    @Data
    public static class ProviderResponse {
        private Long id;
        private String name;
        private String providerType;
        private String endpoint;
        private Boolean isDefault;
        private boolean hasApiKey;
    }
}
