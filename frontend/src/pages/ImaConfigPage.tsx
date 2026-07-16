import { useState, useEffect } from 'react';
import {
  Card, Table, Button, Modal, Form, Input, Select, Slider, Tag, Typography, App, Space, Popconfirm
} from 'antd';
import { PlusOutlined, LinkOutlined, DeleteOutlined } from '@ant-design/icons';
import { imaApi } from '../api/client';

const { Title, Paragraph } = Typography;

interface ImaConfig {
  id: number;
  kbId: string;
  kbName: string;
  kbType: string;
  purpose: string;
  searchWeight: number;
  enabled: boolean;
}

const purposeLabels: Record<string, string> = {
  product_doc: '产品文档',
  case_lib: '案例库',
  industry_standard: '行业标准',
  competitor: '竞品信息',
  general: '通用',
};

const purposeColors: Record<string, string> = {
  product_doc: 'blue',
  case_lib: 'green',
  industry_standard: 'purple',
  competitor: 'orange',
  general: 'default',
};

export default function ImaConfigPage() {
  const [configs, setConfigs] = useState<ImaConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();
  const { message } = App.useApp();

  const fetchConfigs = async () => {
    setLoading(true);
    try {
      const { data } = await imaApi.listConfigs();
      setConfigs(data);
    } catch {
      setConfigs([
        { id: 1, kbId: 'kb-supermap-products', kbName: '超图产品智答库', kbType: 'subscribed', purpose: 'product_doc', searchWeight: 0.8, enabled: true },
        { id: 2, kbId: 'kb-case-library', kbName: '行业案例库', kbType: 'subscribed', purpose: 'case_lib', searchWeight: 0.6, enabled: true },
        { id: 3, kbId: 'kb-competitors', kbName: '竞品信息库', kbType: 'owned', purpose: 'competitor', searchWeight: 0.4, enabled: false },
      ]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchConfigs(); }, []);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      await imaApi.createConfig(values);
      message.success('添加成功');
      setModalOpen(false);
      form.resetFields();
      fetchConfigs();
    } catch (err: any) {
      if (err.response) message.error(err.response.data?.error || '添加失败');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await imaApi.deleteConfig(id);
      message.success('删除成功');
      fetchConfigs();
    } catch {
      message.error('删除失败');
    }
  };

  const handleTest = async (id: number) => {
    try {
      const { data } = await imaApi.testConfig(id);
      message.success(data.message || '连接成功');
    } catch {
      message.error('连接测试失败');
    }
  };

  const columns = [
    { title: '知识库名称', dataIndex: 'kbName', key: 'kbName' },
    { title: 'ID', dataIndex: 'kbId', key: 'kbId', ellipsis: true },
    {
      title: '类型', dataIndex: 'kbType', key: 'kbType',
      render: (t: string) => t === 'subscribed' ? <Tag>订阅</Tag> : <Tag color="gold">自建</Tag>,
    },
    {
      title: '用途', dataIndex: 'purpose', key: 'purpose',
      render: (p: string) => <Tag color={purposeColors[p] || 'default'}>{purposeLabels[p] || p}</Tag>,
    },
    {
      title: '检索权重', dataIndex: 'searchWeight', key: 'searchWeight',
      render: (w: number) => `${(w * 100).toFixed(0)}%`,
    },
    {
      title: '状态', dataIndex: 'enabled', key: 'enabled',
      render: (v: boolean) => v ? <Tag color="success">已启用</Tag> : <Tag>已禁用</Tag>,
    },
    {
      title: '操作', key: 'actions',
      render: (_: any, record: ImaConfig) => (
        <Space>
          <Button size="small" icon={<LinkOutlined />} onClick={() => handleTest(record.id)}>测试</Button>
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>IMA 知识库配置</Title>
          <Paragraph type="secondary">连接你的 IMA 知识库，配置用途和检索权重</Paragraph>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          添加知识库
        </Button>
      </div>

      <Card>
        <Table
          dataSource={configs}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={false}
        />
      </Card>

      <Modal
        title="添加 IMA 知识库"
        open={modalOpen}
        onOk={handleCreate}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
        okText="添加"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="kbId" label="知识库 ID" rules={[{ required: true }]}>
            <Input placeholder="输入 IMA 知识库 ID" />
          </Form.Item>
          <Form.Item name="kbName" label="知识库名称" rules={[{ required: true }]}>
            <Input placeholder="如：超图产品智答库" />
          </Form.Item>
          <Form.Item name="kbType" label="类型" initialValue="subscribed">
            <Select
              options={[
                { label: '订阅的知识库', value: 'subscribed' },
                { label: '自建的知识库', value: 'owned' },
              ]}
            />
          </Form.Item>
          <Form.Item name="purpose" label="用途" rules={[{ required: true }]} initialValue="general">
            <Select
              options={[
                { label: '产品文档', value: 'product_doc' },
                { label: '案例库', value: 'case_lib' },
                { label: '行业标准', value: 'industry_standard' },
                { label: '竞品信息', value: 'competitor' },
                { label: '通用', value: 'general' },
              ]}
            />
          </Form.Item>
          <Form.Item name="searchWeight" label="检索权重" initialValue={0.5}>
            <Slider min={0} max={1} step={0.1} marks={{ 0: '0', 0.5: '0.5', 1: '1' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
