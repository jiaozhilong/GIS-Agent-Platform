package com.gisagent.controller;

import com.gisagent.service.UsageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用量计费聚合接口（P7-3）。
 * GET /api/usage/summary  用量概览（按用户/项目/组织/时间聚合 + 估算费用）
 *   - 普通用户：仅本人项目
 *   - SUPER_ADMIN 传 all=true：查看全平台，可附加 orgId / projectId 下钻
 *   - 可选 from / to（yyyy-MM-dd，UTC）限定时间窗
 */
@RestController
@RequestMapping("/api/usage")
@Slf4j
public class UsageController {

    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) return null;
        return (Long) auth.getPrincipal();
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(@RequestParam(value = "all", required = false) Boolean all,
                                     @RequestParam(value = "orgId", required = false) Long orgId,
                                     @RequestParam(value = "projectId", required = false) Long projectId,
                                     @RequestParam(value = "from", required = false) String from,
                                     @RequestParam(value = "to", required = false) String to,
                                     Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        try {
            return ResponseEntity.ok(usageService.summary(uid, all, orgId, projectId, from, to));
        } catch (RuntimeException e) {
            log.error("用量聚合失败", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            return ResponseEntity.status(statusOf(e)).body(Map.of("message", msg));
        }
    }

    private int statusOf(RuntimeException e) {
        if (e instanceof org.springframework.web.server.ResponseStatusException rse)
            return rse.getStatusCode().value();
        return 400;
    }
}
