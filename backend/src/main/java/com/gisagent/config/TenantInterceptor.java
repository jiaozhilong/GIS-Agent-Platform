package com.gisagent.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户拦截器：从请求 Authorization 头解析 JWT，提取 orgId 写入 {@link TenantContext}。
 * 运行于 Spring Security 之后（DispatcherServlet 内），令牌已随请求到达。
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    public TenantInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Long orgId = jwtTokenProvider.getOrganizationIdFromToken(header.substring(7));
                TenantContext.setOrganizationId(orgId);
            } catch (Exception e) {
                TenantContext.setOrganizationId(null);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }
}
