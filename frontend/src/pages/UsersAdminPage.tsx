import { useEffect, useState } from 'react';
import { adminApi } from '../api/client';
import { useToast } from '../components/ui/Toast';

interface UserRow {
  id: number;
  username: string;
  displayName: string | null;
  email: string | null;
  role: string;
  enabled: boolean;
  createdAt: string | null;
}

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  ADMIN: '管理员',
  USER: '普通用户',
};

export default function UsersAdminPage() {
  const { showToast } = useToast();
  const [users, setUsers] = useState<UserRow[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const { data } = await adminApi.listUsers();
      setUsers(data.users || []);
    } catch (e: any) {
      showToast(e.response?.data?.error || '加载用户列表失败', true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleRole = async (id: number, role: string) => {
    try {
      await adminApi.changeRole(id, role);
      showToast('角色已更新');
      load();
    } catch (e: any) {
      showToast(e.response?.data?.error || '更新失败', true);
    }
  };

  const handleToggle = async (id: number) => {
    try {
      await adminApi.toggleEnabled(id);
      showToast('状态已切换');
      load();
    } catch (e: any) {
      showToast(e.response?.data?.error || '操作失败', true);
    }
  };

  return (
    <div>
      <div className="breadcrumb"><span>系统管理</span> / <span>用户与权限</span></div>
      <h1>用户与权限管理</h1>
      <p className="muted">管理平台用户账号、全局角色与启用状态。仅超级管理员可访问。</p>

      <div className="panel full" style={{ marginTop: 16 }}>
        <div className="panel-head"><h2>用户列表（{users.length}）</h2></div>
        {loading ? (
          <div className="muted" style={{ padding: 20 }}>加载中…</div>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>用户名</th>
                <th>显示名</th>
                <th>邮箱</th>
                <th>角色</th>
                <th>状态</th>
                <th>注册时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>{u.id}</td>
                  <td>{u.username}</td>
                  <td>{u.displayName || '—'}</td>
                  <td>{u.email || '—'}</td>
                  <td>
                    <select
                      className="role-select"
                      value={u.role}
                      onChange={(e) => handleRole(u.id, e.target.value)}
                    >
                      {Object.entries(ROLE_LABELS).map(([k, v]) => (
                        <option key={k} value={k}>{v}</option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <span className={`status-badge ${u.enabled ? 'done' : 'failed'}`}>
                      {u.enabled ? '启用' : '禁用'}
                    </span>
                  </td>
                  <td className="muted">{u.createdAt?.slice(0, 10)}</td>
                  <td>
                    <button className="btn btn-sm btn-secondary" onClick={() => handleToggle(u.id)}>
                      {u.enabled ? '禁用' : '启用'}
                    </button>
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
