package com.gisagent.service;

import com.gisagent.entity.*;
import com.gisagent.repository.*;
import com.gisagent.config.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 团队空间与 RBAC：团队 CRUD、成员邀请/改角色/移除、基于角色的访问控制。
 * 角色层级 OWNER > ADMIN > EDITOR > MEMBER > VIEWER。
 */
@Service
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public TeamService(TeamRepository teamRepository, TeamMemberRepository memberRepository,
                       ProjectRepository projectRepository, UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    /** 创建团队，创建者自动成为 OWNER */
    @Transactional
    public Team createTeam(String name, Long ownerId) {
        if (name == null || name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "团队名称不能为空");
        Team team = Team.builder().name(name.trim()).ownerId(ownerId)
                .organizationId(TenantContext.getOrganizationId()).build();
        Team saved = teamRepository.save(team);
        memberRepository.save(TeamMember.builder().teamId(saved.getId()).userId(ownerId).role(Role.OWNER).build());
        log.info("创建团队: id={}, name={}, owner={}", saved.getId(), saved.getName(), ownerId);
        return saved;
    }

    /** 我所在的团队（含我的角色），按当前组织隔离 */
    public List<Map<String, Object>> listMyTeams(Long userId) {
        Long org = TenantContext.getOrganizationId();
        List<TeamMember> members = memberRepository.findByUserId(userId);
        return members.stream().map(m -> {
            Team t = teamRepository.findById(m.getTeamId()).orElse(null);
            if (t == null) return null;
            if (org != null && t.getOrganizationId() != null && !org.equals(t.getOrganizationId())) return null;
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", t.getId());
            map.put("name", t.getName());
            map.put("ownerId", t.getOwnerId());
            map.put("myRole", m.getRole().name());
            map.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
            return map;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /** 团队成员列表（需为成员） */
    public Map<String, Object> getTeamDetail(Long teamId, Long userId) {
        TeamMember me = memberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "无团队访问权限"));
        Team t = teamRepository.findById(teamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "团队不存在"));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", t.getId());
        detail.put("name", t.getName());
        detail.put("ownerId", t.getOwnerId());
        detail.put("myRole", me.getRole().name());
        List<Map<String, Object>> members = memberRepository.findByTeamId(teamId).stream().map(m -> {
            User u = userRepository.findById(m.getUserId()).orElse(null);
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("userId", m.getUserId());
            mm.put("username", u != null ? u.getUsername() : "用户" + m.getUserId());
            mm.put("role", m.getRole().name());
            mm.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            return mm;
        }).collect(Collectors.toList());
        detail.put("members", members);
        return detail;
    }

    /** 邀请成员（owner/admin 可操作） */
    @Transactional
    public Map<String, Object> addMember(Long teamId, Long operatorId, String username, String roleStr) {
        TeamMember op = requireManage(teamId, operatorId);
        Role role = parseRole(roleStr);
        if (role == Role.OWNER && op.getRole() != Role.OWNER)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅团队 OWNER 可指派 OWNER 角色");
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "团队不存在"));
        User target = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + username));
        // 多租户：禁止跨组织邀请
        Long org = TenantContext.getOrganizationId();
        if (org != null && team.getOrganizationId() != null
                && target.getOrganizationId() != null
                && !target.getOrganizationId().equals(team.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "不能邀请其他组织的成员");
        }
        if (memberRepository.existsByTeamIdAndUserId(teamId, target.getId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该用户已是团队成员");
        TeamMember m = memberRepository.save(TeamMember.builder().teamId(teamId).userId(target.getId()).role(role).build());
        log.info("团队 {} 邀请成员 {} 角色 {}", teamId, target.getUsername(), role);
        return memberDto(m, target.getUsername());
    }

    /** 修改成员角色（owner/admin 可操作，不能动最后一个 OWNER） */
    @Transactional
    public Map<String, Object> updateRole(Long teamId, Long operatorId, Long targetUserId, String roleStr) {
        requireManage(teamId, operatorId);
        Role role = parseRole(roleStr);
        TeamMember target = memberRepository.findByTeamIdAndUserId(teamId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "成员不存在"));
        if (target.getRole() == Role.OWNER) {
            long owners = memberRepository.findByTeamId(teamId).stream().filter(m -> m.getRole() == Role.OWNER).count();
            if (owners <= 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "团队至少保留一名 OWNER");
        }
        if (role == Role.OWNER && !memberRepository.findByTeamIdAndUserId(teamId, operatorId).get().getRole().isAtLeast(Role.OWNER))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅团队 OWNER 可指派 OWNER 角色");
        target.setRole(role);
        memberRepository.save(target);
        User u = userRepository.findById(targetUserId).orElse(null);
        return memberDto(target, u != null ? u.getUsername() : "用户" + targetUserId);
    }

    /** 移除成员（owner/admin 可操作，不能移除最后一个 OWNER） */
    @Transactional
    public void removeMember(Long teamId, Long operatorId, Long targetUserId) {
        requireManage(teamId, operatorId);
        TeamMember target = memberRepository.findByTeamIdAndUserId(teamId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "成员不存在"));
        if (target.getRole() == Role.OWNER) {
            long owners = memberRepository.findByTeamId(teamId).stream().filter(m -> m.getRole() == Role.OWNER).count();
            if (owners <= 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "团队至少保留一名 OWNER");
        }
        memberRepository.deleteByTeamIdAndUserId(teamId, targetUserId);
        log.info("团队 {} 移除成员 {}", teamId, targetUserId);
    }

    /**
     * 项目访问鉴权：minRole 为所需最低角色。
     * - 个人项目(teamId=null)：仅创建者可访问。
     * - 团队项目：需为成员且角色 >= minRole。
     */
    public void requireProjectRole(Long projectId, Long userId, Role minRole) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在"));
        // 多租户：拒绝跨组织访问
        Long org = TenantContext.getOrganizationId();
        if (org != null && p.getOrganizationId() != null && !org.equals(p.getOrganizationId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问其他组织的项目");
        if (p.getTeamId() == null) {
            if (!p.getUserId().equals(userId))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅项目创建者可访问");
            return;
        }
        TeamMember m = memberRepository.findByTeamIdAndUserId(p.getTeamId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "无团队访问权限"));
        if (!m.getRole().isAtLeast(minRole))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "角色权限不足（需 " + minRole.name() + " 及以上）");
    }

    /** 团队访问鉴权：minRole 为所需最低角色（按 teamId，适用于项目尚不存在时） */
    public void requireTeamRole(Long teamId, Long userId, Role minRole) {
        Team t = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "团队不存在"));
        Long org = TenantContext.getOrganizationId();
        if (org != null && t.getOrganizationId() != null && !org.equals(t.getOrganizationId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问其他组织的团队");
        TeamMember m = memberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "无团队访问权限"));
        if (!m.getRole().isAtLeast(minRole))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "角色权限不足（需 " + minRole.name() + " 及以上）");
    }

    /** 校验操作者具有管理权限（ADMIN/OWNER），返回其成员记录 */
    private TeamMember requireManage(Long teamId, Long operatorId) {
        TeamMember op = memberRepository.findByTeamIdAndUserId(teamId, operatorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "无团队访问权限"));
        if (!op.getRole().isAtLeast(Role.ADMIN))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅团队 ADMIN/OWNER 可管理成员");
        return op;
    }

    private Role parseRole(String s) {
        if (s == null || s.isBlank()) return Role.MEMBER;
        try { return Role.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法角色: " + s); }
    }

    private Map<String, Object> memberDto(TeamMember m, String username) {
        Map<String, Object> mm = new LinkedHashMap<>();
        mm.put("userId", m.getUserId());
        mm.put("username", username);
        mm.put("role", m.getRole().name());
        mm.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
        return mm;
    }
}
