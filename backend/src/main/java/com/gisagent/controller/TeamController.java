package com.gisagent.controller;

import com.gisagent.service.TeamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@Slf4j
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    private Long uid(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) return null;
        return (Long) auth.getPrincipal();
    }

    /** 创建团队（创建者自动为 OWNER） */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        try {
            return ResponseEntity.status(201).body(teamService.createTeam(body.get("name"), uid));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /** 我所在的团队 */
    @GetMapping
    public ResponseEntity<?> myTeams(Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        return ResponseEntity.ok(teamService.listMyTeams(uid));
    }

    /** 团队详情 + 成员列表 */
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        try {
            return ResponseEntity.ok(teamService.getTeamDetail(id, uid));
        } catch (RuntimeException e) {
            return ResponseEntity.status(statusOf(e)).body(Map.of("message", e.getMessage()));
        }
    }

    /** 邀请成员 */
    @PostMapping("/{id}/members")
    public ResponseEntity<?> addMember(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        try {
            return ResponseEntity.status(201).body(teamService.addMember(id, uid, body.get("username"), body.get("role")));
        } catch (RuntimeException e) {
            return ResponseEntity.status(statusOf(e)).body(Map.of("message", e.getMessage()));
        }
    }

    /** 修改成员角色 */
    @PutMapping("/{id}/members/{userId}")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @PathVariable Long userId,
                                        @RequestBody Map<String, String> body, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        try {
            return ResponseEntity.ok(teamService.updateRole(id, uid, userId, body.get("role")));
        } catch (RuntimeException e) {
            return ResponseEntity.status(statusOf(e)).body(Map.of("message", e.getMessage()));
        }
    }

    /** 移除成员 */
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<?> removeMember(@PathVariable Long id, @PathVariable Long userId, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        try {
            teamService.removeMember(id, uid, userId);
            return ResponseEntity.ok(Map.of("message", "已移除成员"));
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
