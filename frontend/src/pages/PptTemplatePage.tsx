import { useEffect, useRef, useState } from 'react';
import { pptTemplateApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { Modal } from '../components/ui/Modal';
import { IconTemplate, IconDownload, IconEdit } from '../components/ui/icons';

interface PptTemplate {
  id: number;
  name: string;
  description: string;
  fileSize: number;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

function fmtSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function fmtDate(ts?: string): string {
  if (!ts) return '';
  try { return new Date(ts).toLocaleString('zh-CN'); } catch { return ts; }
}

export default function PptTemplatePage() {
  const { showToast } = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [templates, setTemplates] = useState<PptTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [editTarget, setEditTarget] = useState<PptTemplate | null>(null);
  const [editName, setEditName] = useState('');
  const [editDesc, setEditDesc] = useState('');
  const [saving, setSaving] = useState(false);

  const load = async () => {
    try {
      const { data } = await pptTemplateApi.list();
      setTemplates(data || []);
    } catch { /* ignore */ } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleUpload = async () => {
    const file = fileInputRef.current?.files?.[0];
    if (!file) { showToast('请选择 .pptx 文件', true); return; }
    if (!file.name.toLowerCase().endsWith('.pptx')) { showToast('仅支持 .pptx 格式', true); return; }
    setUploading(true);
    try {
      await pptTemplateApi.upload(file, file.name.replace(/\.pptx$/i, ''));
      showToast('模板上传成功');
      if (fileInputRef.current) fileInputRef.current.value = '';
      await load();
    } catch (e: any) {
      showToast(e.response?.data?.error || '上传失败', true);
    } finally {
      setUploading(false);
    }
  };

  const handleSetDefault = async (id: number) => {
    try {
      await pptTemplateApi.setDefault(id);
      showToast('已设为默认模板');
      await load();
    } catch (e: any) {
      showToast(e.response?.data?.error || '操作失败', true);
    }
  };

  const handleDelete = async (id: number, name: string) => {
    if (!window.confirm(`确认删除模板「${name}」？`)) return;
    try {
      await pptTemplateApi.delete(id);
      showToast('模板已删除');
      await load();
    } catch (e: any) {
      showToast(e.response?.data?.error || '删除失败', true);
    }
  };

  const openEdit = (t: PptTemplate) => {
    setEditTarget(t);
    setEditName(t.name);
    setEditDesc(t.description || '');
  };

  const confirmEdit = async () => {
    if (!editTarget || !editName.trim()) return;
    setSaving(true);
    try {
      await pptTemplateApi.update(editTarget.id, { name: editName.trim(), description: editDesc.trim() });
      showToast('模板信息已更新');
      setEditTarget(null);
      await load();
    } catch (e: any) {
      showToast(e.response?.data?.error || '保存失败', true);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <div className="breadcrumb">
        <span>设置</span> / <span>PPT 模板管理</span>
      </div>

      <div className="project-hero">
        <div>
          <h1>PPT 模板管理</h1>
          <div className="meta-row">
            <span>上传自定义 .pptx 模板，生成方案时选择使用</span>
          </div>
        </div>
      </div>

      {/* 上传区域 */}
      <div className="panel" style={{ marginBottom: 22 }}>
        <div className="panel-head"><h2>上传新模板</h2></div>
        <div className="panel-body">
          <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
            <input
              ref={fileInputRef}
              type="file"
              accept=".pptx"
              style={{ flex: 1, minWidth: 240 }}
              className="form-input"
            />
            <button className="btn btn-primary" onClick={handleUpload} disabled={uploading}>
              {uploading ? '上传中…' : '上传模板'}
            </button>
          </div>
          <p className="text-muted" style={{ marginTop: 8 }}>
            支持 .pptx 格式。上传后可在项目详情页下载 PPT 时选择使用。
            模板中的配色、字体、Logo 等母版元素将被保留，内容由系统自动填充。
          </p>
        </div>
      </div>

      {/* 模板列表 */}
      <div className="panel">
        <div className="panel-head">
          <h2>我的模板</h2>
          {templates.length > 0 && <span className="badge badge-cyan">{templates.length} 个</span>}
        </div>
        <div className="panel-body">
          {loading ? (
            <div className="empty-state"><p>加载中…</p></div>
          ) : templates.length === 0 ? (
            <div className="empty-state">
              <IconTemplate />
              <h3>还没有上传模板</h3>
              <p>上传你的品牌 PPT 模板，生成方案时自动套用</p>
            </div>
          ) : (
            <div className="template-list">
              {templates.map((t) => (
                <div className={`tpl-card ${t.isDefault ? 'default' : ''}`} key={t.id}>
                  <div className="tpl-card-preview">
                    <IconTemplate width={36} height={36} />
                    {t.isDefault && <span className="tpl-default-badge">默认</span>}
                  </div>
                  <div className="tpl-card-body">
                    <h3 className="tpl-card-name">{t.name}</h3>
                    {t.description && <p className="tpl-card-desc">{t.description}</p>}
                    <div className="tpl-card-meta">
                      <span>{fmtSize(t.fileSize)}</span>
                      <span>{fmtDate(t.createdAt)}</span>
                    </div>
                  </div>
                  <div className="tpl-card-ops">
                    <button className="mini-btn" onClick={() => openEdit(t)}>
                      <IconEdit width={13} height={13} /> 编辑
                    </button>
                    {!t.isDefault && (
                      <button className="mini-btn" onClick={() => handleSetDefault(t.id)}>设为默认</button>
                    )}
                    <button className="mini-btn" style={{ color: 'var(--danger)' }} onClick={() => handleDelete(t.id, t.name)}>
                      删除
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* 编辑模态 */}
      <Modal
        open={editTarget !== null}
        title="编辑模板信息"
        onClose={() => !saving && setEditTarget(null)}
        footer={
          <>
            <button className="btn btn-secondary btn-sm" onClick={() => setEditTarget(null)} disabled={saving}>取消</button>
            <button className="btn btn-primary btn-sm" onClick={confirmEdit} disabled={saving || !editName.trim()}>
              {saving ? '保存中…' : '保存'}
            </button>
          </>
        }
      >
        <div className="form-group">
          <label className="form-label">模板名称</label>
          <input className="form-input" value={editName} onChange={(e) => setEditName(e.target.value)} placeholder="如：公司品牌模板" />
        </div>
        <div className="form-group">
          <label className="form-label">描述（可选）</label>
          <input className="form-input" value={editDesc} onChange={(e) => setEditDesc(e.target.value)} placeholder="如：蓝色科技风，含公司 Logo" />
        </div>
      </Modal>
    </div>
  );
}
