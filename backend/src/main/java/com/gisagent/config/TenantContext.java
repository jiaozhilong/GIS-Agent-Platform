package com.gisagent.config;

/**
 * 租户（组织）上下文：在请求生命周期内保存当前用户的组织 ID。
 * 由 {@link TenantInterceptor} 从 JWT 的 orgId claim 解析并写入，请求结束后清空。
 */
public class TenantContext {

    private static final ThreadLocal<Long> ORG_ID = new ThreadLocal<>();

    public static void setOrganizationId(Long id) {
        ORG_ID.set(id);
    }

    public static Long getOrganizationId() {
        return ORG_ID.get();
    }

    public static void clear() {
        ORG_ID.remove();
    }
}
