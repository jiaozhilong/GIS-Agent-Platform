package com.gisagent.dto;

import lombok.Data;

public class BillingDto {

    /** PUT /api/billing/quota 请求体 */
    @Data
    public static class QuotaSetRequest {
        private Long organizationId;
        private Long tokenLimit;
        private Integer warnThreshold;
    }
}
