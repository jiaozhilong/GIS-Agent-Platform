package com.gisagent.controller;

import com.gisagent.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 站内通知中心（P5-5）。
 * GET  /api/notifications — 未读数 + 最近通知列表
 * POST /api/notifications/read-all — 全部标为已读
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<?> summary(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(notificationService.summary(userId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> readAll(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        Long userId = (Long) auth.getPrincipal();
        notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
