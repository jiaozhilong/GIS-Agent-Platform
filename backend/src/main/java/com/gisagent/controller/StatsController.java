package com.gisagent.controller;

import com.gisagent.service.StatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@Slf4j
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) return null;
        return (Long) auth.getPrincipal();
    }

    /**
     * 使用数据看板概览。
     * - 不传 teamId：统计当前用户的个人项目。
     * - 传 teamId：统计该团队项目（需为成员，否则 403）。
     */
    @GetMapping("/overview")
    public ResponseEntity<?> overview(@RequestParam(value = "teamId", required = false) Long teamId,
                                      Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        try {
            return ResponseEntity.ok(statsService.overview(uid, teamId));
        } catch (RuntimeException e) {
            return ResponseEntity.status(statusOf(e)).body(Map.of("message", e.getMessage()));
        }
    }

    private int statusOf(RuntimeException e) {
        if (e instanceof org.springframework.web.server.ResponseStatusException rse)
            return rse.getStatusCode().value();
        return 400;
    }
}
