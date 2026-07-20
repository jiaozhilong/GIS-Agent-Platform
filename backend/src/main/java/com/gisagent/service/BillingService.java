package com.gisagent.service;

import com.gisagent.entity.*;
import com.gisagent.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;

import static org.springframework.http.HttpStatus.FORBIDDEN;

/**
 * 计费纵深服务（P8-1）。
 * 职责：
 *  1) 组织月度 token 配额：超管设置上限与告警阈值；
 *  2) 账期账单：超管按月结算，聚合各组织当月 LLM 用量与估算费用；
 *  3) 超限告警：每次流水线运行结束后实时聚合组织当月用量，达到阈值即写审计 + 站内通知。
 *
 * 配额按"组织"维度生效；当前月用量由 pipeline_runs 实时聚合，不单独落表。
 */
@Service
@Slf4j
public class BillingService {

    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final PipelineRunRepository pipelineRunRepository;
    private final UsageQuotaRepository quotaRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Value("${app.usage.input-price-per-1k:0.001}")
    private double inputPricePer1k;

    @Value("${app.usage.output-price-per-1k:0.002}")
    private double outputPricePer1k;

    public BillingService(ProjectRepository projectRepository,
                          OrganizationRepository organizationRepository,
                          PipelineRunRepository pipelineRunRepository,
                          UsageQuotaRepository quotaRepository,
                          InvoiceRepository invoiceRepository,
                          UserRepository userRepository,
                          AuditService auditService,
                          NotificationService notificationService) {
        this.projectRepository = projectRepository;
        this.organizationRepository = organizationRepository;
        this.pipelineRunRepository = pipelineRunRepository;
        this.quotaRepository = quotaRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    // ===================== 配额 =====================

    public Optional<UsageQuota> getQuota(Long orgId) {
        return quotaRepository.findByOrganizationId(orgId);
    }

    /** 全平台配额列表（超管未指定组织时） */
    public List<UsageQuota> allQuotas() {
        return quotaRepository.findAll();
    }

    @Transactional
    public UsageQuota setQuota(Long orgId, Long tokenLimit, Integer warnThreshold,
                               Long operatorId, String operatorName) {
        if (tokenLimit == null || tokenLimit <= 0) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "tokenLimit 必须为正整数");
        }
        int threshold = warnThreshold == null ? 80 : Math.max(1, Math.min(100, warnThreshold));
        UsageQuota quota = quotaRepository.findByOrganizationId(orgId).orElseGet(() ->
                UsageQuota.builder().organizationId(orgId).build());
        quota.setTokenLimit(tokenLimit);
        quota.setWarnThreshold(threshold);
        // 修改配额后允许重新触发告警（同月去重状态随阈值变化而失效）
        quota.setAlertedMonth(null);
        UsageQuota saved = quotaRepository.save(quota);
        String orgName = organizationRepository.findById(orgId).map(Organization::getName).orElse("org-" + orgId);
        auditService.log(operatorId, operatorName, "BILLING_QUOTA_SET", "Organization", orgId,
                String.format("组织「%s」设置月度配额 %d tokens，告警阈值 %d%%", orgName, tokenLimit, threshold), null);
        return saved;
    }

    // ===================== 账单 =====================

    public List<Invoice> getInvoices(Long orgId) {
        if (orgId != null) return invoiceRepository.findByOrganizationIdOrderByPeriodMonthDesc(orgId);
        return invoiceRepository.findAll().stream()
                .sorted(Comparator.comparing(Invoice::getPeriodMonth).reversed())
                .toList();
    }

    @Transactional
    public Map<String, Object> generateInvoices(String month, Long operatorId, String operatorName) {
        String target = (month == null || month.isBlank()) ? currentMonth() : month;
        // 幂等：清空该账期全部组织账单后重建
        invoiceRepository.deleteByMonth(target);

        List<Organization> orgs = organizationRepository.findAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        double totalCost = 0.0;
        for (Organization org : orgs) {
            MonthUsage u = monthUsage(org.getId(), target);
            double cost = u.inputTokens / 1000.0 * inputPricePer1k + u.outputTokens / 1000.0 * outputPricePer1k;
            Invoice inv = Invoice.builder()
                    .organizationId(org.getId())
                    .periodMonth(target)
                    .runCount(u.runCount)
                    .inputTokens(u.inputTokens)
                    .outputTokens(u.outputTokens)
                    .totalTokens(u.totalTokens)
                    .estimatedCost(round2(cost))
                    .status("DRAFT")
                    .build();
            invoiceRepository.save(inv);
            totalCost += cost;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("organizationId", org.getId());
            row.put("organizationName", org.getName());
            row.put("runCount", u.runCount);
            row.put("totalTokens", u.totalTokens);
            row.put("estimatedCost", round2(cost));
            rows.add(row);
        }
        auditService.log(operatorId, operatorName, "BILLING_INVOICE_GENERATE", "Billing", null,
                String.format("生成账期 %s 账单，覆盖 %d 个组织，合计费用 %.2f 元", target, orgs.size(), totalCost), null);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("month", target);
        res.put("orgCount", orgs.size());
        res.put("totalCost", round2(totalCost));
        res.put("invoices", rows);
        return res;
    }

    // ===================== 运行后配额检查（由 PipelineEngine 调用） =====================

    /**
     * 流水线运行结束后触发：聚合组织当月用量，超限即告警（同月去重）。
     * 非核心流程，异常吞掉不影响主流程。
     */
    public void afterRun(Long projectId) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null || project.getOrganizationId() == null) return;
            evaluateOrg(project.getOrganizationId(), project.getUserId());
        } catch (Exception e) {
            log.warn("配额检查失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /**
     * 巡检某组织当月用量并触发超限告警（同月去重）。
     * 由 finishRun 自动调用，也可由超管通过 POST /api/billing/quota/check 手动触发。
     */
    public void evaluateOrg(Long orgId, Long runningUserId) {
        try {
            if (orgId == null) return;
            UsageQuota quota = quotaRepository.findByOrganizationId(orgId).orElse(null);
            if (quota == null) return; // 未配置配额则不限制、不告警

            String month = currentMonth();
            MonthUsage usage = monthUsage(orgId, month);
            if (quota.getTokenLimit() <= 0) return;

            int pct = (int) (usage.totalTokens * 100 / quota.getTokenLimit());
            if (pct >= quota.getWarnThreshold()) {
                if (month.equals(quota.getAlertedMonth())) return; // 同月已告警，去重
                quota.setAlertedMonth(month);
                quotaRepository.save(quota);
                fireQuotaAlert(orgId, usage.totalTokens, quota.getTokenLimit(), pct, runningUserId);
            }
        } catch (Exception e) {
            log.warn("配额巡检失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private void fireQuotaAlert(Long orgId, long used, long limit, int pct, Long runningUserId) {
        Organization org = organizationRepository.findById(orgId).orElse(null);
        String orgName = org != null ? org.getName() : ("org-" + orgId);
        String detail = String.format("组织「%s」本月用量 %d tokens，已达配额 %d 的 %d%%，请关注成本控制。",
                orgName, used, limit, pct);

        // 1) 审计日志（操作人取本次运行用户，缺失则记 system）
        String operatorName = "system";
        if (runningUserId != null) {
            operatorName = userRepository.findById(runningUserId).map(User::getUsername).orElse("system");
        }
        auditService.log(runningUserId, operatorName, "BILLING_QUOTA_WARN", "Organization", orgId, detail, null);

        // 2) 站内通知：组织 ADMIN + 平台 SUPER_ADMIN（去重）
        Set<Long> recipients = new LinkedHashSet<>();
        userRepository.findByOrganizationIdAndRole(orgId, "ADMIN")
                .forEach(u -> recipients.add(u.getId()));
        userRepository.findByRole("SUPER_ADMIN")
                .forEach(u -> recipients.add(u.getId()));

        for (Long uid : recipients) {
            notificationService.send(uid, "QUOTA", "用量配额告警", detail, "/billing");
        }
        log.info("已触发组织 {} 配额告警（{}%），通知 {} 人", orgId, pct, recipients.size());
    }

    // ===================== 内部工具 =====================

    /** 聚合某组织指定账期的 LLM 用量 */
    private MonthUsage monthUsage(Long orgId, String month) {
        YearMonth ym = YearMonth.parse(month);
        Instant from = ym.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Long> pids = projectRepository.findByOrganizationId(orgId).stream()
                .map(Project::getId).toList();
        if (pids.isEmpty()) return new MonthUsage(0L, 0L, 0L, 0L);
        List<Object[]> rows = pipelineRunRepository.aggregateByProjectsInWindow(pids, from, to);
        if (rows == null || rows.isEmpty()) return new MonthUsage(0L, 0L, 0L, 0L);
        Object[] agg = rows.get(0);
        long in = ((Number) agg[0]).longValue();
        long out = ((Number) agg[1]).longValue();
        long tot = ((Number) agg[2]).longValue();
        long runs = ((Number) agg[3]).longValue();
        return new MonthUsage(in, out, tot, runs);
    }

    private static class MonthUsage {
        final long inputTokens, outputTokens, totalTokens, runCount;
        MonthUsage(long in, long out, long tot, long runs) {
            this.inputTokens = in; this.outputTokens = out; this.totalTokens = tot; this.runCount = runs;
        }
    }

    private String currentMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
