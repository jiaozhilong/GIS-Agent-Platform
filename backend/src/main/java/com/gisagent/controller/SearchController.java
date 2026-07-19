package com.gisagent.controller;

import com.gisagent.service.EmbeddingService;
import com.gisagent.service.TeamService;
import com.gisagent.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 混合检索控制器（P5-1）。
 * POST /api/search — 项目内关键词 + 向量相似度混合检索。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchController {

    private final EmbeddingService embeddingService;
    private final TeamService teamService;

    public record SearchRequest(Long projectId, String query, Integer topK) {}

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody SearchRequest req, Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        Long userId = (Long) auth.getPrincipal();
        Long projectId = req.projectId();
        String query = req.query();
        int topK = req.topK() != null ? req.topK() : 5;

        if (projectId == null || query == null || query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "projectId 和 query 不能为空"));
        }

        // RBAC：至少 VIEWER
        teamService.requireProjectRole(projectId, userId, Role.VIEWER);

        List<Map<String, Object>> results = embeddingService.hybridSearch(projectId, query, topK);
        return ResponseEntity.ok(Map.of(
                "projectId", projectId,
                "query", query,
                "total", results.size(),
                "results", results
        ));
    }
}
