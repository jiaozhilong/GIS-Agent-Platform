import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider, theme, App as AntApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import LoginPage from './pages/LoginPage';
import MainLayout from './components/MainLayout';
import ProjectCreatePage from './pages/ProjectCreatePage';
import PipelineRunPage from './pages/PipelineRunPage';
import LlmConfigPage from './pages/LlmConfigPage';
import ImaConfigPage from './pages/ImaConfigPage';
import { useAuthStore } from './stores/authStore';

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((s) => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function App() {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: '#1565C0',
          borderRadius: 6,
        },
      }}
    >
      <AntApp>
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
              <Route index element={<Navigate to="/projects/new" replace />} />
              <Route path="projects/new" element={<ProjectCreatePage />} />
              <Route path="projects/:id/run" element={<PipelineRunPage />} />
              <Route path="settings/llm" element={<LlmConfigPage />} />
              <Route path="settings/ima" element={<ImaConfigPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  );
}

export default App;
