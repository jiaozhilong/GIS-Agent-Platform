import { useEffect, useState } from 'react';
import { teamApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { IconPlus, IconTeam, IconTrash, IconClose } from '../components/ui/icons';

const ROLE_LABEL: Record<string, string> = {
  OWNER: '所有者',
  ADMIN: '管理员',
  EDITOR: '编辑',
  MEMBER: '成员',
  VIEWER: '只读访客',
};
const ROLE_ORDER = ['OWNER', 'ADMIN', 'EDITOR', 'MEMBER', 'VIEWER'];

interface TeamSummary {
  id: number;
  name: string;
  ownerId: number;
  myRole: string;
  createdAt?: string;
}
interface MemberRow {
  userId: number;
  username: string;
  role: string;
  createdAt?: string;
}
interface TeamDetail extends TeamSummary {
  members: MemberRow[];
}

export default function TeamsPage() {
  const { showToast } = useToast();
  const [teams, setTeams] = useState<TeamSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [detail, setDetail] = useState<TeamDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState('');

  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteUser, setInviteUser] = useState('');
  const [inviteRole, setInviteRole] = useState('EDITOR');

  const loadTeams = async () => {
    try {
      const { data } = await teamApi.listMine();
      const list: TeamSummary[] = (data || []).map((t: any) => ({
        id: t.id, name: t.name, ownerId: t.ownerId, myRole: t.myRole, createdAt: t.createdAt,
      }));
      setTeams(list);
      if (activeId == null && list.length > 0) setActiveId(list[0].id);
    } catch (err: any) {
      showToast(err.response?.data?.message || '团队列表加载失败', true);
    } finally {
      setLoading(false);
    }
  };

  const loadDetail = async (id: number) => {
    setDetailLoading(true);
    try {
      const { data } = await teamApi.detail(id);
      setDetail({
        id: data.id, name: data.name, ownerId: data.ownerId, myRole: data.myRole,
        members: (data.members || []).map((m: any) => ({
          userId: m.userId, username: m.username, role: m.role, createdAt: m.createdAt,
        })),
      });
    } catch (err: any) {
      showToast(err.response?.data?.message || '团队详情加载失败', true);
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  useEffect(() => {
    loadTeams();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (activeId != null) loadDetail(activeId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId]);

  const canManage = detail ? ['OWNER', 'ADMIN'].includes(detail.myRole) : false;

  const handleCreate = async () => {
    const name = newName.trim();
    if (!name) return;
    try {
      await teamApi.create(name);
      setNewName('');
      setCreating(false);
      showToast('团队已创建', false);
      await loadTeams();
    } catch (err: any) {
      showToast(err.response?.data?.message || '创建失败', true);
    }
  };

  const handleInvite = async () => {
    const username = inviteUser.trim();
    if (!username || activeId == null) return;
    try {
      await teamApi.addMember(activeId, username, inviteRole);
      setInviteUser('');
      setInviteOpen(false);
      showToast('已邀请成员', false);
      loadDetail(activeId);
    } catch (err: any) {
      showToast(err.response?.data?.message || '邀请失败', true);
    }
  };

  const handleRole = async (userId: number, role: string) => {
    if (activeId == null) return;
    try {
      await teamApi.updateRole(activeId, userId, role);
      showToast('角色已更新', false);
      loadDetail(activeId);
    } catch (err: any) {
      showToast(err.response?.data?.message || '修改失败', true);
    }
  };

  const handleRemove = async (userId: number) => {
    if (activeId == null) return;
    if (!confirm('确认移除该成员？')) return;
    try {
      await teamApi.removeMember(activeId, userId);
      showToast('已移除成员', false);
      loadDetail(activeId);
    } catch (err: any) {
      showToast(err.response?.data?.message || '移除失败', true);
    }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>团队空间</h1>
          <p>创建团队、邀请成员，并按角色（所有者/管理员/编辑/成员/只读）协作方案项目</p>
        </div>
        <button className="btn btn-primary" onClick={() => { setCreating(true); setNewName(''); }}>
          <IconPlus /> 新建团队
        </button>
      </div>

      <div className="team-layout">
        {/* 左：团队列表 */}
        <aside className="team-list">
          {loading ? (
            <div className="empty-state" style={{ padding: 24 }}><p>加载中…</p></div>
          ) : teams.length === 0 ? (
            <div className="empty-state">
              <IconTeam width={40} height={40} />
              <h3>还没有团队</h3>
              <p>点击右上角「新建团队」开始协作</p>
            </div>
          ) : (
            teams.map((t) => (
              <button
                key={t.id}
                className={`team-card ${activeId === t.id ? 'active' : ''}`}
                onClick={() => setActiveId(t.id)}
              >
                <div className="team-card-name">{t.name}</div>
                <div className="team-card-role">我的角色：{ROLE_LABEL[t.myRole] || t.myRole}</div>
              </button>
            ))
          )}
        </aside>

        {/* 右：成员管理 */}
        <section className="team-detail">
          {detailLoading && <div className="empty-state" style={{ padding: 40 }}><p>加载中…</p></div>}
          {!detailLoading && !detail && (
            <div className="empty-state">
              <IconTeam width={48} height={48} />
              <h3>选择一个团队</h3>
              <p>从左侧选择团队以查看成员与权限</p>
            </div>
          )}
          {!detailLoading && detail && (
            <>
              <div className="team-detail-head">
                <div>
                  <h2>{detail.name}</h2>
                  <span className="team-myrole">我的角色：{ROLE_LABEL[detail.myRole] || detail.myRole}</span>
                </div>
                {canManage && (
                  <button className="btn btn-ghost" onClick={() => { setInviteOpen(true); setInviteUser(''); setInviteRole('EDITOR'); }}>
                    <IconPlus /> 邀请成员
                  </button>
                )}
              </div>

              <div className="table">
                <div className="table-header">
                  <span>成员</span>
                  <span>角色</span>
                  <span>加入时间</span>
                  <span>操作</span>
                </div>
                {detail.members.map((m) => {
                  const editable = canManage && m.role !== 'OWNER'; // 不能改/移所有者（后端也保护最后一名 OWNER）
                  return (
                    <div key={m.userId} className="table-row">
                      <span className="member-name">{m.username}{m.role === 'OWNER' ? '（所有者）' : ''}</span>
                      <span>
                        <select
                          className="role-select"
                          value={m.role}
                          disabled={!editable}
                          onChange={(e) => handleRole(m.userId, e.target.value)}
                        >
                          {ROLE_ORDER.map((r) => (
                            <option key={r} value={r}>{ROLE_LABEL[r]}</option>
                          ))}
                        </select>
                      </span>
                      <span style={{ color: 'var(--muted)', fontSize: 12 }}>{m.createdAt ? m.createdAt.slice(0, 10) : '—'}</span>
                      <span>
                        {editable && (
                          <button className="icon-btn danger" title="移除成员" onClick={() => handleRemove(m.userId)}>
                            <IconTrash />
                          </button>
                        )}
                      </span>
                    </div>
                  );
                })}
              </div>
            </>
          )}
        </section>
      </div>

      {/* 新建团队弹层 */}
      {creating && (
        <div className="modal-mask" onClick={() => setCreating(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h3>新建团队</h3>
              <button className="icon-btn" onClick={() => setCreating(false)}><IconClose /></button>
            </div>
            <input
              className="search-input"
              autoFocus
              placeholder="团队名称"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
            />
            <div className="modal-foot">
              <button className="btn btn-ghost" onClick={() => setCreating(false)}>取消</button>
              <button className="btn btn-primary" onClick={handleCreate} disabled={!newName.trim()}>创建</button>
            </div>
          </div>
        </div>
      )}

      {/* 邀请成员弹层 */}
      {inviteOpen && (
        <div className="modal-mask" onClick={() => setInviteOpen(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h3>邀请成员</h3>
              <button className="icon-btn" onClick={() => setInviteOpen(false)}><IconClose /></button>
            </div>
            <label style={{ display: 'block', marginBottom: 6, color: 'var(--muted)', fontSize: 13 }}>用户名</label>
            <input
              className="search-input"
              autoFocus
              placeholder="被邀请人的登录用户名"
              value={inviteUser}
              onChange={(e) => setInviteUser(e.target.value)}
            />
            <label style={{ display: 'block', margin: '14px 0 6px', color: 'var(--muted)', fontSize: 13 }}>指派角色</label>
            <select className="filter-select" value={inviteRole} onChange={(e) => setInviteRole(e.target.value)}>
              {ROLE_ORDER.filter((r) => detail?.myRole === 'OWNER' || r !== 'OWNER').map((r) => (
                <option key={r} value={r}>{ROLE_LABEL[r]}</option>
              ))}
            </select>
            <div className="modal-foot">
              <button className="btn btn-ghost" onClick={() => setInviteOpen(false)}>取消</button>
              <button className="btn btn-primary" onClick={handleInvite} disabled={!inviteUser.trim()}>邀请</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
