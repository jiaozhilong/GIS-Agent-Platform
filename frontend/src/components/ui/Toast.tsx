import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';

interface ToastItem {
  id: number;
  message: string;
  danger?: boolean;
}

interface ToastContextValue {
  showToast: (message: string, danger?: boolean) => void;
}

const ToastContext = createContext<ToastContextValue>({ showToast: () => {} });

export function useToast() {
  return useContext(ToastContext);
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const showToast = useCallback((message: string, danger = false) => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, message, danger }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3000);
  }, []);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      {toasts.map((t) => (
        <div key={t.id} className="toast show">
          <i className={t.danger ? 'danger' : ''}>{t.danger ? '!' : '✓'}</i>
          <span>{t.message}</span>
        </div>
      ))}
    </ToastContext.Provider>
  );
}
