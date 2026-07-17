import { NavLink, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../stores/authStore';
import {
  IconDashboard, IconProject, IconBrain, IconBook, IconFlow, IconTemplate, IconBrand, IconUser,
} from './ui/icons';

const navItems = [
  { to: '/dashboard', label: '工作台', icon: IconDashboard, section: null },
  { to: '/projects', label: '全部项目', icon: IconProject, badge: '5', section: null },
  { to: '/settings/llm', label: '大模型配置', icon: IconBrain, section: 'AI 配置' },
  { to: '/settings/ima', label: 'IMA 知识库', icon: IconBook, section: 'AI 配置' },
  { to: '/pipeline', label: '流程编排', icon: IconFlow, section: '方案工作流' },
  { to: '/templates', label: '模板市场', icon: IconTemplate, section: '方案工作流' },
];

export function Sidebar() {
  const navigate = useNavigate();
  const { username, logout } = useAuthStore();

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
        {navItems.map((item) => {
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
                {item.badge && <span className="nav-badge">{item.badge}</span>}
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
            <div className="user-role">解决方案工程师 · 退出</div>
          </div>
          <IconUser width={16} height={16} style={{ color: 'var(--muted-2)' }} />
        </div>
      </div>
    </aside>
  );
}
