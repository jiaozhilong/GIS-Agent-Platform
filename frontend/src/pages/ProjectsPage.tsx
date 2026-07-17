import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { projectApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { IconSearch, IconPlus } from '../components/ui/icons';
import { effectiveStatus, STATUS_LABEL, progressOf, templateLabel, fmtDate, type EffStatus } from '../utils/status';

interface ProjectRow {
  id: number;
  name: string;
  description?: string;
  templateId?: string;
  status: string;
  createdAt?: string;
  runStatus?: string;
}

export default function ProjectsPage() {
  const [rows, setRows] = useState<ProjectRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<'all' | 'running' | 'done' | 'draft'>('all');
  const navigate = useNavigate();
  const { showToast } = useToast();

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const { data } = await projectApi.list();
        const list: ProjectRow[] = (data || []).map((p: any) => ({
          id: p.id, name: p.name, description: p.description,
          templateId: p.templateId, status: p.status || 'DRAFT', createdAt: p.createdAt,
        }));
        // 拉取各项目运行态，状态更准确
        await Promise.allSettled(
          list.map(async (p) => {
            try {
              const d = await projectApi.getById(p.id);
              p.runStatus = d.data?.latestRun?.status;
            } catch { /* ignore */ }
          })
        );
        if (!cancelled) setRows(list);
      } catch (err: any) {
        if (!cancelled) showToast(err.response?.data?.error || '项目加载失败', true);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [showToast]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return rows.filter((r) => {
      const es = effectiveStatus(r.status, r.runStatus);
      if (filter !== 'all' && es !== filter) return false;
      if (q && !r.name.toLowerCase().includes(q) && !(r.description || '').toLowerCase().includes(q)) return false;
      return true;
    });
  }, [rows, search, filter]);

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>全部项目</h1>
          <p>管理您的所有方案生成项目</p>
        </div>
        <button className="btn btn-primary" onClick={() => navigate('/projects/new')}>
          <IconPlus /> 新建项目
        </button>
      </div>

      <div className="toolbar">
        <div className="search-wrap">
          <IconSearch className="search-icon" />
          <input
            className="search-input"
            placeholder="搜索项目名称或描述…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
        <select className="filter-select" value={filter} onChange={(e) => setFilter(e.target.value as any)}>
          <option value="all">全部状态</option>
          <option value="running">进行中</option>
          <option value="done">已完成</option>
          <option value="draft">草稿</option>
        </select>
      </div>

      <div className="table">
        <div className="table-header">
          <span>项目名称</span>
          <span>模板</span>
          <span>状态</span>
          <span>日期</span>
          <span>进度</span>
        </div>
        {loading ? (
          <div className="empty-state" style={{ padding: 40 }}><p>加载中…</p></div>
        ) : filtered.length === 0 ? (
          <div className="empty-state">
            <IconProjectIcon />
            <h3>暂无匹配项目</h3>
            <p>尝试调整筛选条件，或新建一个项目</p>
          </div>
        ) : (
          filtered.map((r) => {
            const es: EffStatus = effectiveStatus(r.status, r.runStatus);
            return (
              <div key={r.id} className="table-row" onClick={() => navigate(`/projects/${r.id}`)}>
                <div>
                  <div className="project-name">{r.name}</div>
                  <div className="project-client" style={{ color: 'var(--muted)', fontSize: 12 }}>{r.description || '—'}</div>
                </div>
                <span style={{ color: 'var(--muted)', fontSize: 12 }}>{templateLabel(r.templateId)}</span>
                <span className={`status-badge ${es}`}>{STATUS_LABEL[es]}</span>
                <span style={{ color: 'var(--muted)', fontSize: 12 }}>{fmtDate(r.createdAt)}</span>
                <span style={{ color: es === 'done' ? 'var(--mint)' : 'var(--text)', fontWeight: 680 }}>{progressOf(es)}%</span>
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}

function IconProjectIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} width={48} height={48}>
      <path d="M4 5h6l2 2h8v12H4V5Z" />
    </svg>
  );
}
