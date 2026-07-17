import { useEffect, useState } from 'react';
import { imaApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { IconPlus, IconBook, IconLink, IconClose, IconEdit } from '../components/ui/icons';

interface ImaConfig { id: number; kbId: string; kbName: string; kbType: string; purpose: string; searchWeight: number; enabled: boolean; }

const PURPOSE_LABELS: Record<string, string> = {
  product_doc: '产品文档', case_lib: '案例库', industry_standard: '行业标准', competitor: '竞品信息', general: '通用',
};
const PURPOSE_BADGE: Record<string, string> = {
  product_doc: 'badge-mint', case_lib: 'badge-cyan', industry_standard: 'badge-purple', competitor: 'badge-amber', general: 'badge-idle',
};

const EMPTY = { kbId: '', kbName: '', kbType: 'subscribed', purpose: 'general', searchWeight: 0.5, enabled: true };

export default function ImaConfigPage() {
  const [configs, setConfigs] = useState<ImaConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [testing, setTesting] = useState<number | null>(null);
  const { showToast } = useToast();
  const [form, setForm] = useState({ ...EMPTY });

  const fetchConfigs = async () => {
    setLoading(true);
    try { const { data } = await imaApi.listConfigs(); setConfigs(data || []); }
    catch (e: any) { showToast(e.response?.data?.error || '加载失败', true); }
    finally { setLoading(false); }
  };

  useEffect(() => { fetchConfigs(); }, [showToast]);

  const openAdd = () => { setEditingId(null); setForm({ ...EMPTY }); setModalOpen(true); };
  const openEdit = (c: ImaConfig) => {
    setEditingId(c.id);
    setForm({ kbId: c.kbId, kbName: c.kbName, kbType: c.kbType, purpose: c.purpose, searchWeight: c.searchWeight, enabled: c.enabled });
    setModalOpen(true);
  };

  const handleSave = async () => {
    if (!form.kbId.trim() || !form.kbName.trim()) { showToast('请填写知识库 ID 与名称', true); return; }
    try {
      if (editingId) await imaApi.updateConfig(editingId, form);
      else await imaApi.createConfig(form);
      showToast(editingId ? '已更新' : '添加成功');
      setModalOpen(false);
      fetchConfigs();
    } catch (e: any) { showToast(e.response?.data?.error || '保存失败', true); }
  };

  const handleDelete = async (id: number) => {
    try { await imaApi.deleteConfig(id); showToast('已删除'); fetchConfigs(); }
    catch (e: any) { showToast(e.response?.data?.error || '删除失败', true); }
  };

  const handleToggle = async (c: ImaConfig) => {
    try { await imaApi.updateConfig(c.id, { ...c, enabled: !c.enabled }); fetchConfigs(); }
    catch (e: any) { showToast(e.response?.data?.error || '更新失败', true); }
  };

  const handleTest = async (id: number) => {
    setTesting(id);
    try { const { data } = await imaApi.testConfig(id); showToast(data?.message || '连接成功'); }
    catch (e: any) { showToast(e.response?.data?.error || '连接测试失败', true); }
    finally { setTesting(null); }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>IMA 知识库配置</h1>
          <p>连接你的 IMA 知识库，配置用途和检索权重</p>
        </div>
        <Button variant="primary" onClick={openAdd}><IconPlus /> 添加知识库</Button>
      </div>

      {loading ? (
        <div className="empty-state"><p>加载中…</p></div>
      ) : configs.length === 0 ? (
        <div className="panel"><div className="empty-state">
          <IconBook />
          <h3>尚未连接知识库</h3>
          <p>添加 IMA 知识库以提升方案生成的可信度与专业度</p>
        </div></div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))', gap: 20 }}>
          {configs.map((c) => (
            <div className="card" key={c.id} style={{ padding: 20 }}>
              <div className="flex-between" style={{ marginBottom: 12 }}>
                <div className="flex gap-3" style={{ alignItems: 'center' }}>
                  <span className="node-icon purple"><IconBook /></span>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 15 }}>{c.kbName}</div>
                    <div style={{ fontSize: 11, color: 'var(--muted-2)' }}>{c.kbId}</div>
                  </div>
                </div>
                <span className={`badge ${c.kbType === 'subscribed' ? 'badge-mint' : 'badge-amber'}`}>
                  {c.kbType === 'subscribed' ? '订阅' : '自建'}
                </span>
              </div>

              <div className="flex gap-2" style={{ marginBottom: 14 }}>
                <span className={`badge ${PURPOSE_BADGE[c.purpose] || 'badge-idle'}`}>{PURPOSE_LABELS[c.purpose] || c.purpose}</span>
                <span className={`badge ${c.enabled ? 'badge-mint' : 'badge-idle'}`}>{c.enabled ? '已启用' : '已禁用'}</span>
              </div>

              <div className="field" style={{ marginBottom: 14 }}>
                <label className="label">检索权重</label>
                <div className="flex gap-3" style={{ alignItems: 'center' }}>
                  <input type="range" min={0} max={1} step={0.1} value={c.searchWeight}
                    style={{ flex: 1, accentColor: 'var(--mint)' }}
                    onChange={(e) => setConfigs((prev) => prev.map((x) => x.id === c.id ? { ...x, searchWeight: Number(e.target.value) } : x))}
                    onMouseUp={(e) => imaApi.updateConfig(c.id, { ...c, searchWeight: Number((e.target as any).value) }).then(fetchConfigs).catch(() => showToast('权重更新失败', true))} />
                  <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--mint)', width: 42 }}>{Math.round(c.searchWeight * 100)}%</span>
                </div>
              </div>

              <div className="flex gap-2">
                <Button variant="secondary" size="sm" onClick={() => handleTest(c.id)} loading={testing === c.id}><IconLink /> 测试</Button>
                <Button variant="secondary" size="sm" onClick={() => handleToggle(c)}>{c.enabled ? '禁用' : '启用'}</Button>
                <Button variant="secondary" size="sm" onClick={() => openEdit(c)}><IconEdit /> 编辑</Button>
                <Button variant="danger" size="sm" onClick={() => handleDelete(c.id)}><IconClose /></Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={modalOpen}
        title={editingId ? '编辑知识库' : '添加 IMA 知识库'}
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>取消</Button>
            <Button variant="primary" onClick={handleSave}>保存</Button>
          </>
        }
      >
        <div className="field">
          <label className="label">知识库 ID</label>
          <input className="input" placeholder="如：kb-supermap-products" value={form.kbId}
            onChange={(e) => setForm({ ...form, kbId: e.target.value })} />
        </div>
        <div className="field">
          <label className="label">知识库名称</label>
          <input className="input" placeholder="如：超图产品智答库" value={form.kbName}
            onChange={(e) => setForm({ ...form, kbName: e.target.value })} />
        </div>
        <div className="field">
          <label className="label">类型</label>
          <select className="select" value={form.kbType} onChange={(e) => setForm({ ...form, kbType: e.target.value })}>
            <option value="subscribed">订阅的知识库</option>
            <option value="owned">自建的知识库</option>
          </select>
        </div>
        <div className="field">
          <label className="label">用途</label>
          <select className="select" value={form.purpose} onChange={(e) => setForm({ ...form, purpose: e.target.value })}>
            <option value="product_doc">产品文档</option>
            <option value="case_lib">案例库</option>
            <option value="industry_standard">行业标准</option>
            <option value="competitor">竞品信息</option>
            <option value="general">通用</option>
          </select>
        </div>
        <div className="field">
          <label className="label">检索权重：{Math.round(form.searchWeight * 100)}%</label>
          <input type="range" min={0} max={1} step={0.1} value={form.searchWeight}
            style={{ width: '100%', accentColor: 'var(--mint)' }}
            onChange={(e) => setForm({ ...form, searchWeight: Number(e.target.value) })} />
        </div>
        <label className="checkbox-wrap" style={{ marginTop: 4 }}>
          <input type="checkbox" checked={form.enabled} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} />
          启用该知识库
        </label>
      </Modal>
    </div>
  );
}
