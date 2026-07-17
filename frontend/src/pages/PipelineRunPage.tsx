import { useEffect, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { projectApi, downloadBlob } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { IconPlay, IconDownload, IconChevronRight, IconCheck, IconSync, IconClose, IconDoc } from '../components/ui/icons';
import { effectiveStatus, STATUS_LABEL, type EffStatus } from '../utils/status';

interface ToolStatus { toolType: string; toolOrder: number; status: string; output?: any; errorMessage?: string; }

const TOOL_LABEL: Record<string, string> = {
  REQUIREMENT_ANALYSIS: '需求分析', PRODUCT_MATCHING: '产品匹配', CASE_RECOMMEND: '案例推荐',
  COMPETITIVE_ANALYSIS: '竞品对比', ARCHITECTURE_DIAGRAM: '架构图生成', SOLUTION_OUTLINE: '方案框架',
  SOLUTION_QC: '方案质检', SOLUTION_OUTPUT: '方案输出', PPT_OUTPUT: 'PPT 输出',
};

function statusBadge(s: string) {
  switch (s) {
    case 'SUCCESS': return <span className="badge badge-mint"><IconCheck width={12} height={12} /> 完成</span>;
    case 'RUNNING': return <span className="badge badge-cyan"><IconSync width={12} height={12} /> 运行中</span>;
    case 'FAILED': return <span className="badge badge-danger">失败</span>;
    case 'PENDING': return <span className="badge badge-idle">排队中</span>;
    default: return <span className="badge badge-idle">等待中</span>;
  }
}

export default function PipelineRunPage() {
  const { id } = useParams();
  const projectId = Number(id);
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [status, setStatus] = useState<string>('NO_RUN');
  const [tools, setTools] = useState<ToolStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const pollRef = useRef<number | null>(null);

  const poll = async () => {
    try {
      const { data } = await projectApi.status(projectId);
      if (data.status === 'NO_RUN') { setStatus('NO_RUN'); return; }
      setStatus(data.status);
      setTools(data.tools || []);
      if (['SUCCESS', 'PARTIAL', 'FAILED'].includes(data.status)) {
        setRunning(false);
        if (pollRef.current) window.clearInterval(pollRef.current);
      }
    } catch { /* ignore */ }
  };

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const { data: existing } = await projectApi.status(projectId);
        if (existing.status === 'NO_RUN') { await projectApi.run(projectId); }
        if (!cancelled) { setLoading(false); poll(); pollRef.current = window.setInterval(poll, 3000); }
      } catch (e: any) {
        if (!cancelled) { setLoading(false); showToast(e.response?.data?.error || '启动流水线失败', true); }
      }
    })();
    return () => { cancelled = true; if (pollRef.current) window.clearInterval(pollRef.current); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const handleRun = async () => {
    setRunning(true);
    try { await projectApi.run(projectId); showToast('流水线已重新启动'); poll(); pollRef.current = window.setInterval(poll, 3000); }
    catch (e: any) { setRunning(false); showToast(e.response?.data?.error || '启动失败', true); }
  };

  const handleDownload = async (type: 'md' | 'docx' | 'pptx') => {
    try {
      const { data } = type === 'md' ? await projectApi.downloadMd(projectId)
        : type === 'docx' ? await projectApi.downloadDocx(projectId)
        : await projectApi.downloadPptx(projectId);
      const ext = type === 'md' ? 'md' : type === 'docx' ? 'docx' : 'pptx';
      downloadBlob(data, `solution_${projectId}.${ext}`);
      showToast('下载已开始');
    } catch (e: any) { showToast(e.response?.data?.error || '下载失败', true); }
  };

  if (loading) {
    return <div className="empty-state"><p>正在初始化流水线…</p></div>;
  }

  const done = ['SUCCESS', 'PARTIAL'].includes(status);
  const failed = status === 'FAILED';
  const finished = tools.filter((t) => t.status === 'SUCCESS' || t.status === 'FAILED').length;
  const progress = tools.length ? Math.round((finished / tools.length) * 100) : 0;
  const es: EffStatus = effectiveStatus(undefined, status === 'NO_RUN' ? undefined : status);

  return (
    <div style={{ maxWidth: 920, margin: '0 auto' }}>
      <div className="flex-between" style={{ marginBottom: 24, flexWrap: 'wrap', gap: 12 }}>
        <div>
          <h1 style={{ fontSize: 26, fontWeight: 760, letterSpacing: '-.03em' }}>方案生成监控</h1>
          <p style={{ fontSize: 13, color: 'var(--muted)' }}>项目 ID：{projectId}</p>
        </div>
        <div className="flex gap-3" style={{ alignItems: 'center' }}>
          <span style={{ fontSize: 28, fontWeight: 760, color: 'var(--mint)' }}>{progress}%</span>
          <span className={`status-badge ${es}`}>{STATUS_LABEL[es]}</span>
          <button className="btn btn-secondary btn-sm" onClick={() => navigate(`/projects/${projectId}`)}><IconChevronRight /> 返回详情</button>
        </div>
      </div>

      {/* Tool cards */}
      <div className="flex gap-4" style={{ marginBottom: 24, flexWrap: 'wrap' }}>
        {tools.length === 0 ? (
          <div className="panel" style={{ flex: 1 }}><div className="empty-state"><p>等待流水线启动…</p></div></div>
        ) : (
          tools.map((t) => (
            <div key={t.toolOrder} className="card" style={{ flex: '1 1 180px', padding: 16, borderColor: t.status === 'RUNNING' ? 'var(--mint)' : 'var(--line)' }}>
              <div className="flex gap-2" style={{ alignItems: 'center', marginBottom: 8 }}>
                <span className={`node-icon ${t.status === 'SUCCESS' ? 'mint' : t.status === 'RUNNING' ? 'cyan' : t.status === 'FAILED' ? 'danger' : ''}`}>
                  {t.status === 'RUNNING' ? <IconSync /> : t.status === 'FAILED' ? <IconClose /> : <IconDoc />}
                </span>
                <div style={{ fontWeight: 680, fontSize: 13 }}>{TOOL_LABEL[t.toolType] || t.toolType}</div>
              </div>
              {statusBadge(t.status)}
            </div>
          ))
        )}
      </div>

      {/* Downloads */}
      <div className="panel">
        <div className="panel-head"><h2>方案下载</h2></div>
        <div className="panel-body">
          {failed ? (
            <div className="badge badge-danger" style={{ padding: 8 }}>流水线执行失败，请检查 LLM Provider 配置与 IMA 连接后重试</div>
          ) : (
            <div className="flex gap-3" style={{ flexWrap: 'wrap' }}>
              <button className="btn btn-primary" onClick={() => handleDownload('docx')} disabled={!done}><IconDownload /> 下载 Word (.docx)</button>
              <button className="btn btn-secondary" onClick={() => handleDownload('md')} disabled={!done}><IconDownload /> 下载 Markdown (.md)</button>
              <button className="btn btn-secondary" onClick={() => handleDownload('pptx')} disabled={!done}><IconDownload /> 下载 PPT (.pptx)</button>
            </div>
          )}
          {!done && !failed && <p style={{ fontSize: 12, color: 'var(--muted)', marginTop: 12 }}>方案生成完成后即可下载</p>}
          {!running && status !== 'NO_RUN' && (
            <button className="btn btn-secondary" style={{ marginTop: 16 }} onClick={handleRun}><IconPlay /> 重新运行</button>
          )}
        </div>
      </div>
    </div>
  );
}
