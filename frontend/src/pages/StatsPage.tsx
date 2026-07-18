import { useEffect, useMemo, useState } from 'react';
import { statsApi, teamApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { IconStats, IconTeam } from '../components/ui/icons';
import { templateLabel } from '../utils/status';

interface TrendPoint { date: string; count: number }
interface ToolStat { toolType: string; count: number; successCount: number; successRate: number; avgSeconds: number }
interface TplStat { templateId: string; count: number }
interface Overview {
  scope: string;
  totalProjects: number;
  totalRuns: number;
  completedRuns: number;
  failedRuns: number;
  successRate: number;
  avgRunSeconds: number;
  trend: TrendPoint[];
  tools: ToolStat[];
  templates: TplStat[];
}

const humanize = (s: string) =>
  (s || '').split('_').map((w) => (w ? w[0] + w.slice(1).toLowerCase() : w)).join(' ');

export default function StatsPage() {
  const { showToast } = useToast();
  const [data, setData] = useState<Overview | null>(null);
  const [loading, setLoading] = useState(true);
  const [teams, setTeams] = useState<{ id: number; name: string }[]>([]);
  const [scope, setScope] = useState<{ type: 'personal' | 'team'; teamId?: number }>({ type: 'personal' });

  const loadTeams = async () => {
    try {
      const { data } = await teamApi.listMine();
      setTeams((data || []).map((t: any) => ({ id: t.id, name: t.name })));
    } catch { /* ignore */ }
  };

  const loadOverview = async () => {
    setLoading(true);
    try {
      const { data } = await statsApi.overview(scope.type === 'team' ? scope.teamId : undefined);
      setData(data);
    } catch (err: any) {
      showToast(err.response?.data?.message || '看板数据加载失败', true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadTeams(); /* eslint-disable-next-line */ }, []);
  useEffect(() => { loadOverview(); /* eslint-disable-next-line */ }, [scope]);

  const maxTrend = useMemo(() => Math.max(1, ...(data?.trend || []).map((t) => t.count)), [data]);
  const maxTool = useMemo(() => Math.max(1, ...(data?.tools || []).map((t) => t.count)), [data]);
  const maxTpl = useMemo(() => Math.max(1, ...(data?.templates || []).map((t) => t.count)), [data]);

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>数据看板</h1>
          <p>方案生成量、成功率、工具表现与模板使用分布（近 30 天）</p>
        </div>
        <select
          className="filter-select"
          value={scope.type === 'team' ? `team:${scope.teamId}` : 'personal'}
          onChange={(e) => {
            const v = e.target.value;
            if (v === 'personal') setScope({ type: 'personal' });
            else { const id = Number(v.split(':')[1]); setScope({ type: 'team', teamId: id }); }
          }}
        >
          <option value="personal">个人视角</option>
          {teams.map((t) => <option key={t.id} value={`team:${t.id}`}>团队：{t.name}</option>)}
        </select>
      </div>

      {/* 概览卡片 */}
      <div className="stats-row">
        <StatCard label="项目数" value={data?.totalProjects ?? 0} color="mint" icon={<IconTeam />} change={scope.type === 'team' ? '团队范围' : '个人范围'} />
        <StatCard label="运行总数" value={data?.totalRuns ?? 0} color="cyan" icon={<IconStats />} change={`完成 ${data?.completedRuns ?? 0} · 失败 ${data?.failedRuns ?? 0}`} />
        <StatCard label="成功率" value={`${Math.round((data?.successRate ?? 0) * 100)}%`} color="amber" icon={<IconStats />} change="成功 / 总运行" />
        <StatCard label="平均耗时" value={`${data?.avgRunSeconds ?? 0}s`} color="purple" icon={<IconStats />} change="单次生成均时" />
      </div>

      {loading ? (
        <div className="empty-state" style={{ padding: 60 }}><p>加载中…</p></div>
      ) : data && data.totalRuns === 0 ? (
        <div className="empty-state">
          <IconStats width={48} height={48} />
          <h3>暂无生成数据</h3>
          <p>{scope.type === 'team' ? '该团队还没有运行过方案' : '去新建项目并跑一次流水线吧'}</p>
        </div>
      ) : data ? (
        <div className="content-grid">
          {/* 生成趋势 */}
          <div className="panel">
            <div className="panel-head"><h2>生成趋势（近 30 天）</h2></div>
            <div className="chart-wrap">
              <TrendChart trend={data.trend} max={maxTrend} />
            </div>
          </div>

          {/* 工具表现 */}
          <div className="panel">
            <div className="panel-head"><h2>工具表现</h2></div>
            <div className="rank-list">
              {data.tools.map((t) => (
                <div key={t.toolType} className="rank-row">
                  <div className="rank-label">{humanize(t.toolType)}</div>
                  <div className="rank-bar-track">
                    <div className="rank-bar" style={{ width: `${(t.count / maxTool) * 100}%` }} />
                  </div>
                  <div className="rank-meta">{t.count} 次 · {Math.round(t.successRate * 100)}% · {Math.round(t.avgSeconds)}s</div>
                </div>
              ))}
              {data.tools.length === 0 && <div className="empty-state"><p>暂无工具执行记录</p></div>}
            </div>
          </div>

          {/* 模板使用分布 */}
          <div className="panel" style={{ gridColumn: '1 / -1' }}>
            <div className="panel-head"><h2>模板使用分布</h2></div>
            <div className="rank-list">
              {data.templates.map((t) => (
                <div key={t.templateId} className="rank-row">
                  <div className="rank-label">{templateLabel(t.templateId)}</div>
                  <div className="rank-bar-track">
                    <div className="rank-bar alt" style={{ width: `${(t.count / maxTpl) * 100}%` }} />
                  </div>
                  <div className="rank-meta">{t.count} 次</div>
                </div>
              ))}
              {data.templates.length === 0 && <div className="empty-state"><p>暂无模板使用记录</p></div>}
            </div>
          </div>
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

function TrendChart({ trend, max }: { trend: TrendPoint[]; max: number }) {
  const W = 640, H = 180, pad = 8, n = trend.length || 1;
  const bw = (W - pad * 2) / n;
  return (
    <svg viewBox={`0 0 ${W} ${H}`} className="trend-svg" preserveAspectRatio="none">
      {trend.map((p, i) => {
        const h = (p.count / max) * (H - 28);
        const x = pad + i * bw;
        const y = H - 20 - h;
        return (
          <g key={i}>
            <rect x={x + 1} y={y} width={Math.max(1, bw - 2)} height={Math.max(0, h)} rx={2}
              fill="url(#barGrad)" />
            {i % 5 === 0 && (
              <text x={x + bw / 2} y={H - 6} fontSize={9} fill="var(--muted-2)" textAnchor="middle">
                {p.date.slice(5)}
              </text>
            )}
          </g>
        );
      })}
      <defs>
        <linearGradient id="barGrad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--mint)" />
          <stop offset="100%" stopColor="var(--cyan)" />
        </linearGradient>
      </defs>
    </svg>
  );
}
