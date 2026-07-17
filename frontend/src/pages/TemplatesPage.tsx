import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { templateApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { IconTemplate, IconPlay, IconDoc, IconClock, IconPlus } from '../components/ui/icons';

// toolType → 中文展示名（与后端 PipelineEngine TOOL_BY_TYPE 对齐）
const TOOL_LABEL: Record<string, string> = {
  REQUIREMENT_ANALYSIS: '需求分析',
  PRODUCT_MATCHING: '产品匹配',
  CASE_RECOMMEND: '案例推荐',
  COMPETITOR_ANALYSIS: '竞品对比',
  ARCHITECTURE_DIAGRAM: '架构图',
  SOLUTION_OUTLINE: '方案大纲',
  SOLUTION_QC: '方案质检',
  SOLUTION_OUTPUT: '方案输出',
};

interface Tpl {
  id: number;
  templateKey: string;
  name: string;
  category: 'official' | 'community' | 'mine';
  description: string;
  toolChain: string[]; // toolType 列表
  estimatedTime?: string;
  usageCount?: number;
  builtin: boolean;
}

const CAT_LABEL: Record<string, string> = { official: '官方', community: '社区', mine: '我的' };

export default function TemplatesPage() {
  const [tab, setTab] = useState<'all' | 'official' | 'community' | 'mine'>('all');
  const [templates, setTemplates] = useState<Tpl[]>([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const { showToast } = useToast();

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const params = tab === 'all' ? undefined : { category: tab };
        const res = await templateApi.list(params?.category);
        if (cancelled) return;
        const list: any[] = res.data || [];
        setTemplates(list.map((t) => ({
          id: t.id,
          templateKey: t.templateKey,
          name: t.name,
          category: t.category || 'official',
          description: t.description || '',
          toolChain: parseChain(t.toolChain),
          estimatedTime: t.estimatedTime,
          usageCount: t.usageCount || 0,
          builtin: t.builtin,
        })));
      } catch (e) {
        if (!cancelled) showToast('模板列表加载失败');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [tab, showToast]);

  const useTemplate = (t: Tpl) => {
    showToast(`已加载模板「${t.name}」，请上传需求文档`);
    navigate(`/projects/new?template=${encodeURIComponent(t.templateKey)}`);
  };

  const preview = (t: Tpl) => {
    showToast(`「${t.name}」流程：${t.toolChain.map((c) => TOOL_LABEL[c] || c).join(' → ')}`);
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>模板市场</h1>
          <p>预置流程模板和社区共享模板，一键启动方案生成</p>
        </div>
        <button className="btn btn-primary" onClick={() => navigate('/pipeline')}><IconPlus /> 自定义流程</button>
      </div>

      <div className="tab-row">
        <button className={`tab-btn ${tab === 'all' ? 'active' : ''}`} onClick={() => setTab('all')}>全部模板</button>
        <button className={`tab-btn ${tab === 'official' ? 'active' : ''}`} onClick={() => setTab('official')}>官方预置</button>
        <button className={`tab-btn ${tab === 'community' ? 'active' : ''}`} onClick={() => setTab('community')}>社区共享</button>
        <button className={`tab-btn ${tab === 'mine' ? 'active' : ''}`} onClick={() => setTab('mine')}>我的模板</button>
      </div>

      {loading ? (
        <div className="empty-hint">模板加载中…</div>
      ) : templates.length === 0 ? (
        <div className="empty-hint">该分类下暂无模板</div>
      ) : (
        <div className="template-grid">
          {templates.map((t) => (
            <div className="template-card" key={t.id}>
              <div className="template-preview">
                <div className="preview-flow">
                  {t.toolChain.map((c, i) => (
                    <span key={i}>
                      {i > 0 && <span className="preview-arrow">→</span>}
                      <span className="preview-node" style={{ marginLeft: i > 0 ? 8 : 0 }}>{TOOL_LABEL[c] || c}</span>
                    </span>
                  ))}
                </div>
                <span className={`preview-badge badge-${t.category === 'official' ? 'official' : t.category === 'community' ? 'community' : 'mine'}`}>{CAT_LABEL[t.category]}</span>
              </div>
              <div className="template-body">
                <h3>{t.name}</h3>
                <p>{t.description}</p>
                <div className="template-meta">
                  <span><IconTemplate width={13} height={13} />{t.toolChain.length} 工具</span>
                  {t.estimatedTime && <span><IconClock width={13} height={13} />{t.estimatedTime}</span>}
                  <span>{t.usageCount} 次使用</span>
                </div>
              </div>
              <div className="template-footer">
                <button className="btn btn-primary btn-sm" onClick={() => useTemplate(t)}><IconPlay /> 使用模板</button>
                <button className="btn btn-secondary btn-sm" onClick={() => preview(t)}><IconDoc /> 预览</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// toolChain 后端为 JSON 字符串（JSONB）或数组，统一解析为 string[]
function parseChain(raw: any): string[] {
  if (Array.isArray(raw)) return raw.map(String);
  if (typeof raw === 'string') {
    try { return JSON.parse(raw).map(String); } catch { return []; }
  }
  return [];
}
