import { useEffect, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { projectApi, toolApi, downloadBlob, searchApi, llmApi, imaApi, pptTemplateApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { Modal } from '../components/ui/Modal';
import VersionPanel from '../components/VersionPanel';
import {
  IconProject, IconDownload, IconPlay, IconEdit,
  IconDoc, IconSearch, IconBrain, IconTemplate, IconCheck, IconSync,
} from '../components/ui/icons';
import { effectiveStatus, STATUS_LABEL, templateLabel, fmtDate, type EffStatus } from '../utils/status';

interface ToolStatus { execId: number; toolType: string; toolOrder: number; status: string; output?: any; errorMessage?: string; }
interface Detail { id: number; name: string; description?: string; templateId?: string; status: string; createdAt?: string; documents?: any[]; latestRun?: { id: number; status: string; tools: ToolStatus[] }; kbDirty?: boolean; kbDirtyNote?: string; kbDirtySince?: string; }

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

  // P5-1 语义搜索
  const [searchQ, setSearchQ] = useState('');
  const [searching, setSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<any[] | null>(null);

  // PPT 模板选择
  const [pptTemplates, setPptTemplates] = useState<any[]>([]);
  const [selectedPptTemplate, setSelectedPptTemplate] = useState<number | ''>('');
  const loadPptTemplates = async () => {
    try {
      const { data } = await pptTemplateApi.list();
      const list = data || [];
      setPptTemplates(list);
      const def = list.find((t: any) => t.isDefault);
      if (def) setSelectedPptTemplate(def.id);
    } catch { /* ignore */ }
  };

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
      loadPptTemplates();
      if (!cancelled) setLoading(false);
    })();
    return () => { cancelled = true; if (pollRef.current) window.clearInterval(pollRef.current); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const handleRun = async () => {
    // 先加载配置，成功后再打开弹窗；加载失败给明确提示
    try {
      const [{ data: ps }, { data: kbs }] = await Promise.all([
        llmApi.list(),
        imaApi.listConfigs(),
      ]);
      const providers = ps || [];
      const enabled = (kbs || []).filter((k: any) => k.enabled);
      if (providers.length === 0) {
        showToast('请先在「设置 → LLM 配置」中添加大模型 API Key', true);
        return;
      }
      setProviders(providers);
      setKbConfigs(enabled);
      const def = providers.find((p: any) => p.isDefault);
      setSelectedProvider(def ? def.id : (providers[0] ? providers[0].id : ''));
      setSelectedKbs(enabled.map((k: any) => k.id));
      setRunConfigOpen(true);
    } catch (e: any) {
      const msg = e.response?.data?.error || e.response?.status === 401 ? '登录已过期，请重新登录' : '加载运行配置失败，请确认已配置 LLM Provider 和 IMA 知识库';
      showToast(msg, true);
    }
  };

  // ===== 运行前配置（模型 + 知识库）=====
  const [runConfigOpen, setRunConfigOpen] = useState(false);
  const [providers, setProviders] = useState<any[]>([]);
  const [kbConfigs, setKbConfigs] = useState<any[]>([]);
  const [selectedProvider, setSelectedProvider] = useState<number | ''>('');
  const [selectedKbs, setSelectedKbs] = useState<number[]>([]);

  const submitRun = async () => {
    setRunning(true);
    setRunConfigOpen(false);
    try {
      await projectApi.run(projectId, {
        providerId: selectedProvider === '' ? undefined : selectedProvider,
        kbConfigIds: selectedKbs,
      });
      showToast('流水线已启动，正在生成方案…');
      poll();
      if (pollRef.current) window.clearInterval(pollRef.current);
      pollRef.current = window.setInterval(poll, 3000);
    } catch (e: any) {
      setRunning(false);
      showToast(e.response?.data?.error || '启动失败，请确认已配置 LLM Provider', true);
    }
  };

  // P5-1 语义搜索
  const handleSearch = async () => {
    const q = searchQ.trim();
    if (!q) return;
    setSearching(true);
    setSearchResults(null);
    try {
      const { data } = await searchApi.search(projectId, q, 5);
      setSearchResults(data.results || []);
      if (!data.results || data.results.length === 0) {
        showToast('未找到相关内容');
      }
    } catch (e: any) {
      showToast(e.response?.data?.error || '搜索失败', true);
    } finally {
      setSearching(false);
    }
  };

  const handleDownload = async (type: 'md' | 'docx' | 'pptx') => {
    try {
      const { data } = type === 'md' ? await projectApi.downloadMd(projectId)
        : type === 'docx' ? await projectApi.downloadDocx(projectId)
        : await projectApi.downloadPptx(projectId, selectedPptTemplate === '' ? undefined : selectedPptTemplate as number);
      const ext = type === 'md' ? 'md' : type === 'docx' ? 'docx' : 'pptx';
      const name = `${(detail?.name || 'solution').replace(/\s+/g, '_')}.${ext}`;
      downloadBlob(data, name);
      showToast('下载已开始');
    } catch (e: any) {
      // 详细错误提示：区分超时 / 后端错误 / 网络问题
      let msg = '下载失败';
      if (e.code === 'ECONNABORTED' || e.message?.includes('timeout')) {
        msg = `${type.toUpperCase()} 生成超时，请稍后重试或直接到后端 export 目录查看已生成文件`;
      } else if (e.response?.data?.error) {
        msg = e.response.data.error;
      } else if (e.response?.status) {
        msg = `下载失败（HTTP ${e.response.status}）`;
      }
      showToast(msg, true);
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

  // ===== 中间产物折叠与标签页 =====
  const [expandedCards, setExpandedCards] = useState<Set<number>>(new Set());
  const [activeTab, setActiveTab] = useState<string>('');
  const toggleCard = (order: number) => {
    setExpandedCards((prev) => {
      const next = new Set(prev);
      next.has(order) ? next.delete(order) : next.add(order);
      return next;
    });
  };
  // 产物变化时，自动展开 RUNNING 状态的工具 + 最后一个 SUCCESS 的工具，其余折叠
  useEffect(() => {
    const outputTools = tools.filter((t) => t.output);
    if (outputTools.length === 0) return;
    const newExpanded = new Set<number>();
    // 展开所有 RUNNING 的
    outputTools.filter((t) => t.status === 'RUNNING').forEach((t) => newExpanded.add(t.toolOrder));
    // 展开最后一个 SUCCESS 的（如果没有 RUNNING 的话）
    if (newExpanded.size === 0) {
      const lastSuccess = [...outputTools].reverse().find((t) => t.status === 'SUCCESS');
      if (lastSuccess) newExpanded.add(lastSuccess.toolOrder);
    }
    setExpandedCards(newExpanded);
    // 自动切换到当前关注的阶段
    const focusTool = outputTools.find((t) => t.status === 'RUNNING')
      || [...outputTools].reverse().find((t) => t.status === 'SUCCESS');
    if (focusTool) {
      const step = STEP_DEFS.find((s) => s.tool === focusTool.toolType);
      if (step) setActiveTab(step.key);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tools]);
  const handleImprove = async () => {
    const outline = tools.find((t) => t.toolType === 'SOLUTION_OUTLINE');
    if (!outline) { showToast('未找到方案大纲节点', true); return; }
    await handleRerun(outline);
  };

  // ===== 用最新知识库重生成（P3-1：知识库有更新时） =====
  const [regenKb, setRegenKb] = useState(false);
  const handleRegenKb = async () => {
    setRegenKb(true);
    try {
      await projectApi.rerunKb(projectId);
      showToast('已用最新知识库触发重生成…');
      poll();
      if (pollRef.current) window.clearInterval(pollRef.current);
      pollRef.current = window.setInterval(poll, 3000);
    } catch (e: any) {
      showToast(e.response?.data?.error || '重生成启动失败', true);
    } finally {
      setRegenKb(false);
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
    // PPT_OUTPUT 不是流水线节点（下载时生成），状态跟随方案整体完成情况
    if (s.tool === 'PPT_OUTPUT') {
      const pptStatus = done ? 'SUCCESS' : failed ? 'FAILED' : (runStatus === 'NO_RUN' ? 'IDLE' : 'PENDING');
      return { ...s, status: pptStatus };
    }
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
            <IconEdit /> 独立监控页
          </button>
        </div>
      </div>

      {detail?.kbDirty && (
        <div className="kb-dirty-banner">
          <span className="kb-dirty-icon">📡</span>
          <div className="kb-dirty-text">
            <strong>知识库有更新</strong>
            <span>{detail.kbDirtyNote || '关联的知识库发生变化，建议用最新内容重生成方案。'}</span>
          </div>
          <button className="btn btn-primary btn-sm" onClick={handleRegenKb} disabled={regenKb || running}>
            {regenKb ? <IconSync width={13} height={13} className="spin" /> : <IconSync width={13} height={13} />} 用最新知识库重生成
          </button>
        </div>
      )}

      {/* 运行前配置弹窗：模型 + 知识库 */}
      {runConfigOpen && (
        <div className="modal-overlay" onClick={() => setRunConfigOpen(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3 className="modal-title">运行配置</h3>
            {providers.length === 0 && (
              <div className="empty-state compact" style={{ marginBottom: 16, padding: '20px 18px' }}>
                <p style={{ marginBottom: 12 }}>
                  运行流水线需要先配置大模型（LLM Provider）。<br />
                  请到「设置 → LLM 配置」添加 API Key 后再试。
                </p>
                <button className="btn btn-primary" onClick={() => navigate('/settings/llm')}>
                  去配置大模型
                </button>
              </div>
            )}
            <div className="form-group">
              <label>大模型</label>
              <select
                className="form-input"
                value={selectedProvider}
                onChange={(e) => setSelectedProvider(e.target.value ? Number(e.target.value) : '')}
              >
                {providers.length === 0 && <option value="">请先在设置页配置模型</option>}
                {providers.map((p: any) => (
                  <option key={p.id} value={p.id}>{p.name}（{p.model || p.endpoint}）</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label>知识库（可多选，不选则使用全部启用库）</label>
              {kbConfigs.length === 0 ? (
                <p className="text-muted">未配置知识库，将直接基于大模型生成</p>
              ) : (
                <div className="checkbox-list">
                  {kbConfigs.map((k: any) => (
                    <label key={k.id} className="checkbox-item">
                      <input
                        type="checkbox"
                        checked={selectedKbs.includes(k.id)}
                        onChange={(e) =>
                          setSelectedKbs(e.target.checked
                            ? [...selectedKbs, k.id]
                            : selectedKbs.filter((id) => id !== k.id))
                        }
                      />
                      {k.kbName}（{k.purpose || '通用'}）
                    </label>
                  ))}
                </div>
              )}
            </div>
            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => setRunConfigOpen(false)}>取消</button>
              <button className="btn btn-primary" onClick={submitRun} disabled={running || providers.length === 0}>
                开始生成
              </button>
            </div>
          </div>
        </div>
      )}

      {/* P5-1 语义搜索 */}
      <div className="search-bar">
        <input
          className="search-input"
          placeholder="搜索项目方案内容…（支持语义检索）"
          value={searchQ}
          onChange={(e) => setSearchQ(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
        />
        <button className="btn btn-primary btn-sm" onClick={handleSearch} disabled={searching}>
          {searching ? '搜索中…' : '搜索'}
        </button>
      </div>
      {searchResults && searchResults.length > 0 && (
        <div className="search-results">
          {searchResults.map((r: any, i: number) => (
            <div key={i} className="search-result-item">
              <div className="search-result-meta">
                <span className="search-result-source">{r.source}</span>
              </div>
              <div className="search-result-content">{r.content}</div>
            </div>
          ))}
        </div>
      )}

      <div className="content-grid">
        {/* Workflow */}
        <div className="panel full">
          <div className="panel-head">
            <h2>工作流状态</h2>
            {running && <span className="badge badge-cyan"><IconSync width={12} height={12} className="spin" /> 生成中…</span>}
            {done && <span className="badge badge-mint"><IconCheck width={12} height={12} /> 已生成</span>}
            {failed && <span className="badge badge-danger">生成失败</span>}
          </div>
          <div className="workflow-track">
            <span className="track-progress" style={{ width: trackWidth }} />
            {stepStates.map((s) => {
              const cls = s.status === 'SUCCESS' ? 'done' : s.status === 'RUNNING' ? 'active running' : (s.status === 'PENDING' || s.status === 'IDLE') ? 'pending' : 'failed';
              return (
                <div key={s.key} className={`flow-step ${cls}`}>
                  <span className={`step-icon ${s.color}`}>
                    {s.status === 'RUNNING' ? <IconSync className="spin" /> : s.status === 'FAILED' ? '✕' : s.icon}
                  </span>
                  {s.key}
                </div>
              );
            })}
          </div>
          <div className="workflow-meta">
            <span>进度：<strong>{trackWidth}</strong></span>
            {running && <span className="text-muted">每 3 秒自动刷新，实时查看各阶段</span>}
          </div>
        </div>

        {/* Project Info — 全宽 */}
        <div className="panel full">
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

        {/* Intermediate results — 全宽 + 标签页 + 折叠 */}
        <div className="panel full">
          <div className="panel-head">
            <h2>中间产物</h2>
            {hasRun && <span className="badge badge-cyan">可编辑 / 重跑下游</span>}
          </div>
          <div className="panel-body">
            {tools.length === 0 ? (
              <div className="empty-state"><p>运行流水线后，这里将展示各工具的分析结果</p></div>
            ) : (
              <>
                {/* 阶段标签页 */}
                <div className="tab-row">
                  {STEP_DEFS.map((s) => {
                    const hasOutput = tools.some((t) => t.toolType === s.tool && t.output);
                    return (
                      <button
                        key={s.key}
                        className={`tab-btn ${activeTab === s.key ? 'active' : ''} ${hasOutput ? 'has-data' : ''}`}
                        onClick={() => setActiveTab(s.key)}
                      >
                        <span className={`step-icon-sm ${s.color}`}>{s.icon}</span>
                        <span>{s.key}</span>
                        {hasOutput && <i className="tab-dot" />}
                      </button>
                    );
                  })}
                </div>
                {/* 当前标签页下的产物卡片（多列网格） */}
                <div className="result-grid">
                  {tools
                    .filter((t) => {
                      if (!t.output) return false;
                      if (activeTab) {
                        const step = STEP_DEFS.find((s) => s.tool === t.toolType);
                        return step && step.key === activeTab;
                      }
                      return true;
                    })
                    .map((t) => {
                      const isExpanded = expandedCards.has(t.toolOrder);
                      return (
                        <div className={`result-card ${isExpanded ? 'expanded' : ''}`} key={t.toolOrder}>
                          <div
                            className="result-card-head clickable"
                            onClick={() => toggleCard(t.toolOrder)}
                          >
                            <h3>
                              <span className="collapse-arrow">{isExpanded ? '▾' : '▸'}</span>
                              {t.status === 'SUCCESS' ? '✅ ' : t.status === 'FAILED' ? '⚠️ ' : '🔄 '}
                              {TOOL_LABEL[t.toolType] || t.toolType}
                            </h3>
                            <div className="result-card-ops" onClick={(e) => e.stopPropagation()}>
                              <button className="mini-btn" title="编辑产物" onClick={() => openEdit(t)}><IconEdit width={13} height={13} /> 编辑</button>
                              {!isLast(t.toolOrder) && (
                                <button className="mini-btn" title="重跑该节点之后的下游" disabled={rerunning !== null}
                                  onClick={() => handleRerun(t)}>
                                  {rerunning === t.toolOrder ? <IconSync width={13} height={13} className="spin" /> : <IconSync width={13} height={13} />} 重跑下游
                                </button>
                              )}
                            </div>
                          </div>
                          {isExpanded && (
                            <div className="result-card-body">
                              <ResultBody tool={t} onImprove={t.toolType === 'SOLUTION_QC' ? handleImprove : undefined} improving={rerunning !== null} />
                            </div>
                          )}
                        </div>
                      );
                    })}
                </div>
                {tools.filter((t) => t.output && (!activeTab || STEP_DEFS.some((s) => s.tool === t.toolType && s.key === activeTab))).length === 0 && activeTab && (
                  <div className="empty-state compact" style={{ padding: '18px' }}>
                    <p>该阶段暂无产物，请选择其他标签页查看</p>
                  </div>
                )}
              </>
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
              <div className="download-pptx-group">
                <button className="btn btn-secondary" onClick={() => handleDownload('pptx')} disabled={!done}>
                  <IconDownload /> 下载 PPT (.pptx)
                </button>
                {pptTemplates.length > 0 && (
                  <select
                    className="form-select ppt-template-select"
                    value={selectedPptTemplate}
                    onChange={(e) => setSelectedPptTemplate(e.target.value ? Number(e.target.value) : '')}
                    disabled={!done}
                  >
                    <option value="">系统默认风格</option>
                    {pptTemplates.map((t: any) => (
                      <option key={t.id} value={t.id}>{t.name}{t.isDefault ? '（默认）' : ''}</option>
                    ))}
                  </select>
                )}
                {pptTemplates.length === 0 && (
                  <a className="link-btn" onClick={() => navigate('/settings/ppt-templates')} style={{ fontSize: 11, marginLeft: 8 }}>
                    上传模板
                  </a>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* 方案版本管理（P4-3）：快照 / 历史 / 一键回退 */}
        <VersionPanel projectId={projectId} onRestored={() => { loadOnce(); poll(); }} />
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

function ResultBody({ tool, onImprove, improving }: { tool: ToolStatus; onImprove?: () => void; improving?: boolean }) {
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
  if (tool.toolType === 'CASE_RECOMMEND' && Array.isArray(o)) {
    return (
      <div className="case-list">
        {o.map((c: any, i: number) => (
          <div className="case-item" key={i}>
            <div className="case-head">
              <span className="case-name">{c.caseName}</span>
              {typeof c.matchScore === 'number' && (
                <span className="score-chip">{Math.round(c.matchScore)}<i>分</i></span>
              )}
            </div>
            {c.scenario && <p className="case-line"><b>场景</b> {c.scenario}</p>}
            {c.productsUsed && <p className="case-line">{c.productsUsed}</p>}
            {c.keyEffect && <p className="case-line case-eff">{c.keyEffect}</p>}
            {c.matchReason && <p className="case-line case-reason">匹配点：{c.matchReason}</p>}
            {c.referenceDoc && <span className="src-chip">📚 {c.referenceDoc}</span>}
          </div>
        ))}
      </div>
    );
  }
  if (tool.toolType === 'COMPETITOR_ANALYSIS' && Array.isArray(o)) {
    return (
      <div className="comp-list">
        {o.map((c: any, i: number) => (
          <div className="comp-item" key={i}>
            <div className="comp-head">
              <span className="comp-name">{c.competitorName}</span>
              {typeof c.advantageScore === 'number' && (
                <span className="score-chip adv">优势 {Math.round(c.advantageScore)}<i>分</i></span>
              )}
            </div>
            {c.ourAdvantage && <p className="case-line case-eff"><b>优势</b> {c.ourAdvantage}</p>}
            {c.ourDisadvantage && <p className="case-line"><b>注意</b> {c.ourDisadvantage}</p>}
            {c.recommendation && <p className="case-line case-reason">建议：{c.recommendation}</p>}
            {c.referenceDoc && <span className="src-chip">📚 {c.referenceDoc}</span>}
          </div>
        ))}
      </div>
    );
  }
  if (tool.toolType === 'SOLUTION_QC') {
    const score = typeof o.overallScore === 'number' ? o.overallScore : 0;
    const level = o.level || (score >= 90 ? '优秀' : score >= 80 ? '良好' : score >= 70 ? '合格' : '待改进');
    const passed = o.passed === undefined ? score >= 75 : o.passed;
    const levelCls = level === '优秀' ? 'lv-excellent' : level === '良好' ? 'lv-good' : level === '合格' ? 'lv-ok' : 'lv-warn';
    const dims = Array.isArray(o.dimensions) ? o.dimensions : [];
    const sugs = Array.isArray(o.suggestions) ? o.suggestions : [];
    return (
      <div className="qc-dash">
        <div className="qc-top">
          <div className={`qc-ring ${passed ? 'pass' : 'fail'}`} style={{ ['--p' as any]: `${score}%` }}>
            <span className="qc-score">{Math.round(score)}</span>
            <span className="qc-unit">分</span>
          </div>
          <div className="qc-meta">
            <span className={`qc-level ${levelCls}`}>{level}</span>
            <span className={`qc-pass ${passed ? 'yes' : 'no'}`}>{passed ? '✓ 通过验收' : '✗ 未达验收线'}</span>
            <p className="qc-hint">整体分 ≥ 75 视为通过；为让方案更稳，可点「按建议改进并重跑」让大纲吸收质检建议后重生成。</p>
          </div>
        </div>
        {dims.length > 0 && (
          <div className="qc-dims">
            {dims.map((d: any, i: number) => (
              <div className="qc-dim" key={i}>
                <div className="qc-dim-head">
                  <span className="qc-dim-name">{d.dimension}</span>
                  <span className="qc-dim-score">{Math.round(typeof d.score === 'number' ? d.score : 0)}</span>
                </div>
                <div className="qc-bar"><span style={{ width: `${Math.max(0, Math.min(100, typeof d.score === 'number' ? d.score : 0))}%` }} /></div>
                {d.comment && <p className="qc-dim-comment">{d.comment}</p>}
              </div>
            ))}
          </div>
        )}
        {sugs.length > 0 && (
          <div className="qc-sugs">
            <h4>改进建议</h4>
            <ul>{sugs.map((s: string, i: number) => <li key={i}>{s}</li>)}</ul>
          </div>
        )}
        {onImprove && (
          <button className="btn btn-primary btn-sm qc-improve" onClick={onImprove} disabled={improving}>
            {improving ? <IconSync width={13} height={13} className="spin" /> : <IconSync width={13} height={13} />} 按建议改进并重跑
          </button>
        )}
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
