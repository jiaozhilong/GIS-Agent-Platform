package com.gisagent.controller;

import com.gisagent.service.OrchestrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orchestrate")
@Slf4j
public class OrchestrationController {

    private final OrchestrationService orchestrationService;

    public OrchestrationController(OrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) return null;
        return (Long) auth.getPrincipal();
    }

    /**
     * Agent 自编排：根据自然语言需求推荐有序工具链。
     * body: { "requirement": "..." }
     */
    @PostMapping
    public ResponseEntity<?> recommend(@RequestBody Map<String, String> body, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        String requirement = body == null ? null : body.get("requirement");
        try {
            return ResponseEntity.ok(orchestrationService.recommend(uid, requirement));
        } catch (RuntimeException e) {
            int code = (e instanceof org.springframework.web.server.ResponseStatusException rse)
                    ? rse.getStatusCode().value() : 400;
            return ResponseEntity.status(code).body(Map.of("message", e.getMessage()));
        }
    }
}
