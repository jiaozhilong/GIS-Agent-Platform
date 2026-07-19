package com.gisagent.controller;

import com.gisagent.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 审计日志查询（P5-4）。
 * GET /api/audit?limit=50 — 最近操作记录。
 */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = "50") int limit, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        return ResponseEntity.ok(Map.of("logs", auditService.recent(limit)));
    }
}
