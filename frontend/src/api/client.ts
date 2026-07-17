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
};

// ===== Template (预置流程模板) API =====
export const templateApi = {
  // 模板列表，可选 category 过滤（official/community/mine）
  list: (category?: string) =>
    apiClient.get('/templates', { params: category ? { category } : {} }),
  // 模板详情
  getByKey: (key: string) => apiClient.get(`/templates/${key}`),
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
