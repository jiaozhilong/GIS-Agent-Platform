import { useEffect, useRef, useState } from 'react';
import { imaApi, projectApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { Button } from '../components/ui/Button';
import { Modal } from '../components/ui/Modal';
import { IconPlus, IconBook, IconLink, IconClose, IconEdit, IconSync } from '../components/ui/icons';

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

  // 我的 IMA 凭证（按用户隔离）
  const [cred, setCred] = useState<{ configured: boolean; clientIdMasked?: string; baseUrl?: string; message?: string }>({ configured: false });
  const [credForm, setCredForm] = useState({ clientId: '', apiKey: '', baseUrl: '' });
  const [savingCred, setSavingCred] = useState(false);
  const [testingCred, setTestingCred] = useState(false);

  const fetchConfigs = async () => {
    setLoading(true);
    try { const { data } = await imaApi.listConfigs(); setConfigs(data || []); }
    catch (e: any) { showToast(e.response?.data?.error || '加载失败', true); }
    finally { setLoading(false); }
  };

  const fetchCredential = async () => {
    try { const { data } = await imaApi.getCredential(); setCred(data || { configured: false }); }
    catch (e: any) { /* 非致命 */ setCred({ configured: false }); }
  };

  useEffect(() => { fetchConfigs(); fetchCredential(); }, [showToast]);

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

  const handleSaveCred = async () => {
    if (!credForm.clientId.trim() || !credForm.apiKey.trim()) { showToast('请填写 Client ID 与 API Key', true); return; }
    setSavingCred(true);
    try {
      const { data } = await imaApi.saveCredential(credForm);
      setCred(data); setCredForm({ clientId: '', apiKey: '', baseUrl: data.baseUrl || '' });
      showToast('IMA 凭证已保存（加密存储）');
    } catch (e: any) { showToast(e.response?.data?.error || '保存失败', true); }
    finally { setSavingCred(false); }
  };

  const handleDeleteCred = async () => {
    try { await imaApi.deleteCredential(); setCred({ configured: false }); showToast('已清除 IMA 凭证'); }
    catch (e: any) { showToast(e.response?.data?.error || '清除失败', true); }
  };

  const handleTestCred = async () => {
    setTestingCred(true);
    try { const { data } = await imaApi.testCredential(); showToast(data?.message || (data?.success ? '连接成功' : '连接失败')); }
    catch (e: any) { showToast(e.response?.data?.error || '测试失败', true); }
    finally { setTestingCred(false); }
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

  // ===== 从 IMA 拉取知识库列表，勾选启用 =====
  const [kbListOpen, setKbListOpen] = useState(false);
  const [remoteKbs, setRemoteKbs] = useState<any[]>([]);
  const [selectedKbIds, setSelectedKbIds] = useState<string[]>([]);
  const [loadingKbList, setLoadingKbList] = useState(false);

  const handleFetchKbList = async () => {
    setLoadingKbList(true);
    try {
      const { data } = await imaApi.listRemoteKbs();
      const list = (data?.list || []);
      setRemoteKbs(list);
      // 默认勾选已配置并启用的库
      setSelectedKbIds(list.filter((k: any) => k.configured).map((k: any) => k.kbId));
      setKbListOpen(true);
    } catch (e: any) {
      showToast(e.response?.data?.error || '拉取知识库列表失败，请先配置 IMA 凭证', true);
    } finally {
      setLoadingKbList(false);
    }
  };

  const toggleRemoteKb = (kbId: string) => {
    setSelectedKbIds((prev) => prev.includes(kbId) ? prev.filter((x) => x !== kbId) : [...prev, kbId]);
  };

  const confirmKbList = async () => {
    try {
      for (const kb of remoteKbs) {
        const local = configs.find((c) => c.kbId === kb.kbId);
        const wantEnabled = selectedKbIds.includes(kb.kbId);
        if (local) {
          if (local.enabled !== wantEnabled) {
            await imaApi.updateConfig(local.id, { ...local, enabled: wantEnabled });
          }
        } else if (wantEnabled) {
          await imaApi.createConfig({
            kbId: kb.kbId, kbName: kb.kbName, kbType: kb.kbType || 'subscribed',
            purpose: 'general', searchWeight: 0.5, enabled: true,
          });
        }
      }
      showToast('知识库启用状态已更新');
      setKbListOpen(false);
      fetchConfigs();
    } catch (e: any) {
      showToast(e.response?.data?.error || '保存失败', true);
    }
  };

  // ===== PPT 品牌模板上传（全局导出样式）=====
  const [pptFile, setPptFile] = useState<File | null>(null);
  const [uploadingPpt, setUploadingPpt] = useState(false);
  const pptInputRef = useRef<HTMLInputElement>(null);

  const handlePptUpload = async () => {
    if (!pptFile) { showToast('请先选择 .pptx 文件', true); return; }
    setUploadingPpt(true);
    try {
      const { data } = await projectApi.uploadPptTemplate(pptFile);
      showToast('PPT 品牌模板已上传，导出时将自动套用');
      setPptFile(null);
      if (pptInputRef.current) pptInputRef.current.value = '';
      console.log('ppt-template uploaded', data?.path);
    } catch (e: any) {
      showToast(e.response?.data?.error || '上传失败', true);
    } finally {
      setUploadingPpt(false);
    }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>IMA 知识库配置</h1>
          <p>连接你的 IMA 知识库，配置用途和检索权重</p>
        </div>
        <Button variant="primary" onClick={openAdd}><IconPlus /> 添加知识库</Button>
        <Button variant="secondary" onClick={handleFetchKbList} loading={loadingKbList}>
          <IconSync /> 从 IMA 拉取
        </Button>
      </div>

      {/* 我的 IMA 凭证（按用户隔离，加密存储） */}
      <div className="panel" style={{ marginBottom: 24 }}>
        <div className="flex-between" style={{ marginBottom: 14 }}>
          <div className="flex gap-3" style={{ alignItems: 'center' }}>
            <span className="node-icon purple"><IconBook /></span>
            <div>
              <div style={{ fontWeight: 700, fontSize: 15 }}>我的 IMA 凭证</div>
              <div style={{ fontSize: 11, color: 'var(--muted-2)' }}>每个用户独立配置，仅本人可用，密钥加密存储</div>
            </div>
          </div>
          {cred.configured
            ? <span className="badge badge-mint">已配置{cred.clientIdMasked ? ` · ${cred.clientIdMasked}` : ''}</span>
            : <span className="badge badge-idle">未配置</span>}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 14, marginBottom: 14 }}>
          <div className="field" style={{ margin: 0 }}>
            <label className="label">Client ID</label>
            <input className="input" placeholder="IMA 开放平台获取的 Client ID" value={credForm.clientId}
              onChange={(e) => setCredForm({ ...credForm, clientId: e.target.value })} />
          </div>
          <div className="field" style={{ margin: 0 }}>
            <label className="label">API Key</label>
            <input className="input" type="password" placeholder="IMA 开放平台获取的 API Key" value={credForm.apiKey}
              onChange={(e) => setCredForm({ ...credForm, apiKey: e.target.value })} />
          </div>
          <div className="field" style={{ margin: 0 }}>
            <label className="label">Base URL（可选）</label>
            <input className="input" placeholder="默认 https://ima.qq.com/openapi/note/v1" value={credForm.baseUrl}
              onChange={(e) => setCredForm({ ...credForm, baseUrl: e.target.value })} />
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" size="sm" onClick={handleSaveCred} loading={savingCred}><IconLink /> 保存凭证</Button>
          <Button variant="secondary" size="sm" onClick={handleTestCred} loading={testingCred}>测试连接</Button>
          {cred.configured && <Button variant="danger" size="sm" onClick={handleDeleteCred}>清除</Button>}
        </div>
        {cred.message && <p style={{ fontSize: 12, color: 'var(--muted)', marginTop: 10, marginBottom: 0 }}>{cred.message}</p>}
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

      {/* 从 IMA 拉取知识库列表，勾选启用 */}
      <Modal
        open={kbListOpen}
        title="从 IMA 拉取知识库"
        onClose={() => setKbListOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setKbListOpen(false)}>取消</Button>
            <Button variant="primary" onClick={confirmKbList}>保存启用状态</Button>
          </>
        }
      >
        {remoteKbs.length === 0 ? (
          <div className="empty-state"><p>没有发现可访问的知识库，请确认 IMA 凭证已配置且有效</p></div>
        ) : (
          <div className="checkbox-list" style={{ maxHeight: 360, overflowY: 'auto' }}>
            {remoteKbs.map((kb: any) => (
              <label key={kb.kbId} className="checkbox-item" style={{ padding: '10px 0', borderBottom: '1px solid var(--border, rgba(255,255,255,0.06))' }}>
                <input type="checkbox" checked={selectedKbIds.includes(kb.kbId)} onChange={() => toggleRemoteKb(kb.kbId)} />
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  <span style={{ fontWeight: 600 }}>{kb.kbName}</span>
                  <span style={{ fontSize: 11, color: 'var(--muted-2)' }}>{kb.kbId} · {kb.kbType === 'owned' ? '自建' : '订阅'}{kb.docCount > 0 ? ` · ${kb.docCount} 篇` : ''}</span>
                </div>
              </label>
            ))}
          </div>
        )}
        <p style={{ fontSize: 12, color: 'var(--muted)', marginTop: 10, marginBottom: 0 }}>
          勾选的库将被启用并参与方案检索；取消勾选已配置库会停用它（不删除）。
        </p>
      </Modal>

      {/* PPT 品牌模板上传（全局导出样式） */}
      <div className="panel" style={{ marginTop: 24 }}>
        <div className="flex gap-3" style={{ alignItems: 'center', marginBottom: 12 }}>
          <span className="node-icon cyan"><IconBook /></span>
          <div>
            <div style={{ fontWeight: 700, fontSize: 15 }}>PPT 品牌模板</div>
            <div style={{ fontSize: 11, color: 'var(--muted-2)' }}>上传 .pptx 作为品牌模板，导出 PPT 时自动套用（保存在 data/templates/brand-template.pptx）</div>
          </div>
        </div>
        <div className="flex gap-3" style={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <input
            ref={pptInputRef}
            type="file"
            accept=".pptx"
            className="input"
            style={{ flex: 1, minWidth: 220 }}
            onChange={(e) => setPptFile(e.target.files && e.target.files[0] ? e.target.files[0] : null)}
          />
          <Button variant="primary" onClick={handlePptUpload} loading={uploadingPpt} disabled={!pptFile}>
            <IconBook /> 上传模板
          </Button>
          {pptFile && <span style={{ fontSize: 12, color: 'var(--muted)' }}>{pptFile.name}</span>}
        </div>
      </div>
    </div>
  );
}
