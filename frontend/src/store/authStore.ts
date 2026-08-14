import { create } from 'zustand';
import type { AuthResponse } from '../types';

interface AuthState {
  token: string | null;
  user: {
    username: string;
    email: string;
    role: 'PATIENT' | 'PHARMACIST' | 'ADMIN';
  } | null;
  isAuthenticated: boolean;
  login: (data: AuthResponse) => void;
  logout: () => void;
}

const initialToken = localStorage.getItem('sentinel_token');
const initialUserStr = localStorage.getItem('sentinel_user');
let initialUser = null;

if (initialUserStr) {
  try {
    initialUser = JSON.parse(initialUserStr);
  } catch (e) {
    console.error('Failed to parse cached user');
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  token: initialToken,
  user: initialUser,
  isAuthenticated: !!initialToken,
  login: (data: AuthResponse) => {
    const userInfo = {
      username: data.username,
      email: data.email,
      role: data.role,
    };
    localStorage.setItem('sentinel_token', data.token);
    localStorage.setItem('sentinel_user', JSON.stringify(userInfo));
    set({
      token: data.token,
      user: userInfo,
      isAuthenticated: true,
    });
  },
  logout: () => {
    localStorage.removeItem('sentinel_token');
    localStorage.removeItem('sentinel_user');
    set({
      token: null,
      user: null,
      isAuthenticated: false,
    });
  },
}));
