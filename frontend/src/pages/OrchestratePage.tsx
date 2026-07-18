import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { orchestrateApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { IconWand, IconPlus } from '../components/ui/icons';

const humanize = (s: string) =>
  (s || '').split('_').map((w) => (w ? w[0] + w.slice(1).toLowerCase() : w)).join(' ');

const EXAMPLES = [
  '为某市级自然资源局做一个国土空间规划辅助决策平台，需要对接倾斜摄影和三维管线数据',
  '给一个园区招商做智慧园区物联网平台方案，强调设备接入和可视化大屏',
  '面向水利厅的防汛抗旱指挥系统，需要实时水文监测与预警',
];

export default function OrchestratePage() {
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [requirement, setRequirement] = useState('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<{
    reason: string;
    toolChain: string[];
    model?: string;
    usedFallback: boolean;
  } | null>(null);

  const run = async () => {
    if (!requirement.trim()) {
      showToast('请先描述方案需求', true);
      return;
    }
    setLoading(true);
    setResult(null);
    try {
      const { data } = await orchestrateApi.recommend(requirement.trim());
      setResult(data);
      if (data.usedFallback) showToast('已使用默认推荐链路（LLM 暂不可用）', false);
    } catch (err: any) {
      showToast(err.response?.data?.message || '智能编排失败', true);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>智能编排</h1>
          <p>用一句话描述方案需求，Agent 自动推荐最合适的工具链（Agent 自编排）</p>
        </div>
      </div>

      <div className="panel">
        <div className="panel-head"><h2>需求描述</h2></div>
        <div style={{ padding: '16px 20px' }}>
          <textarea
            className="req-textarea"
            rows={5}
            placeholder="例如：为某市级自然资源局做一个国土空间规划辅助决策平台，需要对接倾斜摄影和三维管线数据…"
            value={requirement}
            onChange={(e) => setRequirement(e.target.value)}
          />
          <div className="example-row">
            <span className="example-label">示例：</span>
            {EXAMPLES.map((ex, i) => (
              <button key={i} className="example-chip" onClick={() => setRequirement(ex)}>{ex.slice(0, 16)}…</button>
            ))}
          </div>
          <button className="btn btn-primary" onClick={run} disabled={loading}>
            <IconWand /> {loading ? '编排中…' : 'AI 推荐工具链'}
          </button>
        </div>
      </div>

      {result && (
        <div className="panel" style={{ marginTop: 20 }}>
          <div className="panel-head">
            <h2>推荐工具链</h2>
            {result.usedFallback && <span className="fallback-badge">默认链路</span>}
          </div>
          <div style={{ padding: '16px 20px' }}>
            {result.reason && <p className="orch-reason">{result.reason}</p>}
            <div className="chain-flow">
              {result.toolChain.map((t, i) => (
                <div key={t} className="chain-node">
                  <span className="chain-index">{i + 1}</span>
                  <span className="chain-name">{humanize(t)}</span>
                  <span className="chain-type">{t}</span>
                  {i < result.toolChain.length - 1 && <span className="chain-arrow">→</span>}
                </div>
              ))}
            </div>
            <button className="btn btn-secondary" style={{ marginTop: 16 }} onClick={() => navigate('/projects/new')}>
              <IconPlus /> 用此链路新建项目
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
