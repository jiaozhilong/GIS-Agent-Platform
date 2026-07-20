import { useEffect, useMemo, useState } from 'react';
import { billingApi, usageApi, orgApi } from '../api/client';
import { useToast } from '../components/ui/Toast';
import { IconUsage } from '../components/ui/icons';
import { useAuthStore } from '../stores/authStore';

interface Quota { id: number; organizationId: number; tokenLimit: number; warnThreshold: number; alertedMonth: string | null }
interface Invoice { id: number; organizationId: number; periodMonth: string; runCount: number; inputTokens: number; outputTokens: number; totalTokens: number; estimatedCost: number; status: string }
interface Org { id: number; name: string; slug: string | null }

const fmt = (n: number | undefined) => (n ?? 0).toLocaleString('zh-CN');
const fmtCost = (n: number | undefined) => `¥${(n ?? 0).toFixed(2)}`;

// 本月 UTC 窗口（用于实时取组织当月用量）
function monthWindow() {
  const now = new Date();
  const y = now.getUTCFullYear();
  const m = now.getUTCMonth() + 1;
  const from = `${y}-${String(m).padStart(2, '0')}-01`;
  const to = `${y}-${String(m).padStart(2, '0')}-${String(now.getUTCDate()).padStart(2, '0')}`;
  return { from, to };
}

