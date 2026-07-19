package com.gisagent.service;

import com.gisagent.entity.Organization;
import com.gisagent.entity.PipelineRun;
import com.gisagent.entity.Project;
import com.gisagent.entity.User;
import com.gisagent.repository.OrganizationRepository;
import com.gisagent.repository.PipelineRunRepository;
import com.gisagent.repository.ProjectRepository;
import com.gisagent.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.FORBIDDEN;

/**
 * 用量计费聚合服务（P7-3）。
 * 以 pipeline_runs 的 token 字段为数据源，按用户 / 项目 / 组织 / 时间维度聚合，
 * 并提供基于可配置单价的估算费用。
 *
 * 权限：普通用户仅能查看自己项目的用量；SUPER_ADMIN 传 all=true 可查看全平台，
 * 并可附加 orgId / projectId 进一步下钻。
 */
@Service
@Slf4j
public class UsageService {

    private final PipelineRunRepository runRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;

    /** 每 1K 输入 token 单价（元），可通过 app.usage.input-price-per-1k 覆盖 */
    @Value("${app.usage.input-price-per-1k:0.001}")
    private double inputPricePer1k;

    /** 每 1K 输出 token 单价（元），可通过 app.usage.output-price-per-1k 覆盖 */
    @Value("${app.usage.output-price-per-1k:0.002}")
    private double outputPricePer1k;

    public UsageService(PipelineRunRepository runRepository,
                        ProjectRepository projectRepository,
                        UserRepository userRepository,
                        OrganizationRepository organizationRepository) {
        this.runRepository = runRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
    }

    /**
     * 聚合用量概览。
     *
     * @param uid       当前用户 ID（鉴权 principal）
     * @param all       超管专属：true 表示查看全平台
     * @param orgId     组织下钻（仅超管 + all 时生效）
     * @param projectId 项目下钻（仅本人/超管可见范围内生效）
     * @param from      起始日期 yyyy-MM-dd（含），UTC
     * @param to        结束日期 yyyy-MM-dd（含），UTC
     */
    public Map<String, Object> summary(Long uid, Boolean all, Long orgId, Long projectId,
                                        String from, String to) {
        User me = userRepository.findById(uid).orElseThrow(
                () -> new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "用户不存在"));
        boolean superAdmin = "SUPER_ADMIN".equals(me.getRole());
        boolean viewAll = superAdmin && Boolean.TRUE.equals(all);

        Instant fromInstant = parseDate(from, false);
        Instant toInstant = parseDate(to, true);

        // 1) 确定可见的 pipeline_runs
        List<PipelineRun> runs;
        if (superAdmin && (viewAll || orgId != null || projectId != null)) {
            List<Long> pids;
            if (orgId != null) {
                pids = projectRepository.findByOrganizationId(orgId).stream()
                        .map(Project::getId).collect(Collectors.toList());
            } else if (projectId != null) {
                pids = List.of(projectId);
            } else {
                pids = projectRepository.findAll().stream()
                        .map(Project::getId).collect(Collectors.toList());
            }
            runs = runRepository.findByProjectIdIn(pids);
        } else {
            List<Long> myPids = projectRepository.findByUserId(uid).stream()
                    .map(Project::getId).collect(Collectors.toList());
            if (projectId != null) {
                myPids = myPids.stream().filter(id -> id.equals(projectId)).collect(Collectors.toList());
            }
            runs = runRepository.findByProjectIdIn(myPids);
        }

        // 2) 时间窗过滤（finished_at 为 null 的运行不计入用量统计）
        if (fromInstant != null || toInstant != null) {
            final Instant f = fromInstant, t = toInstant;
            runs = runs.stream().filter(r -> {
                if (r.getFinishedAt() == null) return false;
                if (f != null && r.getFinishedAt().isBefore(f)) return false;
                if (t != null && r.getFinishedAt().isAfter(t)) return false;
                return true;
            }).collect(Collectors.toList());
        }

