import { useEffect, useState } from 'react';
import { llmApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { IconPlus, IconBrain, IconCheck, IconClose, IconSync } from '../components/ui/icons';

interface Provider { id: number; name: string; providerType: string; endpoint: string; model?: string; isDefault: boolean; hasApiKey: boolean; }

export default function LlmConfigPage() {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [testing, setTesting] = useState<number | null>(null);
  const { showToast } = useToast();

  const [form, setForm] = useState({ name: '', providerType: 'openai_compatible', endpoint: '', apiKey: '', model: '', isDefault: false });

  const fetchProviders = async () => {
    setLoading(true);
    try {
      const { data } = await llmApi.list();
      setProviders(data || []);
    } catch (e: any) {
      showToast(e.response?.data?.error || '加载失败', true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchProviders(); }, [showToast]);

  const openModal = () => { setForm({ name: '', providerType: 'openai_compatible', endpoint: '', apiKey: '', model: '', isDefault: false }); setModalOpen(true); };

  const handleCreate = async () => {
    if (!form.name.trim() || !form.endpoint.trim()) { showToast('请填写名称与 API 地址', true); return; }
    try {
      await llmApi.create(form);
      showToast('Provider 添加成功');
      setModalOpen(false);
      fetchProviders();
    } catch (e: any) {
      showToast(e.response?.data?.error || '添加失败', true);
    }
  };

  const handleDelete = async (id: number) => {
    try { await llmApi.delete(id); showToast('已删除'); fetchProviders(); }
    catch (e: any) { showToast(e.response?.data?.error || '删除失败', true); }
  };

  const handleTest = async (id: number) => {
    setTesting(id);
    try { const { data } = await llmApi.test(id); showToast(data?.message || '连接成功'); }
    catch (e: any) { showToast(e.response?.data?.error || '连接测试失败', true); }
    finally { setTesting(null); }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>大模型配置</h1>
          <p>管理你的 LLM Provider，工具执行时可选择使用的模型</p>
        </div>
        <Button variant="primary" onClick={openModal}><IconPlus /> 添加 Provider</Button>
      </div>

      {loading ? (
        <div className="empty-state"><p>加载中…</p></div>
      ) : providers.length === 0 ? (
        <div className="panel"><div className="empty-state">
          <IconBrain />
          <h3>尚未配置模型</h3>
          <p>添加至少一个 LLM Provider 才能运行方案生成流水线</p>
        </div></div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 20 }}>
          {providers.map((p) => (
            <div className="card" key={p.id} style={{ padding: 20 }}>
              <div className="flex-between" style={{ marginBottom: 12 }}>
                <div className="flex gap-3" style={{ alignItems: 'center' }}>
                  <span className="node-icon mint"><IconBrain /></span>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 15 }}>{p.name}</div>
                    <div style={{ fontSize: 11, color: 'var(--muted-2)' }}>{p.providerType}</div>
                  </div>
                </div>
                {p.isDefault && <span className="badge badge-cyan">默认</span>}
              </div>
              <div style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 14, wordBreak: 'break-all' }}>{p.endpoint}</div>
              {p.model && <div style={{ fontSize: 11, color: 'var(--mint)', marginBottom: 14 }}>模型：{p.model}</div>}
              <div className="flex gap-2" style={{ marginBottom: 14 }}>
                <span className={`badge ${p.hasApiKey ? 'badge-mint' : 'badge-idle'}`}>
                  {p.hasApiKey ? <><IconCheck width={12} height={12} /> 已配置 Key</> : '未配置 Key'}
                </span>
              </div>
              <div className="flex gap-2">
                <Button variant="secondary" size="sm" onClick={() => handleTest(p.id)} loading={testing === p.id}>
                  <IconSync /> 测试
                </Button>
                <Button variant="danger" size="sm" onClick={() => handleDelete(p.id)}>
                  <IconClose /> 删除
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={modalOpen}
        title="添加 LLM Provider"
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>取消</Button>
            <Button variant="primary" onClick={handleCreate}>添加</Button>
          </>
        }
      >
        <div className="field">
          <label className="label">Provider 名称</label>
          <input className="input" placeholder="如：GPT-4o、DeepSeek-V3" value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </div>
        <div className="field">
          <label className="label">类型</label>
          <select className="select" value={form.providerType} onChange={(e) => setForm({ ...form, providerType: e.target.value })}>
            <option value="openai_compatible">OpenAI 兼容</option>
            <option value="local">本地模型</option>
          </select>
        </div>
        <div className="field">
          <label className="label">API 地址</label>
          <input className="input" placeholder="https://api.openai.com/v1" value={form.endpoint}
            onChange={(e) => setForm({ ...form, endpoint: e.target.value })} />
        </div>
        <div className="field">
          <label className="label">API Key</label>
          <input className="input" type="password" placeholder="sk-..." value={form.apiKey}
            onChange={(e) => setForm({ ...form, apiKey: e.target.value })} />
        </div>
        <div className="field">
          <label className="label">模型名</label>
          <input className="input" placeholder="如 deepseek-chat / gpt-4o / qwen-max" value={form.model}
            onChange={(e) => setForm({ ...form, model: e.target.value })} />
        </div>
        <label className="checkbox-wrap" style={{ marginTop: 4 }}>
          <input type="checkbox" checked={form.isDefault} onChange={(e) => setForm({ ...form, isDefault: e.target.checked })} />
          设为默认 Provider
        </label>
      </Modal>
    </div>
  );
}
