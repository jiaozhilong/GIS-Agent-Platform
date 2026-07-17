import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import ReactFlow, {
  Background,
  Controls,
  MiniMap,
  Handle,
  Position,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge,
  type NodeProps,
  MarkerType,
} from 'reactflow';
import 'reactflow/dist/style.css';
import { useNavigate } from 'react-router-dom';
import { templateApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { Modal } from '../components/ui/Modal';
import {
  IconBrain,
  IconSearch,
  IconDoc,
  IconTemplate,
  IconClose,
  IconPlay,
  IconEdit,
  IconPlus,
  IconTrash,
  IconCheck,
} from '../components/ui/icons';

/** toolType → 展示元信息（与后端 PipelineEngine.TOOL_BY_TYPE / 模板 toolChain 对齐） */
const TOOL_META: Record<string, { label: string; color: string; icon: ReactNode; desc: string }> = {
  REQUIREMENT_ANALYSIS: { label: '需求分析', color: 'mint', icon: <IconDoc />, desc: '解析需求文档，提取结构化需求' },
  PRODUCT_MATCHING: { label: '产品匹配', color: 'mint', icon: <IconBrain />, desc: '基于需求与知识库匹配推荐 GIS 产品' },
  CASE_RECOMMEND: { label: '案例推荐', color: 'cyan', icon: <IconSearch />, desc: '检索标杆案例，提供可借鉴方案' },
  COMPETITOR_ANALYSIS: { label: '竞品对比', color: 'amber', icon: <IconSearch />, desc: '分析竞品能力，明确差异化优势' },
  ARCHITECTURE_DIAGRAM: { label: '架构图生成', color: 'cyan', icon: <IconTemplate />, desc: '生成方案技术架构图（Mermaid）' },
  SOLUTION_OUTLINE: { label: '方案框架', color: 'amber', icon: <IconDoc />, desc: '组装方案章节结构' },
  SOLUTION_QC: { label: '方案质检', color: 'purple', icon: <IconSearch />, desc: '校验方案完整性与一致性' },
  SOLUTION_OUTPUT: { label: '方案输出', color: 'mint', icon: <IconDoc />, desc: '生成可交付的 Word / PPT' },
};

/** 可添加的工具列表（编辑模式下用于扩展自定义链） */
const TOOL_OPTIONS = Object.entries(TOOL_META).map(([type, m]) => ({ type, label: m.label }));

interface Tpl {
  id: number;
  templateKey: string;
  name: string;
  category: string;
  description: string;
  toolChain: string[];
  estimatedTime?: string;
  builtin: boolean;
}

function parseChain(raw: any): string[] {
  if (Array.isArray(raw)) return raw.map(String);
  if (typeof raw === 'string') {
    try { return JSON.parse(raw).map(String); } catch { return []; }
  }
  return [];
}

function mapTpl(t: any): Tpl {
  return {
    id: t.id,
    templateKey: t.templateKey,
    name: t.name,
    category: t.category || 'official',
    description: t.description || '',
    toolChain: parseChain(t.toolChain),
    estimatedTime: t.estimatedTime,
    builtin: t.builtin,
  };
}

/** 根据工具链构建 React Flow 节点（横向布局） */
function buildGraph(toolChain: string[]): { nodes: Node[]; edges: Edge[] } {
  const nodes: Node[] = toolChain.map((type, i) => {
    const meta = TOOL_META[type] || { label: type, color: 'mint', icon: <IconDoc />, desc: '' };
    return {
      id: `n${i}`,
      type: 'tool',
      position: { x: 40 + i * 250, y: 140 },
      data: { toolType: type, label: meta.label, color: meta.color, icon: meta.icon, desc: meta.desc },
    };
  });
  const edges: Edge[] = toolChain.slice(1).map((_, i) => ({
    id: `e${i}`,
    source: `n${i}`,
    target: `n${i + 1}`,
    animated: true,
    markerEnd: { type: MarkerType.ArrowClosed, color: 'var(--accent)' },
    style: { stroke: 'var(--accent)' },
  }));
  return { nodes, edges };
}

function ToolNode({ data }: NodeProps) {
  return (
    <div className={`rf-node rf-${data.color}`}>
      <Handle type="target" position={Position.Left} />
      <div className="rf-node-head">
        <span className={`rf-node-icon rf-${data.color}`}>{data.icon}</span>
        <span className="rf-node-title">{data.label}</span>
      </div>
      <div className="rf-node-desc">{data.desc}</div>
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

const nodeTypes = { tool: ToolNode };

type Filter = 'all' | 'official' | 'mine';

export default function PipelinePage() {
  const [templates, setTemplates] = useState<Tpl[]>([]);
  const [filter, setFilter] = useState<Filter>('all');
  const [activeKey, setActiveKey] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Node | null>(null);

  // 编辑态
  const [editMode, setEditMode] = useState(false);
  const [chain, setChain] = useState<string[]>([]);
  const [addType, setAddType] = useState<string>('REQUIREMENT_ANALYSIS');

  // 保存模态
  const [saveOpen, setSaveOpen] = useState(false);
  const [saveName, setSaveName] = useState('');
  const [saveDesc, setSaveDesc] = useState('');
  const [saving, setSaving] = useState(false);

  const { showToast } = useToast();
  const navigate = useNavigate();

  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);

  const loadTemplates = useCallback(async (cat: Filter) => {
    setLoading(true);
    try {
      const res = await templateApi.list(cat === 'all' ? undefined : cat);
      const list: any[] = res.data || [];
      const mapped = list.map(mapTpl);
      setTemplates(mapped);
      setActiveKey((prev) => {
        if (prev && mapped.some((t) => t.templateKey === prev)) return prev;
        const def = mapped.find((t) => t.templateKey === 'full_solution') || mapped[0];
        return def ? def.templateKey : '';
      });
    } catch {
      showToast('模板加载失败', true);
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  useEffect(() => { loadTemplates(filter); }, [filter, loadTemplates]);

  const active = useMemo(
    () => templates.find((t) => t.templateKey === activeKey),
    [templates, activeKey],
  );

  // 切换模板：若处于编辑态，以其工具链重新初始化编辑链
  useEffect(() => {
    if (editMode && active) setChain(active.toolChain.slice());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeKey]);

  // 模板切换 / 编辑态变化 / 编辑链变化 → 重建画布
  useEffect(() => {
    const src = editMode ? chain : (active?.toolChain ?? []);
    const { nodes: ns, edges: es } = buildGraph(src);
    setNodes(ns);
    setEdges(es);
    setSelected(null);
  }, [active, editMode, chain, setNodes, setEdges]);

  // ===== 编辑操作 =====
  const enterEdit = () => {
    if (!active) return;
    setChain(active.toolChain.slice());
    setEditMode(true);
  };
  const exitEdit = () => { setEditMode(false); setSelected(null); };
  const addTool = () => {
    if (!addType) return;
    setChain((prev) => [...prev, addType]);
  };
  const removeTool = (idx: number) => setChain((prev) => prev.filter((_, i) => i !== idx));
  const moveTool = (idx: number, dir: -1 | 1) => {
    setChain((prev) => {
      const next = prev.slice();
      const j = idx + dir;
      if (j < 0 || j >= next.length) return prev;
      [next[idx], next[j]] = [next[j], next[idx]];
      return next;
    });
  };

  // ===== 保存 / 删除 =====
  const openSave = () => {
    if (chain.length === 0) { showToast('工具链为空，无法保存', true); return; }
    setSaveName(active?.name ? `我的-${active.name}` : '我的模板');
    setSaveDesc('');
    setSaveOpen(true);
  };
  const confirmSave = async () => {
    if (!saveName.trim()) { showToast('请填写模板名称', true); return; }
    setSaving(true);
    try {
      const res = await templateApi.create({
        name: saveName.trim(),
        description: saveDesc.trim(),
        toolChain: chain,
      });
      const saved = res.data;
      showToast('模板已保存，可在「我的」中复用');
      setSaveOpen(false);
      setEditMode(false);
      setFilter('mine');
      await loadTemplates('mine');
      if (saved?.templateKey) setActiveKey(saved.templateKey);
    } catch (e: any) {
      const msg = e?.response?.data?.message || '保存失败';
      showToast(msg, true);
    } finally {
      setSaving(false);
    }
  };
  const deleteTemplate = async (t: Tpl) => {
    if (t.builtin) { showToast('内置模板不可删除', true); return; }
    try {
      await templateApi.remove(t.templateKey);
      showToast('已删除自定义模板');
      await loadTemplates(filter);
    } catch {
      showToast('删除失败', true);
    }
  };

  const onNodeClick = useCallback((_: any, node: Node) => setSelected(node), []);

  const useTemplate = () => {
    if (!active || editMode) return;
    showToast(`已加载模板「${active.name}」，请在项目中运行`);
    navigate(`/projects/new?template=${encodeURIComponent(active.templateKey)}`);
  };

  const tabs: { key: Filter; label: string }[] = [
    { key: 'all', label: '全部' },
    { key: 'official', label: '内置' },
    { key: 'mine', label: '我的' },
  ];

  return (
    <div className="pipeline-layout">
      {/* Toolbox：模板列表 + 编辑态工具链编辑器 */}
      <aside className="pipeline-toolbox">
        <div className="toolbox-section">
          <div className="toolbox-title">流程模板</div>
          <div className="tpl-tabs">
            {tabs.map((tb) => (
              <button
                key={tb.key}
                className={`tpl-tab ${filter === tb.key ? 'active' : ''}`}
                onClick={() => { if (editMode) exitEdit(); setFilter(tb.key); }}
              >
                {tb.label}
              </button>
            ))}
          </div>
          <div className="tpl-list">
            {templates.length === 0 && !loading && (
              <div className="empty-hint" style={{ padding: 12 }}>
                {filter === 'mine' ? '还没有自定义模板，编辑画布后点「保存为模板」' : '暂无模板'}
              </div>
            )}
            {templates.map((t) => (
              <div key={t.templateKey} className={`tpl-item-wrap ${activeKey === t.templateKey ? 'active' : ''}`}>
                <button
                  className="tpl-item"
                  onClick={() => setActiveKey(t.templateKey)}
                >
                  <span className="tpl-name">{t.name}</span>
                  <span className="tpl-meta">{t.toolChain.length} 工具{t.estimatedTime ? ` · ${t.estimatedTime}` : ''}</span>
                </button>
                {!t.builtin && (
                  <button className="tpl-del" title="删除自定义模板" onClick={() => deleteTemplate(t)}>
                    <IconTrash />
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* 编辑态：工具链编辑器 */}
        {editMode && (
          <div className="toolbox-section edit-panel">
            <div className="toolbox-title">
              编辑工具链 <span className="edit-badge">编辑中</span>
            </div>
            <div className="chain-editor">
              {chain.length === 0 && <div className="empty-hint" style={{ padding: 8 }}>从下方添加工具节点</div>}
              {chain.map((type, i) => {
                const m = TOOL_META[type] || { label: type };
                return (
                  <div key={i} className="chain-row">
                    <span className="chain-idx">{i + 1}</span>
                    <span className={`chain-label rf-text-${m.color || 'mint'}`}>{m.label}</span>
                    <span className="chain-ops">
                      <button className="chain-op" disabled={i === 0} onClick={() => moveTool(i, -1)} title="上移">↑</button>
                      <button className="chain-op" disabled={i === chain.length - 1} onClick={() => moveTool(i, 1)} title="下移">↓</button>
                      <button className="chain-op danger" onClick={() => removeTool(i)} title="删除"><IconTrash /></button>
                    </span>
                  </div>
                );
              })}
            </div>
            <div className="add-tool">
              <select className="form-input" value={addType} onChange={(e) => setAddType(e.target.value)}>
                {TOOL_OPTIONS.map((o) => (
                  <option key={o.type} value={o.type}>{o.label}</option>
                ))}
              </select>
              <button className="btn btn-secondary btn-sm" onClick={addTool}><IconPlus /> 添加</button>
            </div>
          </div>
        )}

        {!editMode && active && (
          <div className="toolbox-section">
            <div className="toolbox-title">模板说明</div>
            <p className="tpl-desc">{active.description}</p>
          </div>
        )}
      </aside>

      {/* Canvas */}
      <div className="canvas-area">
        <div className="canvas-toolbar">
          <span className="canvas-title">{active?.name || '流程编排'}</span>
          {editMode && <span className="edit-badge">编辑中</span>}
          <span style={{ flex: 1 }} />
          {!editMode ? (
            <button className="btn btn-secondary btn-sm" onClick={enterEdit} disabled={!active}>
              <IconEdit /> 编辑
            </button>
          ) : (
            <button className="btn btn-secondary btn-sm" onClick={exitEdit}>
              <IconClose /> 取消
            </button>
          )}
          <button className="btn btn-primary btn-sm" onClick={openSave} disabled={!editMode}>
            <IconTemplate /> 保存为模板
          </button>
          <button className="btn btn-ghost btn-sm" onClick={useTemplate} disabled={!active || editMode} title={editMode ? '保存后可复用' : ''}>
            <IconPlay /> 用此模板新建方案
          </button>
        </div>
        <div className="rf-canvas">
          {loading ? (
            <div className="empty-hint">模板加载中…</div>
          ) : (
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onNodeClick={onNodeClick}
              nodeTypes={nodeTypes}
              fitView
              proOptions={{ hideAttribution: true }}
            >
              <Background color="var(--accent-dim)" gap={22} />
              <Controls />
              <MiniMap pannable zoomable nodeColor={() => 'var(--accent)'} maskColor="rgba(0,0,0,0.45)" />
            </ReactFlow>
          )}
        </div>
      </div>

      {/* Property Panel */}
      <div className="property-panel">
        <div className="prop-header">
          <h2>{selected ? `${selected.data.label} · 属性` : '节点属性'}</h2>
          {selected && (
            <button className="btn btn-secondary btn-sm" onClick={() => setSelected(null)}>
              <IconClose />
            </button>
          )}
        </div>
        <div className="prop-body">
          {!selected ? (
            <div className="empty-prop">
              <p style={{ fontSize: 12 }}>
                {editMode
                  ? '在左侧「编辑工具链」中增删/排序节点，画布实时预览'
                  : '点击画布中的节点\n查看工具类型与说明'}
              </p>
            </div>
          ) : (
            <>
              <div className="prop-section">
                <div className="prop-section-title">基本设置</div>
                <div className="form-group">
                  <label className="form-label">节点名称</label>
                  <input className="form-input" value={selected.data.label} readOnly />
                </div>
                <div className="form-group">
                  <label className="form-label">工具类型</label>
                  <input className="form-input" value={selected.data.toolType} readOnly />
                </div>
              </div>
              <div className="prop-section">
                <div className="prop-section-title">说明</div>
                <p className="prop-desc">{selected.data.desc}</p>
              </div>
              <div className="prop-section">
                <div className="prop-section-title">提示词</div>
                <div className="form-group">
                  <textarea
                    className="form-textarea"
                    readOnly
                    defaultValue={`你是一个专业的 GIS 解决方案${selected.data.label}，请基于项目上下文和知识库检索结果，输出结构化的分析结论。`}
                  />
                </div>
              </div>
              {editMode && (
                <div className="prop-section">
                  <div className="prop-section-title">编辑操作</div>
                  <div className="prop-ops">
                    <button className="btn btn-secondary btn-sm" onClick={() => moveTool(Number(selected.id.slice(1)), -1)}><IconEdit /> 上移</button>
                    <button className="btn btn-secondary btn-sm" onClick={() => moveTool(Number(selected.id.slice(1)), 1)}>下移</button>
                    <button className="btn btn-danger btn-sm" onClick={() => removeTool(Number(selected.id.slice(1)))}><IconTrash /> 删除</button>
                  </div>
                </div>
              )}
              <button className="btn btn-primary btn-block" onClick={useTemplate} disabled={!active || editMode}>
                <IconPlay /> 用此模板新建方案
              </button>
            </>
          )}
        </div>
      </div>

      {/* 保存模态 */}
      <Modal
        open={saveOpen}
        title="保存为自定义模板"
        onClose={() => !saving && setSaveOpen(false)}
        footer={
          <>
            <button className="btn btn-secondary btn-sm" onClick={() => setSaveOpen(false)} disabled={saving}>取消</button>
            <button className="btn btn-primary btn-sm" onClick={confirmSave} disabled={saving}>
              <IconCheck /> {saving ? '保存中…' : '保存'}
            </button>
          </>
        }
      >
        <div className="form-group">
          <label className="form-label">模板名称</label>
          <input className="form-input" value={saveName} onChange={(e) => setSaveName(e.target.value)} placeholder="如：我的-智慧国土方案" />
        </div>
        <div className="form-group">
          <label className="form-label">说明（可选）</label>
          <textarea className="form-textarea" value={saveDesc} onChange={(e) => setSaveDesc(e.target.value)} placeholder="描述该模板适用场景" />
        </div>
        <div className="save-chain-preview">
          工具链：{chain.map((t) => (TOOL_META[t]?.label || t)).join(' → ')}
        </div>
      </Modal>
    </div>
  );
}