        // 3) 构建关联映射
        Set<Long> projIds = runs.stream().map(PipelineRun::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Project> projMap = projectRepository.findAllById(projIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p, (a, b) -> a));
        Set<Long> uids = projMap.values().stream().map(Project::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = userRepository.findAllById(uids).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Set<Long> orgIds = projMap.values().stream().map(Project::getOrganizationId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Organization> orgMap = organizationRepository.findAllById(orgIds).stream()
                .collect(Collectors.toMap(Organization::getId, o -> o, (a, b) -> a));

        // 4) 聚合
        long totalRuns = runs.size();
        long in = 0, out = 0, tot = 0;
        Map<Long, long[]> byUser = new LinkedHashMap<>();
        Map<Long, long[]> byProject = new LinkedHashMap<>();
        Map<Long, long[]> byOrg = new LinkedHashMap<>();
        Map<String, long[]> byDay = new TreeMap<>();

        for (PipelineRun r : runs) {
            long i = zeroIfNull(r.getInputTokens());
            long o = zeroIfNull(r.getOutputTokens());
            long t = zeroIfNull(r.getTotalTokens());
            in += i; out += o; tot += t;

            Project p = projMap.get(r.getProjectId());
            if (p != null && p.getUserId() != null) {
                byUser.computeIfAbsent(p.getUserId(), k -> new long[2])[0]++;
                byUser.get(p.getUserId())[1] += t;
            }
            byProject.computeIfAbsent(r.getProjectId(), k -> new long[2])[0]++;
            byProject.get(r.getProjectId())[1] += t;
            if (p != null && p.getOrganizationId() != null) {
                byOrg.computeIfAbsent(p.getOrganizationId(), k -> new long[2])[0]++;
                byOrg.get(p.getOrganizationId())[1] += t;
            }
            if (r.getFinishedAt() != null) {
                String day = r.getFinishedAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
                byDay.computeIfAbsent(day, k -> new long[2])[0]++;
                byDay.get(day)[1] += t;
            }
        }

        double estimatedCost = in / 1000.0 * inputPricePer1k + out / 1000.0 * outputPricePer1k;

        // 5) 组装响应
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("scope", viewAll ? "all" : "self");
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("from", from);
        window.put("to", to);
        res.put("window", window);
        res.put("totals", Map.of(
                "runs", totalRuns,
                "inputTokens", in,
                "outputTokens", out,
                "totalTokens", tot,
                "estimatedCost", round2(estimatedCost)));
        res.put("byUser", byUser.entrySet().stream().map(e -> {
            User u = userMap.get(e.getKey());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", e.getKey());
            m.put("username", u != null ? u.getUsername() : ("user-" + e.getKey()));
            m.put("displayName", u != null ? u.getDisplayName() : null);
            m.put("runs", e.getValue()[0]);
            m.put("totalTokens", e.getValue()[1]);
            return m;
        }).collect(Collectors.toList()));
        res.put("byProject", byProject.entrySet().stream().map(e -> {
            Project p = projMap.get(e.getKey());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("projectId", e.getKey());
            m.put("projectName", p != null ? p.getName() : ("project-" + e.getKey()));
            m.put("userId", p != null ? p.getUserId() : null);
            m.put("runs", e.getValue()[0]);
            m.put("totalTokens", e.getValue()[1]);
            return m;
        }).collect(Collectors.toList()));
        res.put("byOrg", byOrg.entrySet().stream().map(e -> {
            Organization o = orgMap.get(e.getKey());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orgId", e.getKey());
            m.put("orgName", o != null ? o.getName() : ("org-" + e.getKey()));
            m.put("runs", e.getValue()[0]);
            m.put("totalTokens", e.getValue()[1]);
            return m;
        }).collect(Collectors.toList()));
        res.put("byDay", byDay.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", e.getKey());
            m.put("runs", e.getValue()[0]);
            m.put("totalTokens", e.getValue()[1]);
            return m;
        }).collect(Collectors.toList()));

        return res;
    }

    private Instant parseDate(String s, boolean endOfDay) {
        if (s == null || s.isBlank()) return null;
        try {
            LocalDate d = LocalDate.parse(s.trim());
            if (endOfDay) {
                return d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            return d.atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            log.warn("用量统计日期参数解析失败: {}", s);
            return null;
        }
    }

    private long zeroIfNull(Long v) {
        return v == null ? 0L : v;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