export default function BillingPage() {
  const { showToast } = useToast();
  const role = useAuthStore((s) => s.role);
  const isSuper = role === 'SUPER_ADMIN';

  const [orgs, setOrgs] = useState<Org[]>([]);
  const [selectedOrg, setSelectedOrg] = useState<number | null>(null);

  const [quota, setQuota] = useState<Quota | null>(null);
  const [monthUsage, setMonthUsage] = useState<number>(0);
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [loading, setLoading] = useState(true);

  // 超管设置配额表单
  const [editing, setEditing] = useState(false);
  const [formLimit, setFormLimit] = useState<number>(0);
  const [formThreshold, setFormThreshold] = useState<number>(80);
  const [saving, setSaving] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [genResult, setGenResult] = useState<any>(null);

  // 超管加载组织列表用于下拉选择
  useEffect(() => {
    if (!isSuper) return;
    orgApi.list().then(({ data }) => {
      const list: Org[] = data.organizations || [];
      setOrgs(list);
      if (list.length && selectedOrg == null) setSelectedOrg(list[0].id);
    }).catch(() => {});
  }, [isSuper]); // eslint-disable-line

  const load = async () => {
    setLoading(true);
    try {
      const orgId = isSuper ? selectedOrg ?? undefined : undefined;
      const { data: q } = await billingApi.getQuota(orgId);
      setQuota(q || null);
      if (q) {
        setFormLimit(q.tokenLimit);
        setFormThreshold(q.warnThreshold);
      }
      // 当前组织当月用量（用于配额进度条）
      const { from, to } = monthWindow();
      const { data: usage } = await usageApi.summary({ ...(orgId ? { orgId } : {}), from, to });
      setMonthUsage(usage?.totals?.totalTokens ?? 0);

      const { data: inv } = await billingApi.getInvoices(orgId);
      setInvoices(Array.isArray(inv) ? inv : []);
    } catch (err: any) {
      showToast(err.response?.data?.message || '计费数据加载失败', true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); /* eslint-disable-next-line */ }, [selectedOrg, isSuper]);

  const usagePct = useMemo(() => {
    if (!quota || !quota.tokenLimit) return 0;
    return Math.min(100, Math.round((monthUsage * 100) / quota.tokenLimit));
  }, [quota, monthUsage]);

  const overWarn = quota != null && usagePct >= quota.warnThreshold;

  const saveQuota = async () => {
    if (!selectedOrg) return;
    setSaving(true);
    try {
      await billingApi.setQuota({ organizationId: selectedOrg, tokenLimit: formLimit, warnThreshold: formThreshold });
      showToast('配额已保存');
      setEditing(false);
      await load();
    } catch (err: any) {
      showToast(err.response?.data?.message || '保存失败', true);
    } finally {
      setSaving(false);
    }
  };

  const generate = async () => {
    setGenerating(true);
    setGenResult(null);
    try {
      const { data } = await billingApi.generate();
      setGenResult(data);
      showToast(`已生成 ${data.month} 账期账单（${data.orgCount} 个组织）`);
      await load();
    } catch (err: any) {
      showToast(err.response?.data?.message || '生成失败', true);
    } finally {
      setGenerating(false);
    }
  };

  return (
    <div className="usage-grid">
      <div className="page-head">
        <div>
          <h1>计费与账单</h1>
          <p>组织月度 Token 配额管控与账期结算（P8-1 计费纵深）</p>
        </div>
        {isSuper && orgs.length > 0 && (
          <div className="filter-bar">
            <label className="date-field">
              <span>组织</span>
              <select value={selectedOrg ?? ''} onChange={(e) => setSelectedOrg(Number(e.target.value))}>
                {orgs.map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
              </select>
            </label>
          </div>
        )}
      </div>

      {loading ? (
        <div className="empty-state" style={{ padding: 60 }}><p>加载中…</p></div>
      ) : (
        <>
          {/* 配额面板 */}
          <div className="panel quota-panel">
            <div className="panel-head">
              <h2>组织配额</h2>
              {isSuper && !editing && (
                <button className="btn-primary btn-sm" onClick={() => { setFormLimit(quota?.tokenLimit ?? 0); setFormThreshold(quota?.warnThreshold ?? 80); setEditing(true); }}>
                  {quota ? '修改配额' : '设置配额'}
                </button>
              )}
            </div>

            {!quota ? (
              <div className="empty-state compact">
                <p>该组织尚未配置月度配额</p>
                {isSuper && <button className="btn-primary btn-sm" onClick={() => setEditing(true)}>立即设置</button>}
              </div>
            ) : editing ? (
              <div className="quota-form">
                <label className="date-field">
                  <span>月度 Token 上限</span>
                  <input type="number" min={1} value={formLimit} onChange={(e) => setFormLimit(Number(e.target.value))} />
                </label>
                <label className="date-field">
                  <span>告警阈值 (%)</span>
                  <input type="number" min={1} max={100} value={formThreshold} onChange={(e) => setFormThreshold(Number(e.target.value))} />
                </label>
                <div className="quota-form-actions">
                  <button className="btn-primary btn-sm" onClick={saveQuota} disabled={saving}>{saving ? '保存中…' : '保存'}</button>
                  <button className="btn-ghost btn-sm" onClick={() => setEditing(false)}>取消</button>
                </div>
              </div>
            ) : (
              <>
                <div className="quota-meta">
                  <span>月度上限 <b>{fmt(quota.tokenLimit)}</b> tokens</span>
                  <span>告警阈值 <b>{quota.warnThreshold}%</b></span>
                  <span>本月已用 <b>{fmt(monthUsage)}</b></span>
                </div>
                <div className={`quota-bar ${overWarn ? 'warn' : ''}`}>
                  <div className="quota-bar-fill" style={{ width: `${usagePct}%` }} />
                  <span className="quota-bar-mark" style={{ left: `${quota.warnThreshold}%` }} title={`告警阈值 ${quota.warnThreshold}%`} />
                </div>
                <div className="quota-bar-legend">
                  <span>0</span>
                  <span className={overWarn ? 'over' : ''}>{usagePct}% 已用{overWarn ? '（已超阈值）' : ''}</span>
                  <span>100%</span>
                </div>
              </>
            )}
          </div>

          {/* 账单面板 */}
          <div className="panel">
            <div className="panel-head">
              <h2>账期账单</h2>
              {isSuper && (
                <button className="btn-primary btn-sm" onClick={generate} disabled={generating}>
                  {generating ? '生成中…' : '按月生成账单'}
                </button>
              )}
            </div>

            {genResult && (
              <div className="gen-result">
                已生成账期 <b>{genResult.month}</b>：覆盖 {genResult.orgCount} 个组织，合计费用 {fmtCost(genResult.totalCost)} 元
              </div>
            )}

            {invoices.length === 0 ? (
              <div className="empty-state compact">
                <IconUsage width={36} height={36} />
                <p>{isSuper ? '暂无账单，点击右上角生成当前账期' : '本组织暂无账单'}</p>
              </div>
            ) : (
              <div className={`dim-table ${isSuper ? 'billing-table' : 'billing-table-single'}`}>
                <div className="dim-row dim-head">
                  {isSuper && <span>组织</span>}
                  <span>账期</span>
                  <span>运行次数</span>
                  <span>总 Tokens</span>
                  <span>预估费用</span>
                  <span>状态</span>
                </div>
                {invoices.map((inv) => (
                  <div key={inv.id} className="dim-row">
                    {isSuper && <span>#{inv.organizationId}</span>}
                    <span>{inv.periodMonth}</span>
                    <span>{fmt(inv.runCount)}</span>
                    <span>{fmt(inv.totalTokens)}</span>
                    <span>{fmtCost(inv.estimatedCost)}</span>
                    <span>
                      <span className={`status-pill ${inv.status === 'SETTLED' ? 'ok' : 'draft'}`}>
                        {inv.status === 'SETTLED' ? '已结算' : '待结算'}
                      </span>
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
