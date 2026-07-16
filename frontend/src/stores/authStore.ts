import { create } from 'zustand';

interface AuthState {
  token: string | null;
  username: string | null;
  userId: number | null;
  setAuth: (token: string, username: string, userId: number) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem('token'),
  username: localStorage.getItem('username'),
  userId: localStorage.getItem('userId') ? Number(localStorage.getItem('userId')) : null,

  setAuth: (token, username, userId) => {
    localStorage.setItem('token', token);
    localStorage.setItem('username', username);
    localStorage.setItem('userId', String(userId));
    set({ token, username, userId });
  },

  logout: () => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('userId');
    set({ token: null, username: null, userId: null });
  },
}));
