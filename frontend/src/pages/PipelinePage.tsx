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
import {
  IconBrain,
  IconSearch,
  IconDoc,
  IconTemplate,
  IconClose,
  IconPlay,
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

/** 根据模板工具链构建 React Flow 节点（横向布局） */
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

export default function PipelinePage() {
  const [templates, setTemplates] = useState<Tpl[]>([]);
  const [activeKey, setActiveKey] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Node | null>(null);
  const { showToast } = useToast();
  const navigate = useNavigate();

  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await templateApi.list();
        if (cancelled) return;
        const list: any[] = res.data || [];
        const mapped = list.map((t) => ({
          id: t.id,
          templateKey: t.templateKey,
          name: t.name,
          category: t.category || 'official',
          description: t.description || '',
          toolChain: parseChain(t.toolChain),
          estimatedTime: t.estimatedTime,
          builtin: t.builtin,
        }));
        setTemplates(mapped);
        // 默认选中「全套方案生成」
        const def = mapped.find((t) => t.templateKey === 'full_solution') || mapped[0];
        if (def) setActiveKey(def.templateKey);
      } catch (e) {
        if (!cancelled) showToast('模板加载失败');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [showToast]);

  const active = useMemo(
    () => templates.find((t) => t.templateKey === activeKey),
    [templates, activeKey],
  );

  // 模板切换 → 重建画布
  useEffect(() => {
    if (!active) return;
    const { nodes: ns, edges: es } = buildGraph(active.toolChain);
    setNodes(ns);
    setEdges(es);
    setSelected(null);
  }, [active, setNodes, setEdges]);

  const onNodeClick = useCallback((_: any, node: Node) => setSelected(node), []);

  const useTemplate = () => {
    if (!active) return;
    showToast(`已加载模板「${active.name}」，请在项目中运行`);
    navigate(`/projects/new?template=${encodeURIComponent(active.templateKey)}`);
  };

  return (
    <div className="pipeline-layout">
      {/* Toolbox：真实模板列表 */}
      <aside className="pipeline-toolbox">
        <div className="toolbox-section">
          <div className="toolbox-title">流程模板（{templates.length}）</div>
          <div className="tpl-list">
            {templates.map((t) => (
              <button
                key={t.templateKey}
                className={`tpl-item ${activeKey === t.templateKey ? 'active' : ''}`}
                onClick={() => setActiveKey(t.templateKey)}
              >
                <span className="tpl-name">{t.name}</span>
                <span className="tpl-meta">{t.toolChain.length} 工具{t.estimatedTime ? ` · ${t.estimatedTime}` : ''}</span>
              </button>
            ))}
          </div>
        </div>
        {active && (
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
          <span style={{ flex: 1 }} />
          <button className="btn btn-primary btn-sm" onClick={useTemplate} disabled={!active}>
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
              <p style={{ fontSize: 12 }}>点击画布中的节点<br />查看工具类型与说明</p>
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
              <button className="btn btn-primary btn-block" onClick={useTemplate} disabled={!active}>
                <IconPlay /> 用此模板新建方案
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
