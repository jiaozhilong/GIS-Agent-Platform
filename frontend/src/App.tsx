import type { ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ToastProvider } from './components/ui/Toast';
import LoginPage from './pages/LoginPage';
import MainLayout from './components/MainLayout';
import DashboardPage from './pages/DashboardPage';
import ProjectsPage from './pages/ProjectsPage';
import ProjectCreatePage from './pages/ProjectCreatePage';
import ProjectDetailPage from './pages/ProjectDetailPage';
import PipelineRunPage from './pages/PipelineRunPage';
import LlmConfigPage from './pages/LlmConfigPage';
import ImaConfigPage from './pages/ImaConfigPage';
import SkillsPage from './pages/SkillsPage';
import PipelinePage from './pages/PipelinePage';
import TemplatesPage from './pages/TemplatesPage';
import TeamsPage from './pages/TeamsPage';
import StatsPage from './pages/StatsPage';
import UsagePage from './pages/UsagePage';
import BillingPage from './pages/BillingPage';
import OrchestratePage from './pages/OrchestratePage';
import UsersAdminPage from './pages/UsersAdminPage';
import UserProfilePage from './pages/UserProfilePage';
import AuditPage from './pages/AuditPage';
import PptTemplatePage from './pages/PptTemplatePage';
import { useAuthStore } from './stores/authStore';

function PrivateRoute({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <PrivateRoute>
                <MainLayout />
              </PrivateRoute>
            }
          >
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="projects" element={<ProjectsPage />} />
            <Route path="projects/new" element={<ProjectCreatePage />} />
            <Route path="projects/:id" element={<ProjectDetailPage />} />
            <Route path="projects/:id/run" element={<PipelineRunPage />} />
            <Route path="settings/llm" element={<LlmConfigPage />} />
            <Route path="settings/ima" element={<ImaConfigPage />} />
            <Route path="settings/ppt-templates" element={<PptTemplatePage />} />
            <Route path="settings/skills" element={<SkillsPage />} />
            <Route path="pipeline" element={<PipelinePage />} />
            <Route path="templates" element={<TemplatesPage />} />
            <Route path="teams" element={<TeamsPage />} />
            <Route path="stats" element={<StatsPage />} />
            {/* P7-3 用量计费看板 */}
            <Route path="usage" element={<UsagePage />} />
            {/* P8-1 计费纵深：配额与账单 */}
            <Route path="billing" element={<BillingPage />} />
            <Route path="orchestrate" element={<OrchestratePage />} />
            <Route path="profile" element={<UserProfilePage />} />
            <Route path="admin/users" element={<UsersAdminPage />} />
            <Route path="admin/audit" element={<AuditPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </ToastProvider>
  );
}

export default App;
