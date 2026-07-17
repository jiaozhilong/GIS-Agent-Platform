// 项目状态 / 模板 等展示层映射工具

export type EffStatus = 'running' | 'done' | 'failed' | 'draft' | 'pending';

export const STATUS_LABEL: Record<EffStatus, string> = {
  running: '进行中',
  done: '已完成',
  failed: '失败',
  draft: '草稿',
  pending: '排队中',
};

export function effectiveStatus(status?: string, runStatus?: string): EffStatus {
  const s = (runStatus || status || 'DRAFT').toUpperCase();
  if (s === 'RUNNING') return 'running';
  if (s === 'PENDING') return 'pending';
  if (s === 'SUCCESS' || s === 'PARTIAL') return 'done';
  if (s === 'FAILED') return 'failed';
  return 'draft';
}

export function progressOf(status: EffStatus): number {
  switch (status) {
    case 'done': return 100;
    case 'running': return 65;
    case 'pending': return 20;
    case 'failed': return 100;
    default: return 8;
  }
}

export function templateLabel(templateId?: string): string {
  switch (templateId) {
    case 'quick_selection': return '快速选型';
    case 'full_solution': return '全套方案';
    default: return templateId || '自定义';
  }
}

export function fmtDate(s?: string): string {
  if (!s) return '—';
  return s.slice(0, 10);
}
