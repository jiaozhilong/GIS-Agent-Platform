import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import '../styles/layout.css';

export default function MainLayout() {
  return (
    <div className="app-shell">
      <Sidebar />
      <main className="main-area">
        <Outlet />
      </main>
    </div>
  );
}
