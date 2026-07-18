package com.gisagent.controller;

import com.gisagent.connector.IMAKnowledgeBaseConnector;
import com.gisagent.connector.MockIMAKnowledgeBaseConnector;
import com.gisagent.service.KbAwarenessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 知识库自动感知接口（P3-1）。
 * - POST /api/ima/kb-sync          手动触发一次全量同步（生产也可用）
 * - POST /api/ima/kb-simulate      仅 mock 模式：装填一条模拟更新事件并同步，用于联调验证
 * - POST /api/projects/{id}/rerun-kb  用最新知识库重生成（清除脏标记并重新跑流水线）
 */
@RestController
@RequestMapping("/api")
@Slf4j
public class KbAwarenessController {

    private final KbAwarenessService kbAwarenessService;
    private final IMAKnowledgeBaseConnector imaConnector;

    public KbAwarenessController(KbAwarenessService kbAwarenessService,
                                 IMAKnowledgeBaseConnector imaConnector) {
        this.kbAwarenessService = kbAwarenessService;
        this.imaConnector = imaConnector;
    }

    @PostMapping("/ima/kb-sync")
    public ResponseEntity<?> sync(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        var r = kbAwarenessService.syncUser(userId);
        return ResponseEntity.ok(Map.of(
                "message", "同步完成",
                "eventCount", r.eventCount(),
                "dirtyProjects", r.dirtyProjectCount()
        ));
    }

    @PostMapping("/ima/kb-simulate")
    public ResponseEntity<?> simulate(Authentication auth) {
        if (!(imaConnector instanceof MockIMAKnowledgeBaseConnector mock)) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅 mock 模式支持模拟知识库更新"));
        }
        Long userId = (Long) auth.getPrincipal();
        mock.armSimulation();
        var r = kbAwarenessService.syncUser(userId);
        return ResponseEntity.ok(Map.of(
                "message", "已模拟知识库更新并同步",
                "eventCount", r.eventCount(),
                "dirtyProjects", r.dirtyProjectCount()
        ));
    }

    @PostMapping("/projects/{id}/rerun-kb")
    public ResponseEntity<?> rerunWithLatestKb(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        // 仅允许用户操作自己的项目
        var project = kbAwarenessService.findProject(id);
        if (project == null || !project.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        kbAwarenessService.regenWithLatestKb(id);
        return ResponseEntity.ok(Map.of("message", "已用最新知识库触发重生成", "projectId", id));
    }
}
