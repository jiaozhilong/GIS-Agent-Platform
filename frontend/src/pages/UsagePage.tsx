import { useEffect, useState } from 'react';
import { usageApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { IconUsage, IconClock } from '../components/ui/icons';
import { useAuthStore } from '../stores/authStore';

interface Totals { runs: number; inputTokens: number; outputTokens: number; totalTokens: number; estimatedCost: number }
// 后端维度聚合仅返回 runs + totalTokens（input/output 拆分只在 totals 中）
interface DimRow { label: string; runs: number; totalTokens: number }
interface Summary {
  scope: string;
  window: { from: string | null; to: string | null } | null;
  totals: Totals;
  byUser: { userId: number; username: string; displayName: string | null; runs: number; totalTokens: number }[];
  byProject: { projectId: number; projectName: string; userId: number | null; runs: number; totalTokens: number }[];
  byDay: { date: string; runs: number; totalTokens: number }[];
  byOrg: { orgId: number; orgName: string; runs: number; totalTokens: number }[];
}

const fmt = (n: number | undefined) => (n ?? 0).toLocaleString('zh-CN');
const fmtCost = (n: number | undefined) => `¥${(n ?? 0).toFixed(2)}`;
const scopeLabel = (s: string) =>
  ({ self: '个人范围', org: '组织范围', project: '项目范围', all: '全平台' } as Record<string, string>)[s] || s;

// 将后端各维度数组规整为统一的 { label, runs, totalTokens }
const toDimRows = (rows: any[]): DimRow[] =>
  (rows || []).map((r) => ({
    label: r.displayName || r.username || r.projectName || r.orgName || r.date || String(r.userId ?? r.projectId ?? r.orgId ?? ''),
    runs: r.runs ?? 0,
    totalTokens: r.totalTokens ?? 0,
  }));

export default function UsagePage() {
  const { showToast } = useToast();
  const role = useAuthStore((s) => s.role);
  const isSuper = role === 'SUPER_ADMIN';

  const [data, setData] = useState<Summary | null>(null);
  const [loading, setLoading] = useState(true);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [allScope, setAllScope] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await usageApi.summary({
        ...(isSuper && allScope ? { all: true } : {}),
        ...(from ? { from } : {}),
        ...(to ? { to } : {}),
      });
      setData(data);
    } catch (err: any) {
      showToast(err.response?.data?.message || '用量数据加载失败', true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [allScope]);

  const t = data?.totals;

  return (
    <div className="usage-grid">
      <div className="page-head">
        <div>
          <h1>用量与计费</h1>
          <p>按 LLM Token 消耗聚合方案生成成本（输入 / 输出分别计价）</p>
        </div>
        <div className="filter-bar">
          <label className="date-field">
            <span>起始</span>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="date-field">
            <span>截止</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </label>
          <button className="btn-primary" onClick={load} disabled={loading}>
            {loading ? '加载中…' : '查询'}
          </button>
          {isSuper && (
            <label className="switch-field" title="超管可查看全平台用量">
              <input type="checkbox" checked={allScope} onChange={(e) => setAllScope(e.target.checked)} />
              <span>全平台</span>
            </label>
          )}
        </div>
      </div>

      {data && (
        <div className="usage-scope-tag">
          当前范围：<b>{scopeLabel(data.scope)}</b>
          {data.window?.from || data.window?.to ? (
            <span> · {data.window?.from || '最早'} ~ {data.window?.to || '今天'}</span>
          ) : null}
        </div>
      )}

      <div className="stats-row">
        <StatCard label="运行次数" value={fmt(t?.runs)} color="mint" icon={<IconUsage />} change="聚合时段内" />
        <StatCard label="输入 Tokens" value={fmt(t?.inputTokens)} color="cyan" icon={<IconUsage />} change="prompt tokens" />
        <StatCard label="输出 Tokens" value={fmt(t?.outputTokens)} color="amber" icon={<IconUsage />} change="completion tokens" />
        <StatCard label="合计 Tokens" value={fmt(t?.totalTokens)} color="purple" icon={<IconUsage />} change="输入 + 输出" />
        <StatCard label="预估费用" value={fmtCost(t?.estimatedCost)} color="mint" icon={<IconClock />} change="¥/1k × 用量" />
      </div>

      {loading ? (
        <div className="empty-state" style={{ padding: 60 }}><p>加载中…</p></div>
      ) : data && (t?.runs ?? 0) === 0 ? (
        <div className="empty-state">
          <IconUsage width={48} height={48} />
          <h3>暂无用量数据</h3>
          <p>该范围内还没有完成方案生成，或所选时间窗内无记录</p>
        </div>
      ) : data ? (
        <div className="content-grid">
          {data.byUser.length > 0 && (
            <DimTable title="按用户" rows={toDimRows(data.byUser)} />
          )}
          {data.byOrg.length > 0 && (
            <DimTable title="按组织" rows={toDimRows(data.byOrg)} />
          )}
          {data.byProject.length > 0 && (
            <DimTable title="按项目" rows={toDimRows(data.byProject)} />
          )}
          {data.byDay.length > 0 && (
            <div className="panel" style={{ gridColumn: '1 / -1' }}>
              <div className="panel-head"><h2>按日期</h2></div>
              <DimTable title="" rows={toDimRows(data.byDay)} bare />
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}

function StatCard({ label, value, color, icon, change }: { label: string; value: number | string; color: string; icon: React.ReactNode; change: string }) {
  return (
    <div className="stat-card">
      <div className="stat-head">
        <span className="stat-label">{label}</span>
        <span className={`stat-icon ${color}`}>{icon}</span>
      </div>
      <div className={`stat-value ${color}`}>{value}</div>
      <div className="stat-change">{change}</div>
    </div>
  );
}

function DimTable({ title, rows, bare }: { title: string; rows: DimRow[]; bare?: boolean }) {
  return (
    <div className="panel">
      {!bare && <div className="panel-head"><h2>{title}</h2></div>}
      <div className="dim-table">
        <div className="dim-row dim-head">
          <span>{title || '日期'}</span>
          <span>运行次数</span>
          <span>总 Tokens</span>
        </div>
        {rows.map((r, i) => (
          <div key={i} className="dim-row">
            <span>{r.label}</span>
            <span>{fmt(r.runs)}</span>
            <span>{fmt(r.totalTokens)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
