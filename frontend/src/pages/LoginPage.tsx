import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import api from '../api/client';
import type { AuthResponse } from '../types';
import { Activity, ShieldCheck, Lock, User, AlertCircle, Loader2 } from 'lucide-react';

export const LoginPage: React.FC = () => {
  const [username, setUsername] = useState('patient1');
  const [password, setPassword] = useState('password123');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const login = useAuthStore((state: any) => state.login);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await api.post<{ data: AuthResponse }>('/auth/login', { username, password });
      const authData = res.data.data;
      login(authData);

      if (authData.role === 'ADMIN') {
        navigate('/admin-dashboard');
      } else if (authData.role === 'PHARMACIST') {
        navigate('/inventory');
      } else {
        navigate('/patient-dashboard');
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Invalid credentials or connection error');
    } finally {
      setLoading(false);
    }
  };

  const setDemoUser = (userRole: 'patient' | 'pharmacist' | 'admin') => {
    if (userRole === 'patient') {
      setUsername('patient1');
      setPassword('password123');
    } else if (userRole === 'pharmacist') {
      setUsername('pharmacist1');
      setPassword('password123');
    } else {
      setUsername('admin1');
      setPassword('password123');
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 flex items-center justify-center p-6 relative overflow-hidden">
      {/* Dynamic Background Effects */}
      <div className="absolute -top-40 -left-40 w-96 h-96 bg-blue-600/20 rounded-full blur-3xl"></div>
      <div className="absolute -bottom-40 -right-40 w-96 h-96 bg-emerald-600/20 rounded-full blur-3xl"></div>

      <div className="w-full max-w-md bg-slate-800/90 border border-slate-700/80 backdrop-blur-xl rounded-3xl p-8 shadow-2xl z-10">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-blue-600 text-white rounded-2xl mb-4 shadow-lg shadow-blue-500/30">
            <Activity className="w-8 h-8" />
          </div>
          <h1 className="text-2xl font-bold text-white tracking-tight">SentinelGrid Portal</h1>
          <p className="text-slate-400 text-sm mt-1">Emergency Medicine & Availability Network</p>
        </div>

        {error && (
          <div className="mb-6 p-4 bg-red-500/10 border border-red-500/30 rounded-xl flex items-center gap-3 text-red-400 text-sm">
            <AlertCircle className="w-5 h-5 shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
              Username
            </label>
            <div className="relative">
              <User className="w-5 h-5 text-slate-400 absolute left-3.5 top-3.5" />
              <input
                type="text"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Enter username"
                className="w-full bg-slate-900 border border-slate-700 rounded-xl pl-11 pr-4 py-3 text-white placeholder-slate-500 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition-all"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
              Password
            </label>
            <div className="relative">
              <Lock className="w-5 h-5 text-slate-400 absolute left-3.5 top-3.5" />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Enter password"
                className="w-full bg-slate-900 border border-slate-700 rounded-xl pl-11 pr-4 py-3 text-white placeholder-slate-500 text-sm focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition-all"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 hover:bg-blue-500 text-white font-semibold py-3.5 rounded-xl shadow-lg shadow-blue-600/30 flex items-center justify-center gap-2 text-sm transition-all disabled:opacity-50"
          >
            {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : <ShieldCheck className="w-5 h-5" />}
            <span>Sign In to Healthcare Grid</span>
          </button>
        </form>

        {/* Demo Credentials Quick Switch */}
        <div className="mt-8 pt-6 border-t border-slate-700/60 text-center">
          <p className="text-xs text-slate-400 font-medium mb-3">Quick Demo Login Presets:</p>
          <div className="flex justify-center gap-2">
            <button
              onClick={() => setDemoUser('patient')}
              className="px-3 py-1.5 bg-slate-700/60 hover:bg-slate-700 text-slate-300 text-xs rounded-lg font-medium transition-colors"
            >
              Patient
            </button>
            <button
              onClick={() => setDemoUser('pharmacist')}
              className="px-3 py-1.5 bg-slate-700/60 hover:bg-slate-700 text-slate-300 text-xs rounded-lg font-medium transition-colors"
            >
              Pharmacist
            </button>
            <button
              onClick={() => setDemoUser('admin')}
              className="px-3 py-1.5 bg-slate-700/60 hover:bg-slate-700 text-slate-300 text-xs rounded-lg font-medium transition-colors"
            >
              Admin
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
