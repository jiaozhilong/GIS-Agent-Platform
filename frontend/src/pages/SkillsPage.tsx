import { useEffect, useState } from 'react';
import { skillApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { IconPlus, IconEdit, IconClose, IconSync, IconBook } from '../components/ui/icons';

interface Skill {
  id: number;
  name: string;
  description?: string;
  type: 'API_ENDPOINT' | 'GIT_REPO';
  toolType: string;
  endpointUrl?: string;
  requestTemplate?: string;
  gitRepoUrl?: string;
  gitRef?: string;
  enabled: boolean;
  hasApiKey?: boolean;
}

const TOOL_LABELS: Record<string, string> = {
  REQUIREMENT_ANALYSIS: '需求分析', PRODUCT_MATCHING: '产品选型', CASE_RECOMMEND: '案例推荐',
  COMPETITIVE_ANALYSIS: '竞品对比', ARCHITECTURE_DIAGRAM: '架构图', SOLUTION_OUTLINE: '方案框架',
  SOLUTION_QC: '方案质检', SOLUTION_OUTPUT: '方案输出',
};

const TOOL_COLORS: Record<string, string> = {
  REQUIREMENT_ANALYSIS: 'mint', PRODUCT_MATCHING: 'cyan', CASE_RECOMMEND: 'amber',
  COMPETITIVE_ANALYSIS: 'amber', ARCHITECTURE_DIAGRAM: 'purple', SOLUTION_OUTLINE: 'cyan',
  SOLUTION_QC: 'amber', SOLUTION_OUTPUT: 'mint',
};

const EMPTY = {
  name: '', description: '', type: 'API_ENDPOINT', toolType: 'REQUIREMENT_ANALYSIS',
  endpointUrl: '', apiKey: '', requestTemplate: '', gitRepoUrl: '', gitRef: '', enabled: true,
};

export default function SkillsPage() {
  const [skills, setSkills] = useState<Skill[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [testing, setTesting] = useState<number | null>(null);
  const { showToast } = useToast();
  const [form, setForm] = useState({ ...EMPTY });

  const fetchSkills = async () => {
    setLoading(true);
    try { const { data } = await skillApi.list(); setSkills(data || []); }
    catch (e: any) { showToast(e.response?.data?.error || '加载失败', true); }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchSkills(); }, [showToast]);

  const openAdd = () => { setEditingId(null); setForm({ ...EMPTY }); setModalOpen(true); };
  const openEdit = (s: Skill) => {
    setEditingId(s.id);
    setForm({
      name: s.name, description: s.description || '', type: s.type, toolType: s.toolType,
      endpointUrl: s.endpointUrl || '', apiKey: '', requestTemplate: s.requestTemplate || '',
      gitRepoUrl: s.gitRepoUrl || '', gitRef: s.gitRef || '', enabled: s.enabled,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    if (!form.name.trim()) { showToast('请填写名称', true); return; }
    if (form.type === 'API_ENDPOINT' && !form.endpointUrl.trim()) { showToast('API_ENDPOINT 类型需填写端点地址', true); return; }
    if (form.type === 'GIT_REPO' && !form.gitRepoUrl.trim()) { showToast('GIT_REPO 类型需填写仓库地址', true); return; }
    try {
      if (editingId) await skillApi.update(editingId, form);
      else await skillApi.create(form);
      showToast(editingId ? '已更新' : '创建成功');
      setModalOpen(false);
      fetchSkills();
    } catch (e: any) { showToast(e.response?.data?.error || '保存失败', true); }
  };

  const handleDelete = async (id: number) => {
    try { await skillApi.remove(id); showToast('已删除'); fetchSkills(); }
    catch (e: any) { showToast(e.response?.data?.error || '删除失败', true); }
  };

  const handleToggle = async (s: Skill) => {
    try { await skillApi.update(s.id, { ...s, enabled: !s.enabled }); fetchSkills(); }
    catch (e: any) { showToast(e.response?.data?.error || '更新失败', true); }
  };

  const handleTest = async (id: number) => {
    setTesting(id);
    try { const { data } = await skillApi.test(id); showToast(data?.message || (data?.success ? '连接成功' : '连接失败')); }
    catch (e: any) { showToast(e.response?.data?.error || '测试失败', true); }
    finally { setTesting(null); }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>技能中心</h1>
          <p>为每个流水线工具节点绑定外部能力（API 端点 / Git 仓库），运行时按配置替代内置逻辑</p>
        </div>
        <Button variant="primary" onClick={openAdd}><IconPlus /> 添加 Skill</Button>
      </div>

      {loading ? (
        <div className="empty-state"><p>加载中…</p></div>
      ) : skills.length === 0 ? (
        <div className="panel"><div className="empty-state">
          <IconBook />
          <h3>尚未配置 Skill</h3>
          <p>点击「添加 Skill」为需求分析、产品选型、PPT 输出等工具节点绑定外部技能服务</p>
        </div></div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(400px, 1fr))', gap: 20 }}>
          {skills.map((s) => {
            const toolLabel = TOOL_LABELS[s.toolType] || s.toolType;
            const colorKey = TOOL_COLORS[s.toolType] || 'cyan';
            return (
              <div className="card" key={s.id} style={{ padding: 0, overflow: 'hidden' }}>
                {/* 卡片头部：名称 + 状态 */}
                <div style={{
                  padding: '16px 20px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
                  borderBottom: '1px solid var(--border-color, rgba(255,255,255,0.05))',
                }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 700, fontSize: 15, marginBottom: 4 }}>{s.name}</div>
                    {s.description && (
                      <div style={{ fontSize: 12, color: 'var(--muted-2)', lineHeight: 1.5 }}>{s.description}</div>
                    )}
                  </div>
                  <span className={`badge ${s.enabled ? 'badge-mint' : 'badge-idle'}`} style={{ flexShrink: 0, marginLeft: 12 }}>
                    {s.enabled ? '已启用' : '已禁用'}
                  </span>
                </div>

                {/* 字段区 */}
                <div style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span className={`badge badge-${colorKey}`} style={{ fontSize: 11 }}>{toolLabel}</span>
                    <span className={`badge ${s.type === 'API_ENDPOINT' ? 'badge-cyan' : 'badge-purple'}`} style={{ fontSize: 11 }}>
                      {s.type === 'API_ENDPOINT' ? 'API 端点' : 'Git 仓库'}
                    </span>
                  </div>

                  {/* 配置摘要 */}
                  <div style={{ fontSize: 12, color: 'var(--muted)', lineHeight: 1.8 }}>
                    {s.type === 'API_ENDPOINT' ? (
                      <>
                        <div>端点：<code style={{ fontSize: 11, wordBreak: 'break-all' }}>{s.endpointUrl || '—'}</code></div>
                        <div>密钥：{s.hasApiKey ? '✅ 已配置' : '⚠️ 未配置'}</div>
                        {s.requestTemplate && <div>模板：<code style={{ fontSize: 10 }}>{s.requestTemplate.slice(0, 60)}{s.requestTemplate.length > 60 ? '…' : ''}</code></div>}
                      </>
                    ) : (
                      <>
                        <div>仓库：<code style={{ fontSize: 11, wordBreak: 'break-all' }}>{s.gitRepoUrl || '—'}</code></div>
                        <div>分支：<code style={{ fontSize: 11 }}>{s.gitRef || 'main（默认）'}</code></div>
                      </>
                    )}
                  </div>
                </div>

                {/* 操作区 */}
                <div style={{
                  padding: '12px 20px', display: 'flex', gap: 8,
                  borderTop: '1px solid var(--border-color, rgba(255,255,255,0.05))',
                  background: 'rgba(255,255,255,0.01)',
                }}>
                  <Button variant="secondary" size="sm" onClick={() => handleTest(s.id)} loading={testing === s.id}>
                    <IconSync /> 测试
                  </Button>
                  <Button variant="secondary" size="sm" onClick={() => handleToggle(s)}>
                    {s.enabled ? '禁用' : '启用'}
                  </Button>
                  <Button variant="secondary" size="sm" onClick={() => openEdit(s)}>
                    <IconEdit /> 编辑
                  </Button>
                  <div style={{ flex: 1 }} />
                  <Button variant="danger" size="sm" onClick={() => handleDelete(s.id)}>
                    <IconClose />
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      <Modal
        open={modalOpen}
        title={editingId ? '编辑 Skill' : '添加 Skill'}
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>取消</Button>
            <Button variant="primary" onClick={handleSave}>保存</Button>
          </>
        }
      >
        <div className="field">
          <label className="label">名称</label>
          <input className="input" placeholder="如：ppt-master / 我的需求分析 API" value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </div>
        <div className="field">
          <label className="label">描述（可选）</label>
          <input className="input" placeholder="该 Skill 的作用说明" value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </div>
        <div className="field">
          <label className="label">类型</label>
          <select className="select" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as any })}>
            <option value="API_ENDPOINT">API_ENDPOINT — HTTP 调用外部技能</option>
            <option value="GIT_REPO">GIT_REPO — 从 Git 拉取脚本（Phase 2 执行）</option>
          </select>
        </div>
        <div className="field">
          <label className="label">绑定工具节点</label>
          <select className="select" value={form.toolType} onChange={(e) => setForm({ ...form, toolType: e.target.value })}>
            {Object.entries(TOOL_LABELS).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
          <p style={{ fontSize: 11, color: 'var(--muted-2)', margin: '4px 0 0' }}>
            运行时若该工具节点存在已启用 API_ENDPOINT Skill，将以外部调用替代内置逻辑
          </p>
        </div>
        {form.type === 'API_ENDPOINT' ? (
          <>
            <div className="field">
              <label className="label">端点地址</label>
              <input className="input" placeholder="https://your-skill-service/run" value={form.endpointUrl}
                onChange={(e) => setForm({ ...form, endpointUrl: e.target.value })} />
            </div>
            <div className="field">
              <label className="label">API Key（可选，加密存储）</label>
              <input className="input" type="password" placeholder={editingId ? '留空则不修改' : '技能服务鉴权密钥'}
                value={form.apiKey} onChange={(e) => setForm({ ...form, apiKey: e.target.value })} />
            </div>
            <div className="field">
              <label className="label">请求模板 / 附加 Prompt（可选）</label>
              <textarea className="edit-textarea" style={{ minHeight: 70 }} placeholder="发送给技能服务的附加说明，{context} 占位由引擎替换"
                value={form.requestTemplate} onChange={(e) => setForm({ ...form, requestTemplate: e.target.value })} />
            </div>
          </>
        ) : (
          <>
            <div className="field">
              <label className="label">Git 仓库地址</label>
              <input className="input" placeholder="https://github.com/owner/skill-repo" value={form.gitRepoUrl}
                onChange={(e) => setForm({ ...form, gitRepoUrl: e.target.value })} />
            </div>
            <div className="field">
              <label className="label">分支 / Tag / Commit（可选）</label>
              <input className="input" placeholder="main" value={form.gitRef}
                onChange={(e) => setForm({ ...form, gitRef: e.target.value })} />
            </div>
          </>
        )}
        <label className="checkbox-wrap" style={{ marginTop: 4 }}>
          <input type="checkbox" checked={form.enabled} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} />
          启用该 Skill（启用后运行时将替代对应内置工具）
        </label>
      </Modal>
    </div>
  );
}
