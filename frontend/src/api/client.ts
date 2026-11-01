import axios from 'axios';

/**
 * Resolves the backend API base URL.
 * - When VITE_API_BASE_URL is provided (e.g. in cloud deployment on Vercel pointing to Render),
 *   normalizes it to ensure proper '/api' prefix without trailing slashes or duplication.
 * - In local development or when unset, defaults to '/api' which is proxied by Vite to localhost:8080.
 */
export const resolveApiBaseUrl = (envUrl?: string): string => {
  const raw = envUrl !== undefined ? envUrl : import.meta.env.VITE_API_BASE_URL;
  const trimmed = typeof raw === 'string' ? raw.trim() : '';
  if (!trimmed) {
    return '/api';
  }
  const cleaned = trimmed.replace(/\/+$/, '');
  if (cleaned.endsWith('/api')) {
    return cleaned;
  }
  if (cleaned === '/api') {
    return '/api';
  }
  return `${cleaned}/api`;
};

const api = axios.create({
  baseURL: resolveApiBaseUrl(),
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('sentinel_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('sentinel_token');
      localStorage.removeItem('sentinel_user');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default api;
