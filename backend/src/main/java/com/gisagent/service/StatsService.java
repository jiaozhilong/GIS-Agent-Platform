package com.gisagent.service;

import com.gisagent.entity.*;
import com.gisagent.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 使用数据看板（P4-4）：基于 pipeline_runs / tool_executions 聚合
 * 生成量、成功率、趋势、工具耗时与成功率、模板使用分布。
 * 支持个人视角（默认）与团队视角（teamId，需为成员）。
 */
@Service
@Slf4j
public class StatsService {

    private final ProjectRepository projectRepository;
    private final PipelineRunRepository runRepository;
    private final ToolExecutionRepository toolExecRepository;
    private final TeamService teamService;

    public StatsService(ProjectRepository projectRepository,
                        PipelineRunRepository runRepository,
                        ToolExecutionRepository toolExecRepository,
                        TeamService teamService) {
        this.projectRepository = projectRepository;
        this.runRepository = runRepository;
        this.toolExecRepository = toolExecRepository;
        this.teamService = teamService;
    }

    /** 概览 + 趋势 + 工具 + 模板分布（一次返回，前端少发请求） */
    public Map<String, Object> overview(Long userId, Long teamId) {
        List<Project> projects = resolveProjects(userId, teamId);
        List<Long> pids = projects.stream().map(Project::getId).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", teamId != null ? "team" : "personal");
        out.put("totalProjects", projects.size());

        if (pids.isEmpty()) {
            out.put("totalRuns", 0L);
            out.put("completedRuns", 0L);
            out.put("failedRuns", 0L);
            out.put("successRate", 0.0);
            out.put("avgRunSeconds", 0.0);
            out.put("trend", dailyTrend(List.of(), 30));
            out.put("tools", List.of());
            out.put("templates", List.of());
            return out;
        }

        long totalRuns = runRepository.countByProjectIdIn(pids);
        long completed = runRepository.countByProjectIdInAndStatus(pids, "SUCCESS")
                + runRepository.countByProjectIdInAndStatus(pids, "PARTIAL");
        long failed = runRepository.countByProjectIdInAndStatus(pids, "FAILED");
        out.put("totalRuns", totalRuns);
        out.put("completedRuns", completed);
        out.put("failedRuns", failed);
        out.put("successRate", totalRuns == 0 ? 0.0 : Math.round((double) completed / totalRuns * 1000.0) / 1000.0);

        List<PipelineRun> runs = runRepository.findByProjectIdIn(pids);
        double avgRun = runs.stream()
                .filter(r -> r.getStartedAt() != null && r.getFinishedAt() != null)
                .mapToLong(r -> Duration.between(r.getStartedAt(), r.getFinishedAt()).getSeconds())
                .average().orElse(0.0);
        out.put("avgRunSeconds", Math.round(avgRun * 10.0) / 10.0);

        out.put("trend", dailyTrend(runs, 30));
        out.put("tools", toolStats(runs.stream().map(PipelineRun::getId).toList()));

        Map<String, Long> tpl = runs.stream()
                .filter(r -> r.getTemplateId() != null)
                .collect(Collectors.groupingBy(PipelineRun::getTemplateId, Collectors.counting()));
        out.put("templates", tpl.entrySet().stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("templateId", e.getKey());
            m.put("count", e.getValue());
            return m;
        }).sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count"))).toList());

        return out;
    }

    /** 按 teamId 或个人归属解析统计范围（团队需为成员） */
    private List<Project> resolveProjects(Long userId, Long teamId) {
        if (teamId != null) {
            teamService.requireTeamRole(teamId, userId, Role.VIEWER);
            return projectRepository.findByTeamIdIn(List.of(teamId));
        }
        return projectRepository.findByUserId(userId);
    }

    /** 最近 days 天每日生成计数（按 createdAt 归日）
     *  注：TIMESTAMP 无时区列由 Hibernate 以 UTC 读回 Instant，故分桶与“今天”统一用 UTC，
     *  避免出现“今天”的运行被时区转换推到“明天”而遗漏。 */
    private List<Map<String, Object>> dailyTrend(List<PipelineRun> runs, int days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<LocalDate, Long> counts = runs.stream()
                .filter(r -> r.getCreatedAt() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.counting()));
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d.toString());
            m.put("count", counts.getOrDefault(d, 0L));
            list.add(m);
        }
        return list;
    }

    /** 各工具类型：调用次数、成功率、平均耗时 */
    private List<Map<String, Object>> toolStats(List<Long> runIds) {
        if (runIds.isEmpty()) return List.of();
        List<ToolExecution> execs = toolExecRepository.findByPipelineRunIdIn(runIds);
        Map<String, List<ToolExecution>> byType = execs.stream()
                .collect(Collectors.groupingBy(ToolExecution::getToolType));
        return byType.entrySet().stream().map(e -> {
            List<ToolExecution> xs = e.getValue();
            long cnt = xs.size();
            long succ = xs.stream().filter(x -> "SUCCESS".equals(x.getStatus())).count();
            double avg = xs.stream()
                    .filter(x -> x.getStartedAt() != null && x.getFinishedAt() != null)
                    .mapToLong(x -> Duration.between(x.getStartedAt(), x.getFinishedAt()).getSeconds())
                    .average().orElse(0.0);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("toolType", e.getKey());
            m.put("count", cnt);
            m.put("successCount", succ);
            m.put("successRate", cnt == 0 ? 0.0 : Math.round((double) succ / cnt * 1000.0) / 1000.0);
            m.put("avgSeconds", Math.round(avg * 10.0) / 10.0);
            return m;
        }).sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count"))).toList();
    }
}
