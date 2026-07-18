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
  // 手动触发知识库同步（P3-1）
  kbSync: () => apiClient.post('/ima/kb-sync'),
  // 仅 mock 模式：模拟一次知识库更新（联调验证用）
  kbSimulate: () => apiClient.post('/ima/kb-simulate'),
};

// ===== Skills (可编排能力) API =====
export const skillApi = {
  list: () => apiClient.get('/skills'),
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

  // 启动流水线
  run: (id: number) => apiClient.post(`/projects/${id}/run`),

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
};

// ===== Tool Execution API（中间产物编辑）=====
export const toolApi = {
  // 编辑单个中间产物（execId 对应某次运行的工具执行记录）
  updateOutput: (projectId: number, execId: number, output: string) =>
    apiClient.put(`/projects/${projectId}/tools/${execId}`, { output }),
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
