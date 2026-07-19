import { useEffect, useState } from 'react';
import { auditApi } from '../api/client';
import { useToast } from '../components/ui/Toast';

interface AuditEntry {
  id: number;
  userId: number | null;
  username: string | null;
  action: string;
  targetType: string | null;
  targetId: number | null;
  detail: string | null;
  ipAddress: string | null;
  createdAt: string | null;
}

const ACTION_LABELS: Record<string, string> = {
  LOGIN: '登录', REGISTER: '注册', CREATE_PROJECT: '创建项目',
  RUN_PIPELINE: '运行流水线', EXPORT: '导出', INVITE_MEMBER: '邀请成员',
};

export default function AuditPage() {
  const { showToast } = useToast();
  const [logs, setLogs] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const { data } = await auditApi.list(100);
        setLogs(data.logs || []);
      } catch (e: any) {
        showToast(e.response?.data?.error || '加载审计日志失败', true);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  return (
    <div>
      <div className="breadcrumb"><span>系统管理</span> / <span>审计日志</span></div>
      <h1>操作审计日志</h1>
      <p className="muted">记录关键操作（登录、注册、创建项目等），用于安全追溯。</p>

      <div className="panel full" style={{ marginTop: 16 }}>
        <div className="panel-head"><h2>最近操作（{logs.length}）</h2></div>
        {loading ? (
          <div className="muted" style={{ padding: 20 }}>加载中…</div>
        ) : logs.length === 0 ? (
          <div className="muted" style={{ padding: 20 }}>暂无记录</div>
        ) : (
          <table className="data-table">
            <thead>
              <tr><th>时间</th><th>用户</th><th>操作</th><th>对象</th><th>IP</th><th>详情</th></tr>
            </thead>
            <tbody>
              {logs.map((l) => (
                <tr key={l.id}>
                  <td className="muted">{l.createdAt?.slice(0, 19)?.replace('T', ' ')}</td>
                  <td>{l.username || l.userId || '—'}</td>
                  <td><span className="tag">{ACTION_LABELS[l.action] || l.action}</span></td>
                  <td className="muted">{l.targetType ? `${l.targetType}#${l.targetId}` : '—'}</td>
                  <td className="muted">{l.ipAddress || '—'}</td>
                  <td className="muted" style={{ maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {l.detail || '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
