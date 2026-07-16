import { useState, useEffect } from 'react';
import {
  Card, Table, Button, Modal, Form, Input, Select, Switch, Tag, Typography, App, Space, Popconfirm
} from 'antd';
import { PlusOutlined, ApiOutlined, DeleteOutlined } from '@ant-design/icons';
import { llmApi } from '../api/client';

const { Title, Paragraph } = Typography;

interface Provider {
  id: number;
  name: string;
  providerType: string;
  endpoint: string;
  isDefault: boolean;
  hasApiKey: boolean;
}

export default function LlmConfigPage() {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const { message } = App.useApp();

  const fetchProviders = async () => {
    setLoading(true);
    try {
      const { data } = await llmApi.list();
      setProviders(data);
    } catch {
      // 后端未启动时用模拟数据
      setProviders([
        { id: 1, name: 'GPT-4o', providerType: 'openai_compatible', endpoint: 'https://api.openai.com/v1', isDefault: true, hasApiKey: true },
        { id: 2, name: 'DeepSeek-V3', providerType: 'openai_compatible', endpoint: 'https://api.deepseek.com/v1', isDefault: false, hasApiKey: false },
      ]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchProviders(); }, []);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      await llmApi.create(values);
      message.success('添加成功');
      setModalOpen(false);
      form.resetFields();
      fetchProviders();
    } catch (err: any) {
      if (err.response) message.error(err.response.data?.error || '添加失败');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await llmApi.delete(id);
      message.success('删除成功');
      fetchProviders();
    } catch {
      message.error('删除失败');
    }
  };

  const handleTest = async (id: number) => {
    try {
      const { data } = await llmApi.test(id);
      message.success(data.message || '连接成功');
    } catch {
      message.error('连接测试失败');
    }
  };

  const columns = [
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '类型', dataIndex: 'providerType', key: 'providerType', render: (t: string) => <Tag>{t}</Tag> },
    { title: 'API 地址', dataIndex: 'endpoint', key: 'endpoint', ellipsis: true },
    {
      title: 'API Key', dataIndex: 'hasApiKey', key: 'hasApiKey',
      render: (v: boolean) => v ? <Tag color="green">已配置</Tag> : <Tag color="red">未配置</Tag>,
    },
    {
      title: '默认', dataIndex: 'isDefault', key: 'isDefault',
      render: (v: boolean) => v ? <Tag color="blue">默认</Tag> : null,
    },
    {
      title: '操作', key: 'actions',
      render: (_: any, record: Provider) => (
        <Space>
          <Button size="small" icon={<ApiOutlined />} onClick={() => handleTest(record.id)}>测试</Button>
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ maxWidth: 900, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>大模型配置</Title>
          <Paragraph type="secondary">管理你的 LLM Provider，工具执行时可选择使用哪个模型</Paragraph>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          添加 Provider
        </Button>
      </div>

      <Card>
        <Table
          dataSource={providers}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={false}
        />
      </Card>

      <Modal
        title="添加 LLM Provider"
        open={modalOpen}
        onOk={handleCreate}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
        okText="添加"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="Provider 名称" rules={[{ required: true }]}>
            <Input placeholder="如：GPT-4o、DeepSeek-V3" />
          </Form.Item>
          <Form.Item name="providerType" label="类型" initialValue="openai_compatible">
            <Select
              options={[
                { label: 'OpenAI 兼容', value: 'openai_compatible' },
                { label: '本地模型', value: 'local' },
              ]}
            />
          </Form.Item>
          <Form.Item name="endpoint" label="API 地址" rules={[{ required: true }]}>
            <Input placeholder="https://api.openai.com/v1" />
          </Form.Item>
          <Form.Item name="apiKey" label="API Key">
            <Input.Password placeholder="sk-..." />
          </Form.Item>
          <Form.Item name="isDefault" label="设为默认" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
