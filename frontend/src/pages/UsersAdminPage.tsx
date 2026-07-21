import { useEffect, useMemo, useState } from 'react';
import { adminApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { IconPlus } from '../components/ui/icons';

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
  const [search, setSearch] = useState('');

  // 邀请弹窗
  const [inviteOpen, setInviteOpen] = useState(false);
  const [invite, setInvite] = useState({ username: '', password: '', displayName: '', email: '', role: 'USER' });
  const [inviting, setInviting] = useState(false);

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

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return users;
    return users.filter((u) =>
      u.username.toLowerCase().includes(q) ||
      (u.displayName && u.displayName.toLowerCase().includes(q)) ||
      (u.email && u.email.toLowerCase().includes(q)));
  }, [users, search]);

  const handleRole = async (id: number, role: string) => {
    try { await adminApi.changeRole(id, role); showToast('角色已更新'); load(); }
    catch (e: any) { showToast(e.response?.data?.error || '更新失败', true); }
  };

  const handleToggle = async (id: number) => {
    try { await adminApi.toggleEnabled(id); showToast('状态已切换'); load(); }
    catch (e: any) { showToast(e.response?.data?.error || '操作失败', true); }
  };

  const handleInvite = async () => {
    if (!invite.username.trim() || !invite.password.trim()) {
      showToast('请填写用户名和初始密码', true); return;
    }
    setInviting(true);
    try {
      await adminApi.createUser(invite);
      showToast('用户已创建');
      setInviteOpen(false);
      setInvite({ username: '', password: '', displayName: '', email: '', role: 'USER' });
      load();
    } catch (e: any) { showToast(e.response?.data?.error || '创建失败', true); }
    finally { setInviting(false); }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>用户与权限管理</h1>
          <p>管理平台用户账号、全局角色与启用状态。仅超级管理员可访问。</p>
        </div>
        <Button variant="primary" onClick={() => setInviteOpen(true)}><IconPlus /> 邀请用户</Button>
      </div>

      <div className="panel full" style={{ marginTop: 16 }}>
        <div className="panel-head" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>用户列表（{users.length}）</h2>
          <input
            className="search-input" style={{ width: 220, margin: 0 }}
            placeholder="搜索用户名/显示名/邮箱…"
            value={search} onChange={(e) => setSearch(e.target.value)}
          />
        </div>
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
              {filtered.map((u) => (
                <tr key={u.id}>
                  <td>{u.id}</td>
                  <td style={{ fontWeight: 600 }}>{u.username}</td>
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
                    <Button variant="secondary" size="sm" onClick={() => handleToggle(u.id)}>
                      {u.enabled ? '禁用' : '启用'}
                    </Button>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr><td colSpan={8} className="muted" style={{ textAlign: 'center', padding: 24 }}>无匹配用户</td></tr>
              )}
            </tbody>
          </table>
        )}
      </div>

      <Modal
        open={inviteOpen}
        title="邀请新用户"
        onClose={() => !inviting && setInviteOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setInviteOpen(false)} disabled={inviting}>取消</Button>
            <Button variant="primary" onClick={handleInvite} loading={inviting}>创建用户</Button>
          </>
        }
      >
        <div className="field">
          <label className="label">用户名</label>
          <input className="input" placeholder="登录账号" value={invite.username}
            onChange={(e) => setInvite({ ...invite, username: e.target.value })} />
        </div>
        <div className="field">
          <label className="label">初始密码</label>
          <input className="input" type="password" placeholder="用户首次登录后建议修改" value={invite.password}
            onChange={(e) => setInvite({ ...invite, password: e.target.value })} />
        </div>
        <div className="field">
          <label className="label">显示名（可选）</label>
          <input className="input" placeholder="如：张三" value={invite.displayName}
            onChange={(e) => setInvite({ ...invite, displayName: e.target.value })} />
        </div>
        <div className="field">
          <label className="label">邮箱（可选）</label>
          <input className="input" placeholder="用于 SSO 关联" value={invite.email}
            onChange={(e) => setInvite({ ...invite, email: e.target.value })} />
        </div>
        <div className="field">
          <label className="label">角色</label>
          <select className="select" value={invite.role} onChange={(e) => setInvite({ ...invite, role: e.target.value })}>
            <option value="USER">普通用户</option>
            <option value="ADMIN">管理员</option>
            <option value="SUPER_ADMIN">超级管理员</option>
          </select>
        </div>
        <p style={{ fontSize: 12, color: 'var(--muted)', margin: '12px 0 0' }}>
          新用户将通过此用户名和密码登录。初始密码建议让用户登录后自行修改。
        </p>
      </Modal>
    </div>
  );
}
