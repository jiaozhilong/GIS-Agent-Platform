import axios from 'axios';

const apiClient = axios.create({
  // 默认相对路径：开发/预览由 vite proxy 同源转发到后端，避免 CORS。
  // 生产构建如需直连后端，设置环境变量 VITE_API_BASE=https://your-host/api
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

// 请求拦截器：自动注入 JWT Token
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器：401 自动跳转登录
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default apiClient;

// ===== Auth API =====
export const authApi = {
  login: (username: string, password: string) =>
    apiClient.post('/auth/login', { username, password }),
  register: (username: string, password: string) =>
    apiClient.post('/auth/register', { username, password }),
};

// ===== 用户管理（P6-2 平台管理员）=====
export const adminApi = {
  listUsers: () => apiClient.get('/admin/users'),
  createUser: (data: { username: string; password: string; displayName?: string; email?: string; role?: string }) =>
    apiClient.post('/admin/users', data),
  changeRole: (id: number, role: string) => apiClient.post(`/admin/users/${id}/role`, { role }),
  toggleEnabled: (id: number) => apiClient.post(`/admin/users/${id}/toggle-enabled`),
};

// ===== 当前用户（P6-3）=====
export const userApi = {
  me: () => apiClient.get('/users/me'),
  updateProfile: (data: { displayName?: string; email?: string }) => apiClient.post('/users/me', data),
  changePassword: (oldPassword: string, newPassword: string) =>
    apiClient.post('/users/me/password', { oldPassword, newPassword }),
};

// ===== 审计日志（P5-4）=====
export const auditApi = {
  list: (limit = 50) => apiClient.get(`/audit?limit=${limit}`),
};

// ===== 通知中心（P5-5）=====
export const notificationApi = {
  summary: () => apiClient.get('/notifications'),
  readAll: () => apiClient.post('/notifications/read-all'),
};

// ===== LLM Provider API =====
export const llmApi = {
  list: () => apiClient.get('/providers'),
  create: (data: any) => apiClient.post('/providers', data),
  delete: (id: number) => apiClient.delete(`/providers/${id}`),
  test: (id: number) => apiClient.post(`/providers/${id}/test`),
};

// ===== IMA Config API =====
export const imaApi = {
  listConfigs: () => apiClient.get('/ima/configs'),
  createConfig: (data: any) => apiClient.post('/ima/configs', data),
  updateConfig: (id: number, data: any) => apiClient.put(`/ima/configs/${id}`, data),
  deleteConfig: (id: number) => apiClient.delete(`/ima/configs/${id}`),
  testConfig: (id: number) => apiClient.post(`/ima/configs/${id}/test`),
  // 我的 IMA 凭证（按用户隔离，加密存储）
  getCredential: () => apiClient.get('/ima/credentials'),
  saveCredential: (data: any) => apiClient.put('/ima/credentials', data),
  deleteCredential: () => apiClient.delete('/ima/credentials'),
  testCredential: () => apiClient.post('/ima/credentials/test'),
  // 手动触发知识库同步（P3-1）
  kbSync: () => apiClient.post('/ima/kb-sync'),
  // 仅 mock 模式：模拟一次知识库更新（联调验证用）
  kbSimulate: () => apiClient.post('/ima/kb-simulate'),
  // 从 IMA 远端拉取订阅/自建知识库列表
  listRemoteKbs: () => apiClient.get('/ima/kb-list'),
};

// ===== Skills (可编排能力) API =====
export const skillApi = {
  list: () => apiClient.get('/skills'),
  get: (id: number) => apiClient.get(`/skills/${id}`),
  create: (data: any) => apiClient.post('/skills', data),
  update: (id: number, data: any) => apiClient.put(`/skills/${id}`, data),
  remove: (id: number) => apiClient.delete(`/skills/${id}`),
  test: (id: number) => apiClient.post(`/skills/${id}/test`),
};

// ===== Template (流程模板) API =====
export const templateApi = {
  // 模板列表，可选 category 过滤（official/community/mine）
  list: (category?: string) =>
    apiClient.get('/templates', { params: category ? { category } : {} }),
  // 模板市场（带点赞/收藏/作者态）：scope=all|official|community|mine
  market: (scope: string = 'all', keyword?: string) =>
    apiClient.get('/templates/market', { params: { scope, ...(keyword ? { keyword } : {}) } }),
  // 模板详情
  getByKey: (key: string) => apiClient.get(`/templates/${key}`),
  // 保存自定义模板（名称 + 工具链 + 可选 category=mine|community）
  create: (data: { name: string; description?: string; toolChain: string[]; estimatedTime?: string; category?: string }) =>
    apiClient.post('/templates', data),
  // 删除自定义模板（仅 mine）
  remove: (key: string) => apiClient.delete(`/templates/${key}`),
  // 点赞 / 取消点赞（toggle）
  like: (id: number) => apiClient.post(`/templates/${id}/like`),
  // 收藏 / 取消收藏（toggle）
  favorite: (id: number) => apiClient.post(`/templates/${id}/favorite`),
  // 发布到社区 / 撤回为私有
  publish: (id: number, community: boolean) => apiClient.post(`/templates/${id}/publish?community=${community}`),
};

// ===== Team (团队空间 & RBAC) API（P4-2）=====
export const teamApi = {
  // 我所在的团队（含我的角色）
  listMine: () => apiClient.get('/teams'),
  // 创建团队（创建者自动为 OWNER）
  create: (name: string) => apiClient.post('/teams', { name }),
  // 团队详情 + 成员列表（需为成员）
  detail: (id: number) => apiClient.get(`/teams/${id}`),
  // 邀请成员：role = OWNER|ADMIN|EDITOR|MEMBER|VIEWER
  addMember: (id: number, username: string, role: string) =>
    apiClient.post(`/teams/${id}/members`, { username, role }),
  // 修改成员角色
  updateRole: (id: number, userId: number, role: string) =>
    apiClient.put(`/teams/${id}/members/${userId}`, { role }),
  // 移除成员
  removeMember: (id: number, userId: number) =>
    apiClient.delete(`/teams/${id}/members/${userId}`),
};

// ===== 使用数据看板 API（P4-4）=====
export const statsApi = {
  // 概览 + 趋势 + 工具 + 模板分布；teamId 可选（团队视角，需为成员）
  overview: (teamId?: number) =>
    apiClient.get('/stats/overview', { params: teamId ? { teamId } : {} }),
};

// ===== 用量计费 API（P7-3）=====
export const usageApi = {
  // 用量聚合：self 默认本人范围；all=true 限超管查看全平台；orgId/projectId 限定范围
  // from / to 为 YYYY-MM-DD（UTC 窗口过滤），可选
  summary: (params: { all?: boolean; orgId?: number; projectId?: number; from?: string; to?: string }) =>
    apiClient.get('/usage/summary', { params: { ...params } }),
};

// ===== 计费纵深 API（P8-1）=====
export const billingApi = {
  // 查看配额：超管可传 orgId 查看任意组织；普通用户看本组织
  getQuota: (orgId?: number) =>
    apiClient.get('/billing/quota', { params: orgId ? { orgId } : {} }),
  // 设置配额（仅 SUPER_ADMIN）
  setQuota: (data: { organizationId: number; tokenLimit: number; warnThreshold?: number }) =>
    apiClient.put('/billing/quota', data),
  // 查看账单：超管传 orgId 看指定组织，不传看全部
  getInvoices: (orgId?: number) =>
    apiClient.get('/billing/invoices', { params: orgId ? { orgId } : {} }),
  // 按月生成账单（仅 SUPER_ADMIN），month 默认当月 yyyy-MM
  generate: (month?: string) =>
    apiClient.post('/billing/invoices/generate', {}, { params: month ? { month } : {} }),
};

// ===== 组织（租户）管理 API（P7-1，仅 SUPER_ADMIN）=====
export const orgApi = {
  list: () => apiClient.get('/admin/organizations'),
};

// ===== Agent 自编排 API（P4-5）=====
export const orchestrateApi = {
  // 根据自然语言需求推荐有序工具链；返回 { reason, toolChain, model, usedFallback }
  recommend: (requirement: string) => apiClient.post('/orchestrate', { requirement }),
};

// ===== Project & Pipeline API =====
export const projectApi = {
  // 项目列表
  list: () => apiClient.get('/projects'),

  // 项目详情
  getById: (id: number) => apiClient.get(`/projects/${id}`),

  // 创建项目 + 上传需求文档
  create: (formData: FormData) =>
    apiClient.post('/projects', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),

  // 启动流水线（支持运行时选择模型和知识库）
  run: (id: number, payload?: { providerId?: number; kbConfigIds?: number[] }) =>
    apiClient.post(`/projects/${id}/run`, payload || {}),

  // 查询流水线状态
  status: (id: number) => apiClient.get(`/projects/${id}/status`),

  // 重跑下游：从指定节点（toolOrder）之后重新执行
  rerun: (id: number, runId: number, fromOrder: number) =>
    apiClient.post(`/projects/${id}/runs/${runId}/rerun?fromOrder=${fromOrder}`),

  // 用最新知识库重生成（清除"知识库有更新"标记并重新跑流水线）
  rerunKb: (id: number) => apiClient.post(`/projects/${id}/rerun-kb`),

  // ===== 方案版本管理（P4-3）=====
  // 手动保存当前方案为新版本（读取最新运行/项目的 contextJson）
  saveVersion: (id: number, payload: { title?: string; note?: string }) =>
    apiClient.post(`/projects/${id}/versions`, payload),

  // 历史版本列表（轻量，含方案预览）
  listVersions: (id: number) => apiClient.get(`/projects/${id}/versions`),

  // 版本详情（含完整 contextJson）
  getVersion: (id: number, vid: number) => apiClient.get(`/projects/${id}/versions/${vid}`),

  // 一键回退到指定版本
  restoreVersion: (id: number, vid: number) =>
    apiClient.post(`/projects/${id}/versions/${vid}/restore`),

  // 下载 Markdown
  downloadMd: (id: number) =>
    apiClient.get(`/projects/${id}/download/md`, { responseType: 'blob' }),

  // 下载 Word
  downloadDocx: (id: number) =>
    apiClient.get(`/projects/${id}/download/docx`, { responseType: 'blob' }),

  // 下载 PPT
  downloadPptx: (id: number) =>
    apiClient.get(`/projects/${id}/download/pptx`, { responseType: 'blob' }),

  // 上传 PPT 品牌模板（全局导出样式，保存为 ./data/templates/brand-template.pptx）
  uploadPptTemplate: (file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return apiClient.post(`/projects/ppt-template`, fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};

// ===== Tool Execution API（中间产物编辑）=====
export const toolApi = {
  // 编辑单个中间产物（execId 对应某次运行的工具执行记录）
  updateOutput: (projectId: number, execId: number, output: string) =>
    apiClient.put(`/projects/${projectId}/tools/${execId}`, { output }),
};

// ===== Search API（P5-1 向量混合检索）=====
export const searchApi = {
  search: (projectId: number, query: string, topK = 5) =>
    apiClient.post('/search', { projectId, query, topK }),
};

// 触发浏览器下载 blob
export function downloadBlob(blob: Blob, fileName: string) {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(url);
}
