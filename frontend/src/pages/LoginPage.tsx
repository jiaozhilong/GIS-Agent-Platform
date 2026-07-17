import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { useToast } from '../components/ui/Toast';
import { IconBrand, IconUser, IconLock } from '../components/ui/icons';

export default function LoginPage() {
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState('');
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const { showToast } = useToast();

  const toggleMode = () => {
    setIsRegister((v) => !v);
    setErrors({});
    setFormError('');
    setUsername('');
    setPassword('');
    setConfirm('');
    setDisplayName('');
  };

  const clearFieldError = (field: string) =>
    setErrors((prev) => {
      if (!prev[field]) return prev;
      const next = { ...prev };
      delete next[field];
      return next;
    });

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError('');
    const nextErrors: Record<string, string> = {};

    if (!username.trim()) nextErrors.username = '请输入用户名';
    if (!password || password.length < 6) nextErrors.password = '密码长度不能少于 6 位';
    if (isRegister) {
      if (password !== confirm) nextErrors.confirm = '两次输入的密码不一致';
      if (!displayName.trim()) nextErrors.displayName = '请输入显示名称';
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setLoading(true);
    try {
      if (isRegister) {
        const { data } = await authApi.register(username.trim(), password);
        setAuth(data.token, data.username, data.userId);
        showToast('注册成功，欢迎加入 GeoAgent Studio');
      } else {
        const { data } = await authApi.login(username.trim(), password);
        setAuth(data.token, data.username, data.userId);
        showToast('登录成功，正在进入工作台');
      }
      navigate('/dashboard');
    } catch (err: any) {
      setFormError(err.response?.data?.error || (isRegister ? '注册失败，请重试' : '登录失败，请检查用户名和密码'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-shell">
      {/* Brand Side */}
      <div className="brand-side">
        <div className="orbit-scene">
          <div className="orbit-core">
            <div className="radar" />
            <div className="orbit-ring" />
            <svg className="land-mass" viewBox="0 0 300 300" aria-hidden="true">
              <path d="M53 76 89 52l35 13 18 31 42-10 37 22 18 42-20 30-37 5-17 29-44-3-22-30-35-10-15-42Z" />
            </svg>
          </div>
          <span className="orbit-dot dot-1" title="项目"><IconProject /></span>
          <span className="orbit-dot dot-2" title="知识库"><IconBook /></span>
          <span className="orbit-dot dot-3" title="PPT"><IconTemplate /></span>
          <span className="orbit-dot dot-4" title="流程"><IconFlow /></span>
          <span className="orbit-dot dot-5" title="AI"><IconBrain /></span>
        </div>

        <div className="brand-info">
          <div className="brand-mark-lg"><IconBrand /></div>
          <div className="brand-name">GeoAgent Studio</div>
          <div className="brand-tagline">面向 GIS 解决方案的 AI Agent 协作平台</div>
          <div className="metric-row">
            <div className="metric-item"><div className="metric-value">50+</div><div className="metric-label">行业模板</div></div>
            <div className="metric-item"><div className="metric-value">12</div><div className="metric-label">可编排 Skills</div></div>
            <div className="metric-item"><div className="metric-value">100%</div><div className="metric-label">本地运行</div></div>
          </div>
        </div>
      </div>

      {/* Form Side */}
      <div className="form-side">
        <div className="form-header">
          <h1>{isRegister ? '创建账号' : '欢迎回来'}</h1>
          <p>{isRegister ? '注册 GeoAgent Studio 工作空间' : '登录您的 GeoAgent Studio 工作空间'}</p>
        </div>

        {formError && <div className="form-error">{formError}</div>}

        <form onSubmit={onSubmit} noValidate>
          {isRegister && (
            <div className="form-group">
              <label className="form-label" htmlFor="displayName">显示名称</label>
              <div className="input-wrap">
                <IconUser className="input-icon" />
                <input
                  id="displayName"
                  className={`form-input ${errors.displayName ? 'error' : ''}`}
                  type="text"
                  placeholder="您的姓名"
                  value={displayName}
                  maxLength={50}
                  onChange={(e) => { setDisplayName(e.target.value); clearFieldError('displayName'); }}
                />
              </div>
            </div>
          )}

          <div className="form-group">
            <label className="form-label" htmlFor="username">用户名</label>
            <div className="input-wrap">
              <IconUser className="input-icon" />
              <input
                id="username"
                className={`form-input ${errors.username ? 'error' : ''}`}
                type="text"
                placeholder="输入用户名"
                value={username}
                maxLength={50}
                autoComplete="username"
                onChange={(e) => { setUsername(e.target.value); clearFieldError('username'); }}
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="password">密码</label>
            <div className="input-wrap">
              <IconLock className="input-icon" />
              <input
                id="password"
                className={`form-input ${errors.password ? 'error' : ''}`}
                type="password"
                placeholder="输入密码"
                value={password}
                minLength={6}
                autoComplete={isRegister ? 'new-password' : 'current-password'}
                onChange={(e) => { setPassword(e.target.value); clearFieldError('password'); }}
              />
            </div>
          </div>

          {isRegister && (
            <div className="form-group">
              <label className="form-label" htmlFor="confirm">确认密码</label>
              <div className="input-wrap">
                <IconLock className="input-icon" />
                <input
                  id="confirm"
                  className={`form-input ${errors.confirm ? 'error' : ''}`}
                  type="password"
                  placeholder="再次输入密码"
                  value={confirm}
                  minLength={6}
                  autoComplete="new-password"
                  onChange={(e) => { setConfirm(e.target.value); clearFieldError('confirm'); }}
                />
              </div>
            </div>
          )}

          {!isRegister && (
            <div className="form-row">
              <label className="checkbox-wrap">
                <input type="checkbox" defaultChecked /> 记住登录状态
              </label>
              <a className="forgot-link" onClick={() => showToast('密码重置功能将在后续版本支持')}>忘记密码？</a>
            </div>
          )}

          <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
            {loading && <span className="spinner" />}
            {loading ? '处理中…' : isRegister ? '注册' : '登录'}
          </button>
        </form>

        {!isRegister && (
          <>
            <div className="divider">或使用第三方账号</div>
            <div className="sso-row">
              <button type="button" className="btn-sso" onClick={() => showToast('企业微信登录将在后续版本支持')}>
                <svg viewBox="0 0 24 24" fill="currentColor" width={18} height={18}><path d="M8.5 11a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3Zm5 0a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3ZM12 2C6.48 2 2 5.92 2 10.68c0 2.6 1.3 4.96 3.4 6.54l-.86 2.58a.5.5 0 0 0 .74.56l2.88-1.72c.86.22 1.76.34 2.68.34.16 0 .32 0 .48-.02a6.3 6.3 0 0 1-.32-2c0-3.48 3.13-6.3 7-6.3.7 0 1.37.1 2 .26C20 7.5 16.5 2 12 2Z" /></svg>
                企业微信
              </button>
              <button type="button" className="btn-sso" onClick={() => showToast('GitHub 登录将在后续版本支持')}>
                <svg viewBox="0 0 24 24" fill="currentColor" width={18} height={18}><path d="M12 2C6.477 2 2 6.477 2 12c0 4.42 2.865 8.17 6.839 9.49.5.092.682-.217.682-.482 0-.237-.008-.866-.013-1.7-2.782.604-3.369-1.34-3.369-1.34-.454-1.156-1.11-1.463-1.11-1.463-.908-.62.069-.608.069-.608 1.003.07 1.531 1.03 1.531 1.03.892 1.529 2.341 1.087 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.11-4.555-4.943 0-1.091.39-1.984 1.029-2.683-.103-.253-.446-1.27.098-2.647 0 0 .84-.269 2.75 1.025A9.578 9.578 0 0 1 12 6.836a9.59 9.59 0 0 1 2.504.337c1.909-1.294 2.747-1.025 2.747-1.025.546 1.377.203 2.394.1 2.647.64.699 1.028 1.592 1.028 2.683 0 3.842-2.339 4.687-4.566 4.935.359.309.678.919.678 1.852 0 1.336-.012 2.415-.012 2.743 0 .267.18.578.688.48C19.138 20.167 22 16.418 22 12c0-5.523-4.477-10-10-10Z" /></svg>
                GitHub
              </button>
            </div>
          </>
        )}

        <div className="switch-hint">
          <span>{isRegister ? '已有账号？' : '还没有账号？'}</span>{' '}
          <a className="switch-link" onClick={toggleMode}>{isRegister ? '立即登录' : '立即注册'}</a>
        </div>
      </div>
    </div>
  );
}

// 局部图标（避免污染全局图标表）
function IconProject() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}><path d="M4 5h6l2 2h8v12H4V5Z" /></svg>;
}
function IconBook() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H11v16H6.5A2.5 2.5 0 0 0 4 21.5v-16ZM20 5.5A2.5 2.5 0 0 0 17.5 3H13v16h4.5a2.5 2.5 0 0 1 2.5 2.5v-16Z" /></svg>;
}
function IconTemplate() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}><path d="M4 4h16v12H4zM8 20h8M12 16v4" /></svg>;
}
function IconFlow() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}><circle cx="5" cy="6" r="2" /><circle cx="19" cy="6" r="2" /><circle cx="12" cy="18" r="2" /><path d="M7 6h10M6 8l5 8M18 8l-5 8" /></svg>;
}
function IconBrain() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}><rect x="4" y="7" width="16" height="12" rx="3" /><path d="M9 3h6M12 3v4M8 12h.01M16 12h.01M9 16h6" /></svg>;
}
