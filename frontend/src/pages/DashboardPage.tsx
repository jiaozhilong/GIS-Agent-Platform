import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { projectApi, imaApi, llmApi, skillApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import {
  IconProject, IconCheck, IconBrain, IconBook,
  IconPlus, IconFlow, IconSearch, IconChevronRight, IconTemplate,
} from '../components/ui/icons';

type EffStatus = 'running' | 'done' | 'failed' | 'draft' | 'pending';

interface ProjectItem {
  id: number;
  name: string;
  description?: string;
  status: string;
  createdAt?: string;
  effStatus?: EffStatus;
}

const STATUS_LABEL: Record<EffStatus, string> = {
  running: '进行中',
  done: '已完成',
  failed: '失败',
  draft: '草稿',
  pending: '排队中',
};

function effectiveStatus(p: ProjectItem): EffStatus {
  const s = (p.effStatus || p.status || 'DRAFT').toUpperCase();
  if (s === 'RUNNING') return 'running';
  if (s === 'PENDING') return 'pending';
  if (s === 'SUCCESS' || s === 'PARTIAL') return 'done';
  if (s === 'FAILED') return 'failed';
  return 'draft';
}

export default function DashboardPage() {
  const [projects, setProjects] = useState<ProjectItem[]>([]);
  const [kbCount, setKbCount] = useState(0);
  const [providerCount, setProviderCount] = useState(0);
  const [skillCount, setSkillCount] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<'all' | 'running' | 'done'>('all');
  const [search, setSearch] = useState('');
  const navigate = useNavigate();
  const { showToast } = useToast();

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [projRes, kbRes, provRes, skillRes] = await Promise.all([
          projectApi.list(),
          imaApi.listConfigs(),
          llmApi.list(),
          skillApi.list(),
        ]);
        if (cancelled) return;
        const list: ProjectItem[] = (projRes.data || []).map((p: any) => ({
          id: p.id,
          name: p.name,
          description: p.description,
          status: p.status || 'DRAFT',
          createdAt: p.createdAt,
        }));
        // 拉取最近 5 个项目的运行态，使状态更准确
        const recent = [...list].sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || '')).slice(0, 5);
        await Promise.allSettled(
          recent.map(async (p) => {
            try {
              const { data } = await projectApi.getById(p.id);
              if (data?.latestRun?.status) p.effStatus = data.latestRun.status as EffStatus;
            } catch { /* ignore */ }
          })
        );
        setProjects(list);
        setKbCount((kbRes.data || []).length);
        setProviderCount((provRes.data || []).length);
        setSkillCount((skillRes.data?.total) ?? 0);
      } catch (err: any) {
        if (!cancelled) showToast(err.response?.data?.error || '数据加载失败', true);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [showToast]);

  const recent = useMemo(
    () => [...projects].sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || '')).slice(0, 6),
    [projects]
  );

  const stats = useMemo(() => {
    const running = projects.filter((p) => ['running', 'pending'].includes(effectiveStatus(p))).length;
    const done = projects.filter((p) => effectiveStatus(p) === 'done').length;
    return { running, done };
  }, [projects]);

  const activities = useMemo(() => {
    const acts: { text: string; time: string; dot: string }[] = [];
    recent.slice(0, 4).forEach((p) => {
      acts.push({ text: `项目 <strong>${p.name}</strong> 已创建`, time: fmtTime(p.createdAt), dot: 'mint' });
    });
    if (kbCount > 0) acts.push({ text: `已连接 <strong>${kbCount}</strong> 个 IMA 知识库`, time: '当前', dot: 'amber' });
    if (providerCount > 0) acts.push({ text: `已配置 <strong>${providerCount}</strong> 个大模型 Provider`, time: '当前', dot: 'cyan' });
    return acts;
  }, [recent, kbCount, providerCount]);

  const filtered = recent.filter((p) => {
    const es = effectiveStatus(p);
    if (tab === 'running' && !['running', 'pending'].includes(es)) return false;
    if (tab === 'done' && es !== 'done') return false;
    if (search && !p.name.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>工作台</h1>
          <p>欢迎回来，以下是您的方案工作概览</p>
        </div>
        <div className="flex gap-3">
          <button className="btn btn-secondary" onClick={() => navigate('/templates')}>
            <IconTemplate /> 浏览模板
          </button>
          <button className="btn btn-primary" onClick={() => navigate('/projects/new')}>
            <IconPlus /> 新建项目
          </button>
        </div>
      </div>

      {/* Stats */}
      <div className="stats-row">
        <StatCard label="进行中项目" value={stats.running} color="mint" icon={<IconProject />} change={`共 ${projects.length} 个项目`} />
        <StatCard label="已完成方案" value={stats.done} color="cyan" icon={<IconCheck />} change="流水线已交付" />
        <StatCard label="可编排 Skills" value={skillCount ?? 0} color="amber" icon={<IconFlow />} change="平台内置能力" />
        <StatCard label="知识库连接" value={kbCount} color="purple" icon={<IconBook />} change={kbCount > 0 ? '全部在线' : '待配置'} />
      </div>

      <div className="content-grid">
        {/* Recent Projects */}
        <div className="panel">
          <div className="panel-head">
            <h2>最近项目</h2>
            <div className="tab-row">
              <button className={`tab-btn ${tab === 'all' ? 'active' : ''}`} onClick={() => setTab('all')}>全部</button>
              <button className={`tab-btn ${tab === 'running' ? 'active' : ''}`} onClick={() => setTab('running')}>进行中</button>
              <button className={`tab-btn ${tab === 'done' ? 'active' : ''}`} onClick={() => setTab('done')}>已完成</button>
            </div>
          </div>
          <div className="search-wrap" style={{ margin: '16px 20px 0' }}>
            <IconSearch className="search-icon" />
            <input
              className="search-input"
              placeholder="搜索项目名称…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <div style={{ padding: '8px 12px' }}>
            {loading ? (
              <div className="empty-state"><p>加载中…</p></div>
            ) : filtered.length === 0 ? (
              <div className="empty-state">
                <IconProject />
                <h3>暂无项目</h3>
                <p>点击右上角「新建项目」开始生成方案</p>
              </div>
            ) : (
              filtered.map((p) => {
                const es = effectiveStatus(p);
                return (
                  <div key={p.id} className="project-item" onClick={() => navigate(`/projects/${p.id}`)}>
                    <div>
                      <div className="project-name">{p.name}</div>
                      <div className="project-meta">
                        <span>{fmtDate(p.createdAt)}</span>
                        <span>{p.description || '—'}</span>
                      </div>
                    </div>
                    <div className="project-status">
                      <span className={`status-badge ${es}`}>{STATUS_LABEL[es]}</span>
                      <IconChevronRight width={16} height={16} style={{ color: 'var(--muted-2)' }} />
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Right column */}
        <div className="col-stack">
          <div className="panel">
            <div className="panel-head"><h2>快速操作</h2></div>
            <div className="quick-actions">
              <div className="quick-card" onClick={() => navigate('/projects/new')}>
                <IconPlus style={{ color: 'var(--mint)' }} />
                <div className="q-title">新建项目</div>
                <div className="q-desc">上传需求文档</div>
              </div>
              <div className="quick-card" onClick={() => navigate('/pipeline')}>
                <IconFlow style={{ color: 'var(--cyan)' }} />
                <div className="q-title">流程编排</div>
                <div className="q-desc">拖拽组合节点</div>
              </div>
              <div className="quick-card" onClick={() => navigate('/settings/llm')}>
                <IconBrain style={{ color: 'var(--amber)' }} />
                <div className="q-title">配置模型</div>
                <div className="q-desc">LLM Provider</div>
              </div>
              <div className="quick-card" onClick={() => navigate('/settings/ima')}>
                <IconBook style={{ color: 'var(--purple)' }} />
                <div className="q-title">知识库</div>
                <div className="q-desc">IMA 配置</div>
              </div>
            </div>
          </div>

          <div className="panel">
            <div className="panel-head"><h2>最近动态</h2></div>
            <div style={{ padding: '12px 16px' }}>
              {activities.length === 0 ? (
                <div className="empty-state"><p>暂无动态</p></div>
              ) : (
                activities.map((a, i) => (
                  <div className="activity-item" key={i}>
                    <span className={`activity-dot ${a.dot}`} />
                    <div className="activity-body">
                      <div className="activity-text" dangerouslySetInnerHTML={{ __html: a.text }} />
                      <div className="activity-time">{a.time}</div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value, color, icon, change }: { label: string; value: number; color: string; icon: ReactNode; change: string }) {
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

function fmtDate(s?: string) {
  if (!s) return '—';
  return s.slice(0, 10);
}
function fmtTime(s?: string) {
  if (!s) return '未知';
  return s.slice(0, 10);
}
