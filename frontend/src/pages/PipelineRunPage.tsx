import { useState, useEffect } from 'react';
import { Card, Tag, Button, Typography, Spin, Descriptions, Alert, Space, Progress } from 'antd';
import {
  CheckCircleOutlined,
  SyncOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  DownloadOutlined,
} from '@ant-design/icons';

const { Title, Text, Paragraph } = Typography;

// 模拟 Pipeline 状态（后续替换为 SSE 实时推送）
interface ToolStatus {
  key: string;
  title: string;
  status: 'wait' | 'process' | 'finish' | 'error';
  output?: any;
}

const mockTools: ToolStatus[] = [
  { key: 'req-analysis', title: '需求分析', status: 'finish', output: {
    functional: ['GIS 数据管理与发布', '二维/三维地图可视化', '空间查询与分析'],
    nonFunctional: ['并发用户数 ≥ 500', '响应时间 < 2s', '7×24 可用'],
    constraints: ['需对接现有数据中台', '国产化适配（信创）'],
    industry: '智慧城市',
  }},
  { key: 'product-match', title: '产品匹配', status: 'process', output: null },
  { key: 'solution-output', title: '方案输出', status: 'wait', output: null },
];

export default function PipelineRunPage() {
  const [tools, setTools] = useState<ToolStatus[]>(mockTools);

  // 模拟流水线推进
  useEffect(() => {
    const timer = setInterval(() => {
      setTools((prev) => {
        const newTools = [...prev];
        const runningIdx = newTools.findIndex((t) => t.status === 'process');
        if (runningIdx >= 0 && runningIdx < newTools.length - 1) {
          newTools[runningIdx].status = 'finish';
          newTools[runningIdx].output = {
            products: [
              { name: 'SuperMap iServer', version: '11i', reason: 'GIS 服务发布与管理核心产品', coverage: '95%' },
              { name: 'SuperMap iPortal', version: '11i', reason: 'GIS 门户与资源中心', coverage: '85%' },
              { name: 'SuperMap iDesktopX', version: '11i', reason: '桌面端数据处理与分析', coverage: '80%' },
            ],
          };
          if (runningIdx + 1 < newTools.length) {
            newTools[runningIdx + 1].status = 'process';
          }
        }
        return newTools;
      });
    }, 4000);

    return () => clearInterval(timer);
  }, []);

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'finish': return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
      case 'process': return <SyncOutlined spin style={{ color: '#1890ff' }} />;
      case 'error': return <CloseCircleOutlined style={{ color: '#ff4d4f' }} />;
      default: return <ClockCircleOutlined style={{ color: '#d9d9d9' }} />;
    }
  };

  const getStatusTag = (status: string) => {
    switch (status) {
      case 'finish': return <Tag color="success">完成</Tag>;
      case 'process': return <Tag color="processing">运行中</Tag>;
      case 'error': return <Tag color="error">失败</Tag>;
      default: return <Tag>等待中</Tag>;
    }
  };

  const progress = Math.round((tools.filter((t) => t.status === 'finish').length / tools.length) * 100);

  return (
    <div style={{ maxWidth: 900, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>XX市智慧城市项目</Title>
          <Text type="secondary">方案生成中...</Text>
        </div>
        <Space>
          <Progress type="circle" percent={progress} size={48} />
          <Tag color="processing" icon={<SyncOutlined spin />}>运行中</Tag>
        </Space>
      </div>

      {/* 工具执行流水线 */}
      <div style={{ display: 'flex', gap: 16, marginBottom: 24 }}>
        {tools.map((tool) => (
          <Card
            key={tool.key}
            size="small"
            style={{
              flex: 1,
              textAlign: 'center',
              borderColor: tool.status === 'process' ? '#1890ff' : undefined,
            }}
            title={
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 }}>
                {getStatusIcon(tool.status)}
                <span>{tool.title}</span>
              </div>
            }
          >
            {getStatusTag(tool.status)}
            {tool.status === 'process' && (
              <div style={{ marginTop: 8 }}>
                <Spin size="small" />
              </div>
            )}
          </Card>
        ))}
      </div>

      {/* 中间产物展示 */}
      <Card title="📋 中间产物" style={{ marginBottom: 24 }}>
        {tools.filter((t) => t.output).map((tool) => (
          <div key={tool.key} style={{ marginBottom: 24 }}>
            <Title level={5}>
              {getStatusIcon(tool.status)} {tool.title} 输出
            </Title>
            {tool.key === 'req-analysis' && tool.output && (
              <Descriptions bordered size="small" column={2}>
                <Descriptions.Item label="功能需求" span={2}>
                  {(tool.output.functional as string[]).map((f, i) => (
                    <Tag key={i} color="blue" style={{ marginBottom: 4 }}>{f}</Tag>
                  ))}
                </Descriptions.Item>
                <Descriptions.Item label="非功能需求" span={2}>
                  {(tool.output.nonFunctional as string[]).map((n, i) => (
                    <Tag key={i} color="green" style={{ marginBottom: 4 }}>{n}</Tag>
                  ))}
                </Descriptions.Item>
                <Descriptions.Item label="约束条件" span={2}>
                  {(tool.output.constraints as string[]).map((c, i) => (
                    <Tag key={i} color="orange" style={{ marginBottom: 4 }}>{c}</Tag>
                  ))}
                </Descriptions.Item>
                <Descriptions.Item label="行业场景">
                  <Tag color="purple">{tool.output.industry}</Tag>
                </Descriptions.Item>
              </Descriptions>
            )}
            {tool.key === 'product-match' && tool.output && (
              <div>
                {(tool.output.products as any[]).map((p, i) => (
                  <Card key={i} size="small" style={{ marginBottom: 8 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <Text strong>{p.name}</Text>
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
        <Space size="middle">
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            disabled={tools.some((t) => t.status === 'process')}
          >
            下载 Word 方案文档 (.docx)
          </Button>
          <Button
            icon={<DownloadOutlined />}
            disabled={tools.some((t) => t.status === 'process')}
          >
            下载 Markdown (.md)
          </Button>
        </Space>
      </Card>
    </div>
  );
}
