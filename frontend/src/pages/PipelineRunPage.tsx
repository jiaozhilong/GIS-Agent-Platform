import { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import {
  Card, Tag, Button, Typography, Spin, Descriptions, Alert, Space, Progress, App
} from 'antd';
import {
  CheckCircleOutlined, SyncOutlined, CloseCircleOutlined, ClockCircleOutlined,
  DownloadOutlined,
} from '@ant-design/icons';
import { projectApi, downloadBlob } from '../api/client';

const { Title, Text, Paragraph } = Typography;

interface ToolStatus {
  toolType: string;
  toolOrder: number;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED';
  output?: any;
  errorMessage?: string;
}

interface PipelineStatus {
  pipelineRunId: number;
  projectId: number;
  status: string;
  tools: ToolStatus[];
  context?: any;
}

const TOOL_LABELS: Record<string, string> = {
  REQUIREMENT_ANALYSIS: '需求分析',
  PRODUCT_MATCHING: '产品匹配',
  CASE_RECOMMEND: '案例推荐',
  COMPETITIVE_ANALYSIS: '竞品对比',
  ARCHITECTURE_DIAGRAM: '架构图生成',
  SOLUTION_OUTLINE: '方案框架生成',
  SOLUTION_QC: '方案质检',
  SOLUTION_OUTPUT: '方案输出',
  PPT_OUTPUT: 'PPT 输出',
};

export default function PipelineRunPage() {
  const { id } = useParams();
  const projectId = Number(id);
  const [status, setStatus] = useState<PipelineStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [docReady, setDocReady] = useState(false);
  const { message } = App.useApp();
  const pollTimer = useRef<number | null>(null);

  // 启动流水线 + 轮询状态
  useEffect(() => {
    let cancelled = false;

    const startAndPoll = async () => {
      try {
        // 先检查是否已有运行记录
        const { data: existing } = await projectApi.status(projectId);
        if (existing.status === 'NO_RUN') {
          await projectApi.run(projectId);
        }
        if (cancelled) return;
        poll();
      } catch (err: any) {
        message.error(err.response?.data?.error || '启动流水线失败');
        setLoading(false);
      }
    };

    const poll = async () => {
      try {
        const { data } = await projectApi.status(projectId);
        if (cancelled) return;
        setStatus(data);
        setLoading(false);
        if (data.status === 'SUCCESS' || data.status === 'PARTIAL' || data.status === 'FAILED') {
          if (data.status === 'SUCCESS' || data.status === 'PARTIAL') {
            setDocReady(true);
          }
          if (pollTimer.current) window.clearInterval(pollTimer.current);
        }
      } catch {
        if (cancelled) return;
        setLoading(false);
      }
    };

    startAndPoll();
    pollTimer.current = window.setInterval(poll, 3000);

    return () => {
      cancelled = true;
      if (pollTimer.current) window.clearInterval(pollTimer.current);
    };
  }, [projectId]);

  const getStatusIcon = (s: string) => {
    switch (s) {
      case 'SUCCESS': return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
      case 'RUNNING': return <SyncOutlined spin style={{ color: '#1890ff' }} />;
      case 'FAILED': return <CloseCircleOutlined style={{ color: '#ff4d4f' }} />;
      default: return <ClockCircleOutlined style={{ color: '#d9d9d9' }} />;
    }
  };

  const getStatusTag = (s: string) => {
    switch (s) {
      case 'SUCCESS': return <Tag color="success">完成</Tag>;
      case 'RUNNING': return <Tag color="processing">运行中</Tag>;
      case 'FAILED': return <Tag color="error">失败</Tag>;
      case 'SKIPPED': return <Tag>跳过</Tag>;
      default: return <Tag>等待中</Tag>;
    }
  };

  const handleDownload = async (type: 'md' | 'docx') => {
    try {
      const { data } = type === 'md'
        ? await projectApi.downloadMd(projectId)
        : await projectApi.downloadDocx(projectId);
      const fileName = type === 'md' ? `solution_${projectId}.md` : `solution_${projectId}.docx`;
      downloadBlob(data, fileName);
      message.success('下载成功');
    } catch (err: any) {
      message.error(err.response?.data?.error || '下载失败');
    }
  };

  if (loading && !status) {
    return (
      <div style={{ textAlign: 'center', padding: 80 }}>
        <Spin size="large" />
        <Paragraph style={{ marginTop: 16 }}>正在初始化流水线...</Paragraph>
      </div>
    );
  }

  const tools = status?.tools || [];
  const finishedCount = tools.filter((t) => t.status === 'SUCCESS' || t.status === 'FAILED').length;
  const progress = tools.length > 0 ? Math.round((finishedCount / tools.length) * 100) : 0;
  const isDone = status?.status === 'SUCCESS' || status?.status === 'PARTIAL';

  return (
    <div style={{ maxWidth: 900, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>方案生成中</Title>
          <Text type="secondary">项目 ID：{projectId}</Text>
        </div>
        <Space>
          <Progress type="circle" percent={progress} size={48} />
          {(status?.status === 'RUNNING' || status?.status === 'PENDING') && (
            <Tag color="processing" icon={<SyncOutlined spin />}>运行中</Tag>
          )}
          {status?.status === 'FAILED' && <Tag color="error">失败</Tag>}
          {isDone && <Tag color="success">已完成</Tag>}
        </Space>
      </div>

      {/* 工具执行流水线 */}
      <div style={{ display: 'flex', gap: 16, marginBottom: 24, flexWrap: 'wrap' }}>
        {tools.map((tool) => (
          <Card
            key={tool.toolOrder}
            size="small"
            style={{
              flex: '1 1 200px',
              textAlign: 'center',
              borderColor: tool.status === 'RUNNING' ? '#1890ff' : undefined,
            }}
            title={
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                {getStatusIcon(tool.status)}
                <span>{TOOL_LABELS[tool.toolType] || tool.toolType}</span>
              </div>
            }
          >
            {getStatusTag(tool.status)}
            {tool.status === 'RUNNING' && (
              <div style={{ marginTop: 8 }}><Spin size="small" /></div>
            )}
            {tool.status === 'FAILED' && tool.errorMessage && (
              <Paragraph type="danger" style={{ marginTop: 8, fontSize: 12 }}>
                {tool.errorMessage}
              </Paragraph>
            )}
          </Card>
        ))}
        {tools.length === 0 && (
          <Alert message="等待流水线启动..." type="info" showIcon />
        )}
      </div>

      {/* 中间产物展示 */}
      <Card title="📋 中间产物" style={{ marginBottom: 24 }}>
        {tools.filter((t) => t.output).map((tool) => (
          <div key={tool.toolOrder} style={{ marginBottom: 24 }}>
            <Title level={5}>
              {getStatusIcon(tool.status)} {TOOL_LABELS[tool.toolType] || tool.toolType} 输出
            </Title>
            {tool.toolType === 'REQUIREMENT_ANALYSIS' && tool.output && (
              <Descriptions bordered size="small" column={2}>
                <Descriptions.Item label="功能需求" span={2}>
                  {(tool.output.functional || []).map((f: string, i: number) => (
                    <Tag key={i} color="blue" style={{ marginBottom: 4 }}>{f}</Tag>
                  ))}
                </Descriptions.Item>
                <Descriptions.Item label="非功能需求" span={2}>
                  {(tool.output.nonFunctional || []).map((n: string, i: number) => (
                    <Tag key={i} color="green" style={{ marginBottom: 4 }}>{n}</Tag>
                  ))}
                </Descriptions.Item>
                <Descriptions.Item label="约束条件" span={2}>
                  {(tool.output.constraints || []).map((c: string, i: number) => (
                    <Tag key={i} color="orange" style={{ marginBottom: 4 }}>{c}</Tag>
                  ))}
                </Descriptions.Item>
                <Descriptions.Item label="行业场景">
                  <Tag color="purple">{tool.output.industry}</Tag>
                </Descriptions.Item>
              </Descriptions>
            )}
            {tool.toolType === 'PRODUCT_MATCHING' && tool.output && (
              <div>
                {(tool.output as any[]).map((p, i) => (
                  <Card key={i} size="small" style={{ marginBottom: 8 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <Text strong>{p.productName}</Text>
                        <Tag style={{ marginLeft: 8 }}>{p.version}</Tag>
                      </div>
                      <Tag color="blue">覆盖率 {p.coverage}</Tag>
                    </div>
                    <Paragraph type="secondary" style={{ marginTop: 4, marginBottom: 0 }}>
                      {p.reason}
                    </Paragraph>
                  </Card>
                ))}
              </div>
            )}
          </div>
        ))}
        {tools.filter((t) => t.output).length === 0 && (
          <Alert message="流水线正在运行中，工具输出将实时展示..." type="info" showIcon />
        )}
      </Card>

      {/* 下载区域 */}
      <Card title="📥 方案下载" style={{ marginBottom: 24 }}>
        {status?.status === 'FAILED' ? (
          <Alert message="流水线执行失败，请检查 LLM Provider 配置和 IMA 连接后重试" type="error" showIcon />
        ) : (
          <Space size="middle">
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              disabled={!docReady}
              onClick={() => handleDownload('docx')}
            >
              下载 Word 方案文档 (.docx)
            </Button>
            <Button
              icon={<DownloadOutlined />}
              disabled={!docReady}
              onClick={() => handleDownload('md')}
            >
              下载 Markdown (.md)
            </Button>
          </Space>
        )}
        {!docReady && isDone && (
          <Paragraph type="secondary" style={{ marginTop: 8 }}>
            正在准备下载文件...
          </Paragraph>
        )}
      </Card>
    </div>
  );
}
