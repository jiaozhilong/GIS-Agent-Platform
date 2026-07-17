import { useState, type ChangeEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { projectApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { IconDoc, IconPlay, IconBrain, IconTemplate, IconClose } from '../components/ui/icons';

const TEMPLATES = [
  { key: 'quick_selection', title: '快速选型', desc: '需求分析 → 产品匹配', icon: <IconBrain />, tools: '2 个工具', time: '约 5 分钟' },
  { key: 'full_solution', title: '全套方案生成', desc: '需求分析 → 方案输出 → PPT', icon: <IconTemplate />, tools: '5 个工具', time: '约 15 分钟' },
];

export default function ProjectCreatePage() {
  const [params] = useSearchParams();
  const [template, setTemplate] = useState<string>(params.get('template') || 'full_solution');
  const [file, setFile] = useState<File | null>(null);
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);
  const [dragOver, setDragOver] = useState(false);
  const navigate = useNavigate();
  const { showToast } = useToast();

  const onFile = (f: File | null) => {
    if (!f) return;
    const ok = f.type === 'application/pdf' || f.name.endsWith('.docx') || f.name.endsWith('.doc') || f.type === 'text/plain';
    if (!ok) { showToast('仅支持 PDF、Word、TXT 格式', true); return; }
    setFile(f);
    if (!name) setName(f.name.replace(/\.[^.]+$/, ''));
  };

  const onInput = (e: ChangeEvent<HTMLInputElement>) => onFile(e.target.files?.[0] || null);

  const handleStart = async () => {
    if (!file) { showToast('请先上传需求文档', true); return; }
    if (!name.trim()) { showToast('请输入方案名称', true); return; }
    setLoading(true);
    try {
      const fd = new FormData();
      fd.append('name', name.trim());
      fd.append('description', '');
      fd.append('templateId', template);
      fd.append('file', file);
      const { data } = await projectApi.create(fd);
      showToast('项目创建成功，正在进入方案工作台');
      navigate(`/projects/${data.id}`);
    } catch (e: any) {
      showToast(e.response?.data?.error || '创建失败，请检查后端服务是否启动', true);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 820, margin: '0 auto' }}>
      <div className="page-head">
        <div>
          <h1>新建方案</h1>
          <p>上传客户需求文档，选择执行模式，平台将自动生成方案初稿</p>
        </div>
      </div>

      {/* Steps */}
      <div className="flex gap-3" style={{ marginBottom: 28 }}>
        {['上传需求', 'AI 生成', '下载方案'].map((s, i) => (
          <div key={s} className="flex gap-2" style={{ alignItems: 'center', flex: 1 }}>
            <span className="badge badge-mint" style={{ width: 24, height: 24, borderRadius: '50%', display: 'grid', placeItems: 'center', padding: 0 }}>{i + 1}</span>
            <span style={{ fontSize: 13, color: i === 0 ? 'var(--text)' : 'var(--muted)' }}>{s}</span>
          </div>
        ))}
      </div>

      {/* Upload */}
      <div className="panel" style={{ marginBottom: 20 }}>
        <div className="panel-head"><h2>上传需求文档</h2></div>
        <div className="panel-body">
          <label
            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(e) => { e.preventDefault(); setDragOver(false); onFile(e.dataTransfer.files?.[0] || null); }}
            style={{
              display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
              gap: 10, padding: 36, border: `1px dashed ${dragOver ? 'var(--mint)' : 'var(--line)'}`,
              borderRadius: 'var(--radius-md)', background: 'rgba(255,255,255,.02)', cursor: 'pointer', transition: '.2s',
            }}
          >
            <IconDoc width={36} height={36} style={{ color: 'var(--mint)' }} />
            <div style={{ fontSize: 14, fontWeight: 620 }}>{file ? file.name : '点击或拖拽文件到此处上传'}</div>
            <div style={{ fontSize: 12, color: 'var(--muted-2)' }}>支持 PDF、Word (.docx)、纯文本 (.txt)</div>
            <input type="file" accept=".pdf,.docx,.doc,.txt" hidden onChange={onInput} />
          </label>
          {file && (
            <div className="flex gap-2" style={{ marginTop: 12, alignItems: 'center' }}>
              <span className="badge badge-mint"><IconDoc width={12} height={12} /> {file.name}</span>
              <button className="btn btn-danger btn-sm" onClick={() => setFile(null)}><IconClose /> 移除</button>
            </div>
          )}
        </div>
      </div>

      {/* Name */}
      <div className="panel" style={{ marginBottom: 20 }}>
        <div className="panel-head"><h2>方案名称</h2></div>
        <div className="panel-body">
          <input className="input" placeholder="如：XX市智慧城市 GIS 解决方案" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
      </div>

      {/* Template */}
      <div className="panel" style={{ marginBottom: 24 }}>
        <div className="panel-head"><h2>选择执行模式</h2></div>
        <div className="panel-body">
          <div className="flex gap-4" style={{ flexWrap: 'wrap' }}>
            {TEMPLATES.map((t) => (
              <div
                key={t.key}
                onClick={() => setTemplate(t.key)}
                className="card"
                style={{
                  flex: '1 1 240px', padding: 18, cursor: 'pointer',
                  borderColor: template === t.key ? 'var(--mint)' : 'var(--line)',
                  boxShadow: template === t.key ? '0 0 0 2px rgba(146,246,190,.12)' : 'none',
                }}
              >
                <div className="flex gap-3" style={{ alignItems: 'center', marginBottom: 8 }}>
                  <span className={`node-icon ${template === t.key ? 'mint' : ''}`}>{t.icon}</span>
                  <div style={{ fontWeight: 700 }}>{t.title}</div>
                </div>
                <div style={{ fontSize: 12, color: 'var(--muted)' }}>{t.desc}</div>
                <div style={{ fontSize: 11, color: 'var(--muted-2)', marginTop: 6 }}>{t.tools} · {t.time}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <button className="btn btn-primary btn-block" onClick={handleStart} disabled={loading || !file}>
        <IconPlay /> {loading ? '创建中…' : '开始生成方案'}
      </button>
    </div>
  );
}
