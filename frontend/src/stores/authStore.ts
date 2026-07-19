import { create } from 'zustand';

interface AuthState {
  token: string | null;
  username: string | null;
  userId: number | null;
  role: string | null;
  setAuth: (token: string, username: string, userId: number, role?: string) => void;
  setRole: (role: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem('token'),
  username: localStorage.getItem('username'),
  userId: localStorage.getItem('userId') ? Number(localStorage.getItem('userId')) : null,
  role: localStorage.getItem('role'),

  setAuth: (token, username, userId, role) => {
    localStorage.setItem('token', token);
    localStorage.setItem('username', username);
    localStorage.setItem('userId', String(userId));
    if (role) localStorage.setItem('role', role);
    set({ token, username, userId, role: role ?? null });
  },

  setRole: (role) => {
    localStorage.setItem('role', role);
    set({ role });
  },

  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('userId');
    localStorage.removeItem('role');
    set({ token: null, username: null, userId: null, role: null });
  },
}));
