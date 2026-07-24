import { NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../stores/authStore';
import {
  IconDashboard, IconProject, IconBrain, IconBook, IconFlow, IconTemplate, IconBrand, IconUser, IconTeam, IconStats, IconWand, IconShield, IconBell, IconUsage,
} from './ui/icons';

const navItems = [
  { to: '/dashboard', label: '工作台', icon: IconDashboard, section: null },
  { to: '/projects', label: '全部项目', icon: IconProject, section: null },
  { to: '/profile', label: '个人资料', icon: IconUser, section: '个人' },
  { to: '/teams', label: '团队空间', icon: IconTeam, section: '协作' },
  { to: '/stats', label: '数据看板', icon: IconStats, section: '协作' },
  { to: '/usage', label: '用量计费', icon: IconUsage, section: '协作' },
  { to: '/billing', label: '计费账单', icon: IconUsage, section: '协作' },
  { to: '/settings/llm', label: '大模型配置', icon: IconBrain, section: 'AI 配置' },
  { to: '/settings/ima', label: 'IMA 知识库', icon: IconBook, section: 'AI 配置' },
  { to: '/settings/ppt-templates', label: 'PPT 模板', icon: IconTemplate, section: 'AI 配置' },
  { to: '/settings/skills', label: '技能中心', icon: IconWand, section: 'AI 配置' },
  { to: '/pipeline', label: '流程编排', icon: IconFlow, section: '方案工作流' },
  { to: '/templates', label: '模板市场', icon: IconTemplate, section: '方案工作流' },
  { to: '/orchestrate', label: '智能编排', icon: IconWand, section: '方案工作流' },
  { to: '/admin/users', label: '用户与权限', icon: IconShield, section: '系统管理', adminOnly: true },
  { to: '/admin/audit', label: '审计日志', icon: IconBell, section: '系统管理', adminOnly: true },
];

export function Sidebar() {
  const navigate = useNavigate();
  const { username, role, logout } = useAuthStore();
  const isAdmin = role === 'SUPER_ADMIN' || role === 'ADMIN';

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  let lastSection: string | null = null;

  return (
    <aside className="sidebar">
      <div className="side-brand">
        <span className="side-brand-mark"><IconBrand /></span>
        <span className="side-brand-text">GeoAgent Studio</span>
      </div>

      <nav className="side-nav">
        {navItems
          .filter((item) => !item.adminOnly || isAdmin)
          .map((item) => {
            const showSection = item.section && item.section !== lastSection;
            lastSection = item.section;
            const Icon = item.icon;
            return (
              <div key={item.to}>
                {showSection && <div className="nav-section">{item.section}</div>}
                <NavLink
                  to={item.to}
                  className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
                >
                  <Icon />
                  {item.label}
                </NavLink>
              </div>
            );
          })}
      </nav>

      <div className="side-footer">
        <div className="user-card" onClick={handleLogout} style={{ cursor: 'pointer' }}>
          <div className="user-avatar">{(username || 'U').charAt(0).toUpperCase()}</div>
          <div className="user-info">
            <div className="user-name">{username || '用户'}</div>
            <div className="user-role">{(role === 'SUPER_ADMIN' ? '超级管理员' : role === 'ADMIN' ? '管理员' : '解决方案工程师')} · 退出</div>
          </div>
          <IconUser width={16} height={16} style={{ color: 'var(--muted-2)' }} />
        </div>
      </div>
    </aside>
  );
}
