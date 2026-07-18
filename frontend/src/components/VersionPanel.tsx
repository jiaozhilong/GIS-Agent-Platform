import { useEffect, useState } from 'react';
import { projectApi } from '../api/client';
import { useToast } from './ui/Toast';
import { Modal } from './ui/Modal';
import { IconClock, IconPlus, IconDoc, IconSync } from './ui/icons';

interface VersionItem {
  id: number;
  versionNo: number;
  title: string;
  triggerType: string;
  note: string | null;
  createdAt: string | null;
  solutionPreview: string | null;
}

interface VersionDetail {
  id: number;
  versionNo: number;
  title: string;
  triggerType: string;
  note: string | null;
  createdAt: string | null;
  solutionText: string | null;
  contextJson: string | null;
}

const TRIGGER_LABEL: Record<string, string> = {
  AUTO_RUN: '自动生成',
  KB_RERUN: '知识库重生成',
  MANUAL: '手动保存',
};
const triggerClass = (t: string) =>
  t === 'MANUAL' ? 'badge badge-cyan' : t === 'KB_RERUN' ? 'badge badge-amber' : 'badge badge-mint';

export default function VersionPanel({ projectId, onRestored }: { projectId: number; onRestored?: () => void }) {
  const { showToast } = useToast();
  const [versions, setVersions] = useState<VersionItem[]>([]);
  const [loading, setLoading] = useState(false);

  // 保存模态
  const [saveOpen, setSaveOpen] = useState(false);
  const [saveTitle, setSaveTitle] = useState('');
  const [saveNote, setSaveNote] = useState('');
  const [saving, setSaving] = useState(false);

  // 详情模态
  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<VersionDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [showRaw, setShowRaw] = useState(false);

  // 回退确认模态
  const [restoreTarget, setRestoreTarget] = useState<VersionItem | null>(null);
  const [restoring, setRestoring] = useState(false);

  const loadVersions = async () => {
    setLoading(true);
    try {
      const { data } = await projectApi.listVersions(projectId);
      setVersions(Array.isArray(data) ? data : []);
    } catch (e: any) {
      showToast(e.response?.data?.error || '版本列表加载失败', true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadVersions(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [projectId]);

  const handleSave = async () => {
    setSaving(true);
    try {
      await projectApi.saveVersion(projectId, { title: saveTitle.trim() || undefined, note: saveNote.trim() || undefined });
      showToast('已保存当前方案为新版本');
      setSaveOpen(false);
      setSaveTitle('');
      setSaveNote('');
      await loadVersions();
    } catch (e: any) {
      showToast(e.response?.data?.error || '保存版本失败，请先运行流水线生成方案', true);
    } finally {
      setSaving(false);
    }
  };

  const handleView = async (v: VersionItem) => {
    setDetailLoading(true);
    setDetailOpen(true);
    setShowRaw(false);
    try {
      const { data } = await projectApi.getVersion(projectId, v.id);
      setDetail(data);
    } catch (e: any) {
      showToast(e.response?.data?.error || '版本详情加载失败', true);
      setDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  };

  const confirmRestore = async () => {
    if (!restoreTarget) return;
    setRestoring(true);
    try {
      const { data } = await projectApi.restoreVersion(projectId, restoreTarget.id);
      showToast(data?.message || `已回退至版本 v${restoreTarget.versionNo}`);
      setRestoreTarget(null);
      await loadVersions();
      onRestored?.();
    } catch (e: any) {
      showToast(e.response?.data?.error || '回退失败', true);
    } finally {
      setRestoring(false);
    }
  };

  return (
    <div className="panel full">
      <div className="panel-head">
        <h2><IconClock width={16} height={16} style={{ marginRight: 6, verticalAlign: '-2px' }} />方案版本</h2>
        <div className="flex gap-2" style={{ alignItems: 'center' }}>
          {versions.length > 0 && <span className="badge badge-cyan">{versions.length} 个版本</span>}
          <button className="btn btn-primary btn-sm" onClick={() => setSaveOpen(true)} disabled={saveOpen}>
            <IconPlus width={14} height={14} /> 保存当前版本
          </button>
        </div>
      </div>
      <div className="panel-body">
        {loading ? (
          <div className="empty-state"><p>加载版本列表…</p></div>
        ) : versions.length === 0 ? (
          <div className="empty-state">
            <IconClock width={26} height={26} />
            <h3>暂无版本快照</h3>
            <p>运行流水线生成方案后，可点击「保存当前版本」留存快照；每次生成/知识库重生成也会自动保存。</p>
          </div>
        ) : (
          <div className="version-list">
            {versions.map((v) => (
              <div className="version-card" key={v.id}>
                <div className={`version-no v${v.versionNo}`}>v{v.versionNo}</div>
                <div className="version-main">
                  <div className="version-title-row">
                    <span className="version-title">{v.title}</span>
                    <span className={triggerClass(v.triggerType)}>{TRIGGER_LABEL[v.triggerType] || v.triggerType}</span>
                    {v.createdAt && <span className="version-date">{new Date(v.createdAt).toLocaleString('zh-CN')}</span>}
                  </div>
                  {v.solutionPreview && <p className="version-preview">{v.solutionPreview}</p>}
                  {v.note && <p className="version-note">📝 {v.note}</p>}
                </div>
                <div className="version-ops">
                  <button className="mini-btn" onClick={() => handleView(v)}><IconDoc width={13} height={13} /> 查看</button>
                  <button className="mini-btn mini-restore" onClick={() => setRestoreTarget(v)}>
                    <IconSync width={13} height={13} /> 恢复此版本
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 保存版本模态 */}
      <Modal
        open={saveOpen}
        title="保存当前方案为新版本"
        onClose={() => !saving && setSaveOpen(false)}
        footer={
          <>
            <button className="btn btn-secondary btn-sm" onClick={() => setSaveOpen(false)} disabled={saving}>取消</button>
            <button className="btn btn-primary btn-sm" onClick={handleSave} disabled={saving}>
              {saving ? '保存中…' : '保存版本'}
            </button>
          </>
        }
      >
        <p className="modal-hint">将当前方案（最新一次运行结果）存为快照，便于后续对比与一键回退。留空则自动命名为「vN · 手动保存」。</p>
        <label className="form-label">版本标题</label>
        <input className="form-input" value={saveTitle} onChange={(e) => setSaveTitle(e.target.value)} placeholder="如：向客户汇报版" />
        <label className="form-label" style={{ marginTop: 12 }}>备注</label>
        <textarea className="edit-textarea" style={{ minHeight: 64 }} value={saveNote} onChange={(e) => setSaveNote(e.target.value)} placeholder="可选，记录此版本变更点" />
      </Modal>

      {/* 版本详情模态 */}
      <Modal
        open={detailOpen}
        title={detail ? `方案版本 v${detail.versionNo} · ${detail.title}` : '版本详情'}
        onClose={() => setDetailOpen(false)}
        footer={
          <>
            <button className="btn btn-secondary btn-sm" onClick={() => setDetailOpen(false)}>关闭</button>
            {detail && (
              <button className="btn btn-primary btn-sm" onClick={() => { setDetailOpen(false); setRestoreTarget({ id: detail.id, versionNo: detail.versionNo } as VersionItem); }}>
                <IconSync width={13} height={13} /> 恢复此版本
              </button>
            )}
          </>
        }
      >
        {detailLoading ? <p className="modal-hint">加载中…</p> : detail ? (
          <div>
            <div className="version-detail-meta">
              <span className={triggerClass(detail.triggerType)}>{TRIGGER_LABEL[detail.triggerType] || detail.triggerType}</span>
              {detail.createdAt && <span className="muted">{new Date(detail.createdAt).toLocaleString('zh-CN')}</span>}
            </div>
            {detail.solutionText && (
              <div className="doc-preview">
                <h3>方案正文</h3>
                <p style={{ whiteSpace: 'pre-wrap' }}>{detail.solutionText}</p>
              </div>
            )}
            {detail.note && <p className="version-note" style={{ marginTop: 10 }}>📝 {detail.note}</p>}
            <button className="link-btn" onClick={() => setShowRaw((s) => !s)}>
              {showRaw ? '收起完整上下文' : '查看完整上下文 (Context Bus)'}
            </button>
            {showRaw && detail.contextJson && (
              <pre className="raw-json">{typeof detail.contextJson === 'string' ? detail.contextJson : JSON.stringify(detail.contextJson, null, 2)}</pre>
            )}
          </div>
        ) : null}
      </Modal>

      {/* 回退确认模态 */}
      <Modal
        open={restoreTarget !== null}
        title="确认回退到该版本？"
        onClose={() => !restoring && setRestoreTarget(null)}
        footer={
          <>
            <button className="btn btn-secondary btn-sm" onClick={() => setRestoreTarget(null)} disabled={restoring}>取消</button>
            <button className="btn btn-primary btn-sm" onClick={confirmRestore} disabled={restoring}>
              {restoring ? '回退中…' : `回退至 v${restoreTarget?.versionNo ?? ''}`}
            </button>
          </>
        }
      >
        <p className="modal-hint">
          回退将把项目方案与最新一次运行结果恢复为该版本快照，<b>无需重新调用 LLM</b>，方案预览与下载会立即更新。此操作不影响已保存的其他版本。
        </p>
        {restoreTarget && (
          <div className="version-card static">
            <div className={`version-no v${restoreTarget.versionNo}`}>v{restoreTarget.versionNo}</div>
            <div className="version-main">
              <span className="version-title">{restoreTarget.title}</span>
              {restoreTarget.solutionPreview && <p className="version-preview">{restoreTarget.solutionPreview}</p>}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
