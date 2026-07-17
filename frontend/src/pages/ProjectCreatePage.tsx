import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Upload, Button, Radio, Typography, App, Space, Steps } from 'antd';
import { InboxOutlined, ThunderboltOutlined, BuildOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { projectApi } from '../api/client';

const { Title, Text, Paragraph } = Typography;
const { Dragger } = Upload;

const templates = [
  {
    key: 'quick_selection',
    title: '快速选型',
    description: '需求分析 → 产品匹配',
    icon: <ThunderboltOutlined style={{ fontSize: 32, color: '#1565C0' }} />,
    tools: '2 个工具',
    time: '约 5 分钟',
  },
  {
    key: 'full_solution',
    title: '全套方案生成',
    description: '需求分析 → 产品匹配 → 方案输出',
    icon: <BuildOutlined style={{ fontSize: 32, color: '#1565C0' }} />,
    tools: '3 个工具',
    time: '约 15 分钟',
  },
];

export default function ProjectCreatePage() {
  const [template, setTemplate] = useState('quick_selection');
  const [uploadedFile, setUploadedFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [name, setName] = useState('');
  const navigate = useNavigate();
  const { message: msg } = App.useApp();

  const uploadProps: UploadProps = {
    name: 'file',
    multiple: false,
    maxCount: 1,
    beforeUpload: (file) => {
      const isValid = file.type === 'application/pdf'
        || file.name.endsWith('.docx')
        || file.name.endsWith('.doc')
        || file.type === 'text/plain';
      if (!isValid) {
        msg.error('仅支持 PDF、Word、TXT 格式');
        return Upload.LIST_IGNORE;
      }
      setUploadedFile(file);
      if (!name) setName(file.name.replace(/\.[^.]+$/, ''));
      return false;
    },
    onRemove: () => setUploadedFile(null),
  };

  const handleStart = async () => {
    if (!uploadedFile) {
      msg.warning('请先上传需求文档');
      return;
    }
    if (!name.trim()) {
      msg.warning('请输入方案名称');
      return;
    }
    setLoading(true);
    try {
      const formData = new FormData();
      formData.append('name', name.trim());
      formData.append('description', '');
      formData.append('templateId', template);
      formData.append('file', uploadedFile);
      const { data } = await projectApi.create(formData);
      msg.success('项目创建成功，正在启动方案生成...');
      navigate(`/projects/${data.id}/run`);
    } catch (err: any) {
      msg.error(err.response?.data?.error || '创建失败，请检查后端服务是否启动');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>
      <Title level={3}>新建方案</Title>
      <Paragraph type="secondary">
        上传客户需求文档，选择执行模式，平台将自动生成方案初稿。
      </Paragraph>

      <Steps
        current={0}
        size="small"
        style={{ marginBottom: 32 }}
        items={[
          { title: '上传需求' },
          { title: 'AI 生成' },
          { title: '下载方案' },
        ]}
      />

      <Card title="📄 上传需求文档" style={{ marginBottom: 24 }}>
        <Dragger {...uploadProps}>
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>
          <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
          <p className="ant-upload-hint">
            支持 PDF、Word (.docx)、纯文本 (.txt) 格式，最大 50MB
          </p>
        </Dragger>
      </Card>

      <Card title="📝 方案名称" style={{ marginBottom: 24 }}>
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="如：XX市智慧城市 GIS 解决方案"
          style={{
            width: '100%', padding: '8px 12px', borderRadius: 6,
            border: '1px solid #d9d9d9', fontSize: 14,
          }}
        />
      </Card>

      <Card title="📋 选择执行模式" style={{ marginBottom: 24 }}>
        <Radio.Group
          value={template}
          onChange={(e) => setTemplate(e.target.value)}
          style={{ width: '100%' }}
        >
          <Space direction="vertical" style={{ width: '100%' }}>
            {templates.map((t) => (
              <Radio
                key={t.key}
                value={t.key}
                style={{
                  width: '100%',
                  padding: '12px 16px',
                  border: template === t.key ? '2px solid #1565C0' : '2px solid #f0f0f0',
                  borderRadius: 8,
                  marginBottom: 8,
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                  {t.icon}
                  <div>
                    <Text strong>{t.title}</Text>
                    <br />
                    <Text type="secondary">
                      {t.description} · {t.tools} · {t.time}
                    </Text>
                  </div>
                </div>
              </Radio>
            ))}
          </Space>
        </Radio.Group>
      </Card>

      <Button
        type="primary"
        size="large"
        block
        loading={loading}
        disabled={!uploadedFile}
        onClick={handleStart}
      >
        🚀 开始生成方案
      </Button>
    </div>
  );
}
