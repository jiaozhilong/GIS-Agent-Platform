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
          <h1>技能中心（Skills）</h1>
          <p>为每个流水线工具节点绑定外部能力（如社区 ppt-master、自建 API），运行时按配置替代内置逻辑</p>
        </div>
        <Button variant="primary" onClick={openAdd}><IconPlus /> 添加 Skill</Button>
      </div>

      {loading ? (
        <div className="empty-state"><p>加载中…</p></div>
      ) : skills.length === 0 ? (
        <div className="panel"><div className="empty-state">
          <IconBook />
          <h3>尚未配置 Skill</h3>
          <p>添加一个 API_ENDPOINT 或 GIT_REPO 类型的外部能力，并绑定到工具节点（如需求分析 / 架构图）</p>
        </div></div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))', gap: 20 }}>
          {skills.map((s) => (
            <div className="card" key={s.id} style={{ padding: 20 }}>
              <div className="flex-between" style={{ marginBottom: 12 }}>
                <div className="flex gap-3" style={{ alignItems: 'center' }}>
                  <span className="node-icon cyan"><IconBook /></span>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 15 }}>{s.name}</div>
                    <div style={{ fontSize: 11, color: 'var(--muted-2)' }}>{s.description || '—'}</div>
                  </div>
                </div>
                <span className={`badge ${s.type === 'API_ENDPOINT' ? 'badge-cyan' : 'badge-purple'}`}>
                  {s.type === 'API_ENDPOINT' ? 'API' : 'GIT'}
                </span>
              </div>

              <div className="flex gap-2" style={{ marginBottom: 14 }}>
                <span className="badge badge-mint">{TOOL_LABELS[s.toolType] || s.toolType}</span>
                <span className={`badge ${s.enabled ? 'badge-mint' : 'badge-idle'}`}>{s.enabled ? '已启用' : '已禁用'}</span>
              </div>

              <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 14, wordBreak: 'break-all' }}>
                {s.type === 'API_ENDPOINT'
                  ? (s.endpointUrl || '未配置端点')
                  : (s.gitRepoUrl || '未配置仓库')}
                {s.hasApiKey ? ' · 已配置密钥' : ''}
              </div>

              <div className="flex gap-2">
                <Button variant="secondary" size="sm" onClick={() => handleTest(s.id)} loading={testing === s.id}><IconSync /> 测试</Button>
                <Button variant="secondary" size="sm" onClick={() => handleToggle(s)}>{s.enabled ? '禁用' : '启用'}</Button>
                <Button variant="secondary" size="sm" onClick={() => openEdit(s)}><IconEdit /> 编辑</Button>
                <Button variant="danger" size="sm" onClick={() => handleDelete(s.id)}><IconClose /></Button>
              </div>
            </div>
          ))}
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
          <label className="label">描述</label>
          <input className="input" placeholder="该 Skill 的作用说明" value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </div>
        <div className="field">
          <label className="label">类型</label>
          <select className="select" value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value as any })}>
            <option value="API_ENDPOINT">API_ENDPOINT（HTTP 调用外部技能）</option>
            <option value="GIT_REPO">GIT_REPO（从 Git 拉取脚本，Phase 2 支持）</option>
          </select>
        </div>
        <div className="field">
          <label className="label">绑定工具节点</label>
          <select className="select" value={form.toolType} onChange={(e) => setForm({ ...form, toolType: e.target.value })}>
            {Object.entries(TOOL_LABELS).map(([k, v]) => (
              <option key={k} value={k}>{v}</option>
            ))}
          </select>
        </div>
        {form.type === 'API_ENDPOINT' ? (
          <>
            <div className="field">
              <label className="label">端点地址（endpoint_url）</label>
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
