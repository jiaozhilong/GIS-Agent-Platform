import { useEffect, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { projectApi, toolApi, downloadBlob } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { Modal } from '../components/ui/Modal';
import {
  IconProject, IconDownload, IconPlay, IconEdit,
  IconDoc, IconSearch, IconBrain, IconTemplate, IconCheck, IconSync,
} from '../components/ui/icons';
import { effectiveStatus, STATUS_LABEL, templateLabel, fmtDate, type EffStatus } from '../utils/status';

interface ToolStatus { execId: number; toolType: string; toolOrder: number; status: string; output?: any; errorMessage?: string; }
interface Detail { id: number; name: string; description?: string; templateId?: string; status: string; createdAt?: string; documents?: any[]; latestRun?: { id: number; status: string; tools: ToolStatus[] }; }

const STEP_DEFS = [
  { key: '需求输入', tool: 'REQUIREMENT_ANALYSIS', desc: '解析需求文档', color: 'mint', icon: <IconDoc /> },
  { key: 'Agent 分析', tool: 'PRODUCT_MATCHING', desc: '产品与能力匹配', color: 'cyan', icon: <IconBrain /> },
  { key: '知识检索', tool: 'CASE_RECOMMEND', desc: '检索案例与竞品', color: 'amber', icon: <IconSearch /> },
  { key: '方案生成', tool: 'SOLUTION_OUTPUT', desc: '组装方案内容', color: 'purple', icon: <IconDoc /> },
  { key: 'PPT 交付', tool: 'PPT_OUTPUT', desc: '导出可编辑成果', color: 'mint', icon: <IconTemplate /> },
];

const TOOL_LABEL: Record<string, string> = {
  REQUIREMENT_ANALYSIS: '需求分析', PRODUCT_MATCHING: '产品匹配', CASE_RECOMMEND: '案例推荐',
  COMPETITIVE_ANALYSIS: '竞品对比', ARCHITECTURE_DIAGRAM: '架构图生成', SOLUTION_OUTLINE: '方案框架',
  SOLUTION_QC: '方案质检', SOLUTION_OUTPUT: '方案输出', PPT_OUTPUT: 'PPT 输出',
};

/** 将产物转为可编辑文本：对象/数组 → 美化 JSON；字符串（如最终方案 Markdown）→ 原文 */
function outputToText(output: any): string {
  if (output == null) return '';
  if (typeof output === 'string') return output;
  try { return JSON.stringify(output, null, 2); } catch { return String(output); }
}

export default function ProjectDetailPage() {
  const { id } = useParams();
  const projectId = Number(id);
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [detail, setDetail] = useState<Detail | null>(null);
  const [runStatus, setRunStatus] = useState<string>('NO_RUN');
  const [tools, setTools] = useState<ToolStatus[]>([]);
  const [context, setContext] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const pollRef = useRef<number | null>(null);

  // 编辑模态
  const [editTarget, setEditTarget] = useState<ToolStatus | null>(null);
  const [editText, setEditText] = useState('');
  const [savingEdit, setSavingEdit] = useState(false);
  const [rerunning, setRerunning] = useState<number | null>(null);

  const loadOnce = async () => {
    try {
      const { data: d } = await projectApi.getById(projectId);
      setDetail(d);
    } catch (e: any) {
      showToast(e.response?.data?.error || '项目加载失败', true);
    }
  };

  const poll = async () => {
    try {
      const { data } = await projectApi.status(projectId);
      if (data.status === 'NO_RUN') {
        setRunStatus('NO_RUN');
        return;
      }
      setRunStatus(data.status);
      setTools(data.tools || []);
      setContext(data.context || null);
      if (['SUCCESS', 'PARTIAL', 'FAILED'].includes(data.status)) {
        setRunning(false);
        setRerunning(null);
        if (pollRef.current) window.clearInterval(pollRef.current);
      }
    } catch { /* ignore */ }
  };

  useEffect(() => {
    let cancelled = false;
    (async () => {
      await loadOnce();
      await poll();
      if (!cancelled) setLoading(false);
    })();
    return () => { cancelled = true; if (pollRef.current) window.clearInterval(pollRef.current); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const handleRun = async () => {
    setRunning(true);
    try {
      await projectApi.run(projectId);
      showToast('流水线已启动，正在生成方案…');
      poll();
      if (pollRef.current) window.clearInterval(pollRef.current);
      pollRef.current = window.setInterval(poll, 3000);
    } catch (e: any) {
      setRunning(false);
      showToast(e.response?.data?.error || '启动失败，请确认已配置 LLM Provider', true);
    }
  };

  const handleDownload = async (type: 'md' | 'docx' | 'pptx') => {
    try {
      const { data } = type === 'md' ? await projectApi.downloadMd(projectId)
        : type === 'docx' ? await projectApi.downloadDocx(projectId)
        : await projectApi.downloadPptx(projectId);
      const ext = type === 'md' ? 'md' : type === 'docx' ? 'docx' : 'pptx';
      const name = `${(detail?.name || 'solution').replace(/\s+/g, '_')}.${ext}`;
      downloadBlob(data, name);
      showToast('下载已开始');
    } catch (e: any) {
      showToast(e.response?.data?.error || '下载失败', true);
    }
  };

  // ===== 编辑中间产物 =====
  const openEdit = (t: ToolStatus) => {
    setEditTarget(t);
    setEditText(outputToText(t.output));
  };
  const confirmEdit = async () => {
    if (!editTarget) return;
    setSavingEdit(true);
    try {
      await toolApi.updateOutput(projectId, editTarget.execId, editText);
      showToast('中间产物已保存，方案预览已同步更新');
      setEditTarget(null);
      await poll();
    } catch (e: any) {
      showToast(e.response?.data?.error || '保存失败，请检查格式', true);
    } finally {
      setSavingEdit(false);
    }
  };

  // ===== 重跑下游 =====
  const handleRerun = async (t: ToolStatus) => {
    setRerunning(t.toolOrder);
    try {
      const runId = (await projectApi.status(projectId)).data?.pipelineRunId;
      if (!runId) { showToast('未找到运行记录', true); setRerunning(null); return; }
      await projectApi.rerun(projectId, runId, t.toolOrder);
      showToast(`已从「${TOOL_LABEL[t.toolType] || t.toolType}」之后重跑下游`);
      if (pollRef.current) window.clearInterval(pollRef.current);
      pollRef.current = window.setInterval(poll, 3000);
    } catch (e: any) {
      showToast(e.response?.data?.error || '重跑启动失败', true);
      setRerunning(null);
    }
  };

  if (loading && !detail) {
    return <div className="empty-state"><p>加载中…</p></div>;
  }

  const es: EffStatus = effectiveStatus(detail?.status, runStatus === 'NO_RUN' ? undefined : runStatus);
  const done = ['SUCCESS', 'PARTIAL'].includes(runStatus);
  const failed = runStatus === 'FAILED';
  const hasRun = runStatus !== 'NO_RUN';

  // 工作流步骤状态
  const stepStates = STEP_DEFS.map((s) => {
    const t = tools.find((x) => x.toolType === s.tool);
    return { ...s, status: t?.status || (runStatus === 'NO_RUN' ? 'IDLE' : 'PENDING') };
  });
  const doneCount = stepStates.filter((s) => s.status === 'SUCCESS').length;
  const activeIdx = stepStates.findIndex((s) => s.status === 'RUNNING' || s.status === 'PENDING');
  const trackWidth = `${Math.min(100, ((activeIdx >= 0 ? activeIdx : doneCount) / STEP_DEFS.length) * 100)}%`;

  const doc = detail?.documents?.[0];
  const isLast = (order: number) => tools.length > 0 && order === Math.max(...tools.map((t) => t.toolOrder));

  return (
    <div>
      <div className="breadcrumb">
        <a onClick={() => navigate('/dashboard')}>工作台</a> / <a onClick={() => navigate('/projects')}>项目</a> / <span>{detail?.name}</span>
      </div>

      <div className="project-hero">
        <div>
          <h1>{detail?.name}</h1>
          <div className="meta-row">
            {doc && <span><IconDoc width={14} height={14} />{doc.fileName}</span>}
            <span><IconProject width={14} height={14} />{fmtDate(detail?.createdAt)}</span>
            <span><IconTemplate width={14} height={14} />{templateLabel(detail?.templateId)}</span>
            <span className={`status-badge ${es}`}>{STATUS_LABEL[es]}</span>
          </div>
        </div>
        <div className="flex gap-3">
          <button className="btn btn-primary" onClick={handleRun} disabled={running}>
            <IconPlay /> {running ? '生成中…' : '运行流水线'}
          </button>
          <button className="btn btn-secondary" onClick={() => navigate(`/projects/${projectId}/run`)}>
            <IconEdit /> 监控
          </button>
        </div>
      </div>

      <div className="content-grid">
        {/* Workflow */}
        <div className="panel full">
          <div className="panel-head"><h2>工作流状态</h2></div>
          <div className="workflow-track">
            <span className="track-progress" style={{ width: trackWidth }} />
            {stepStates.map((s) => {
              const cls = s.status === 'SUCCESS' ? 'done' : (s.status === 'RUNNING' || s.status === 'PENDING') ? 'active' : '';
              return (
                <div key={s.key} className={`flow-step ${cls}`}>
                  <span className={`step-icon ${s.color}`}>{s.icon}</span>
                  {s.key}
                </div>
              );
            })}
          </div>
        </div>

        {/* Project Info */}
        <div className="panel">
          <div className="panel-head"><h2>项目信息</h2></div>
          <div className="panel-body">
            <div className="info-grid">
              <div className="info-row"><span className="info-label">描述</span><span className="info-value">{detail?.description || '—'}</span></div>
              <div className="info-row"><span className="info-label">Pipeline 模板</span><span className="info-value"><span className={`badge badge-cyan`}>{templateLabel(detail?.templateId)}</span></span></div>
              <div className="info-row"><span className="info-label">需求文档</span><span className="info-value">{doc ? <span className="badge badge-mint">{doc.fileName}</span> : '—'}</span></div>
              <div className="info-row"><span className="info-label">创建时间</span><span className="info-value">{fmtDate(detail?.createdAt)}</span></div>
              <div className="info-row"><span className="info-label">运行状态</span><span className="info-value">{runStatus === 'NO_RUN' ? '未运行' : runStatus}</span></div>
            </div>
          </div>
        </div>

        {/* Intermediate results */}
        <div className="panel">
          <div className="panel-head">
            <h2>中间产物</h2>
            {hasRun && <span className="badge badge-cyan">可编辑 / 重跑下游</span>}
          </div>
          <div className="panel-body">
            {tools.length === 0 ? (
              <div className="empty-state"><p>运行流水线后，这里将展示各工具的分析结果</p></div>
            ) : (
              tools.filter((t) => t.output).map((t) => (
                <div className="result-card" key={t.toolOrder}>
                  <div className="result-card-head">
                    <h3>{t.status === 'SUCCESS' ? '✅ ' : t.status === 'FAILED' ? '⚠️ ' : '🔄 '}{TOOL_LABEL[t.toolType] || t.toolType}</h3>
                    <div className="result-card-ops">
                      <button className="mini-btn" title="编辑产物" onClick={() => openEdit(t)}><IconEdit width={13} height={13} /> 编辑</button>
                      {!isLast(t.toolOrder) && (
                        <button className="mini-btn" title="重跑该节点之后的下游" disabled={rerunning !== null}
                          onClick={() => handleRerun(t)}>
                          {rerunning === t.toolOrder ? <IconSync width={13} height={13} className="spin" /> : <IconSync width={13} height={13} />} 重跑下游
                        </button>
                      )}
                    </div>
                  </div>
                  <ResultBody tool={t} />
                </div>
              ))
            )}
          </div>
        </div>

        {/* Doc preview */}
        <div className="panel full">
          <div className="panel-head">
            <h2>方案文档预览</h2>
            {done && <span className="badge badge-mint"><IconCheck width={12} height={12} /> 已生成</span>}
            {failed && <span className="badge badge-danger">生成失败</span>}
          </div>
          <div className="panel-body">
            {context?.solutionText ? (
              <div className="doc-preview">
                <h3>{detail?.name} — 解决方案</h3>
                <p style={{ whiteSpace: 'pre-wrap' }}>{context.solutionText}</p>
              </div>
            ) : (
              <div className="empty-state">
                <IconDoc />
                <h3>{failed ? '方案生成失败' : '方案尚未生成'}</h3>
                <p>{failed ? '请检查 LLM Provider 配置与 IMA 连接后重试' : '点击右上角「运行流水线」开始生成方案文档'}</p>
              </div>
            )}
            <div className="download-bar">
              <button className="btn btn-primary" onClick={() => handleDownload('docx')} disabled={!done}>
                <IconDownload /> 下载 Word (.docx)
              </button>
              <button className="btn btn-secondary" onClick={() => handleDownload('md')} disabled={!done}>
                <IconDownload /> 下载 Markdown (.md)
              </button>
              <button className="btn btn-secondary" onClick={() => handleDownload('pptx')} disabled={!done}>
                <IconDownload /> 下载 PPT (.pptx)
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* 编辑中间产物模态 */}
      <Modal
        open={editTarget !== null}
        title={`编辑中间产物 · ${editTarget ? TOOL_LABEL[editTarget.toolType] || editTarget.toolType : ''}`}
        onClose={() => !savingEdit && setEditTarget(null)}
        footer={
          <>
            <button className="btn btn-secondary btn-sm" onClick={() => setEditTarget(null)} disabled={savingEdit}>取消</button>
            <button className="btn btn-primary btn-sm" onClick={confirmEdit} disabled={savingEdit}>
              {savingEdit ? '保存中…' : '保存'}
            </button>
          </>
        }
      >
        <p className="modal-hint">
          编辑产物内容后点击保存，方案预览与下载将立即同步更新；如需让后续节点基于本次修改重新生成，请保存后点「重跑下游」。
        </p>
        <textarea className="edit-textarea" value={editText} onChange={(e) => setEditText(e.target.value)}
          placeholder={editTarget?.toolType === 'SOLUTION_OUTPUT' ? '直接编辑 Markdown 方案文本' : '编辑 JSON 产物内容'} />
      </Modal>
    </div>
  );
}

function ResultBody({ tool }: { tool: ToolStatus }) {
  const o = tool.output;
  if (!o) return null;
  if (tool.toolType === 'REQUIREMENT_ANALYSIS') {
    return (
      <div>
        {o.functional && (<p><strong>功能需求：</strong>{(o.functional as string[]).join('、')}</p>)}
        {o.nonFunctional && (<p><strong>非功能需求：</strong>{(o.nonFunctional as string[]).join('、')}</p>)}
        {o.industry && <p><strong>行业：</strong>{o.industry}</p>}
      </div>
    );
  }
  if (tool.toolType === 'PRODUCT_MATCHING' && Array.isArray(o)) {
    return (
      <div>
        {o.slice(0, 4).map((p: any, i: number) => (
          <p key={i}>{p.productName} · {p.version} · 覆盖率 {p.coverage}</p>
        ))}
      </div>
    );
  }
  if (typeof o === 'string') return <p style={{ whiteSpace: 'pre-wrap' }}>{o}</p>;
  try {
    return <p>{JSON.stringify(o).slice(0, 200)}</p>;
  } catch {
    return null;
  }
}
