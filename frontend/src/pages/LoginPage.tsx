import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import api from '../api/client';
import type { AuthResponse } from '../types';
import {
  ShieldCheck,
  Lock,
  User,
  AlertCircle,
  Loader2,
  Mail,
  UserPlus,
  LogIn,
  AlertTriangle,
  Building2,
  Package,
  Users,
  Sparkles,
  CheckCircle2,
} from 'lucide-react';
import logoWhiteImg from '../assets/logo/logo-white.png';
import logoMainImg from '../assets/logo/logo-main.png';

type DemoRole = 'patient' | 'pharmacist' | 'manager' | 'admin';

const DEMO_PRESETS: Record<
  DemoRole,
  { username: string; label: string; roleName: string; icon: React.ElementType; color: string; desc: string }
> = {
  patient: {
    username: 'patient1',
    label: 'Patient',
    roleName: 'CareLink Portal',
    icon: User,
    color: 'border-blue-200 bg-blue-50/70 text-blue-700 hover:bg-blue-100',
    desc: 'AI medicine locator, 15-min emergency holds & prescription uploads',
  },
  pharmacist: {
    username: 'pharmacist1',
    label: 'Pharmacist',
    roleName: 'Scriptly Desk',
    icon: Package,
    color: 'border-emerald-200 bg-emerald-50/70 text-emerald-700 hover:bg-emerald-100',
    desc: 'Prescription verification, stock adjustments & pickup confirmation',
  },
  manager: {
    username: 'manager1',
    label: 'Manager',
    roleName: 'Branch Console',
    icon: Users,
    color: 'border-purple-200 bg-purple-50/70 text-purple-700 hover:bg-purple-100',
    desc: 'Branch staff scheduling, low-stock alerts & operational governance',
  },
  admin: {
    username: 'admin1',
    label: 'Admin',
    roleName: 'Co-Doctor Command',
    icon: ShieldCheck,
    color: 'border-amber-200 bg-amber-50/70 text-amber-700 hover:bg-amber-100',
    desc: 'Multi-city network observability, audit trails & routing telemetry',
  },
};

export const LoginPage: React.FC = () => {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('patient1');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('password123');
  const [selectedDemoRole, setSelectedDemoRole] = useState<DemoRole>('patient');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const login = useAuthStore((state: any) => state.login);
  const navigate = useNavigate();

  const handleSelectDemoRole = (roleKey: DemoRole) => {
    setSelectedDemoRole(roleKey);
    setMode('login');
    setUsername(DEMO_PRESETS[roleKey].username);
    setPassword('password123');
    setError('');
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      if (mode === 'register') {
        const res = await api.post<{ data: AuthResponse }>('/auth/register', {
          username: username.trim(),
          email: email.trim(),
          password,
        });
        const authData = res.data.data;
        login(authData);
        navigate('/search');
      } else {
        const res = await api.post<{ data: AuthResponse }>('/auth/login', {
          username: username.trim(),
          password,
        });
        const authData = res.data.data;
        login(authData);

        if (authData.role === 'ADMIN') {
          navigate('/');
        } else if (authData.role === 'PHARMACY_MANAGER') {
          navigate('/manager');
        } else if (authData.role === 'PHARMACIST') {
          navigate('/inventory');
        } else {
          navigate('/search');
        }
      }
    } catch (err: any) {
      setError(
        err.response?.data?.message ||
          (mode === 'register'
            ? 'Registration failed. Please try a different username or email.'
            : 'Invalid credentials or connection error.')
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 sm:p-6 lg:p-8 relative overflow-hidden bg-slate-100/70">
      {/* Dynamic Background Effects */}
      <div className="absolute -top-40 -left-40 w-96 h-96 bg-blue-500/10 rounded-full blur-3xl pointer-events-none" />
      <div className="absolute -bottom-40 -right-40 w-96 h-96 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />

      {/* Main Wide Card Container (max-w-5xl on desktop) */}
      <div className="w-full max-w-5xl bg-white border border-slate-200/90 rounded-3xl shadow-xl overflow-hidden z-10 grid grid-cols-1 lg:grid-cols-12">
        {/* Left Column: Brand Hero & Workspaces Showcase (5 cols on lg) */}
        <div className="lg:col-span-5 bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900 text-white p-6 sm:p-8 lg:p-10 flex flex-col justify-between relative overflow-hidden">
          <div className="absolute -right-16 -top-16 h-48 w-48 rounded-full bg-blue-500/20 blur-2xl pointer-events-none" />
          <div className="absolute -bottom-16 -left-16 h-48 w-48 rounded-full bg-emerald-500/20 blur-2xl pointer-events-none" />

          {/* Top Brand Header */}
          <div className="relative z-10 space-y-6">
            <div className="flex items-center gap-3">
              <img src={logoWhiteImg} alt="SentinelGrid" className="h-12 w-auto object-contain rounded-lg" />
            </div>

            <div>
              <h2 className="text-lg sm:text-xl font-bold text-white leading-snug">
                Real-Time Availability & Emergency Reservation Grid
              </h2>
              <p className="text-xs sm:text-sm text-slate-300 mt-2 leading-relaxed">
                Coordinating critical emergency medicine inventory, snake-bite antivenoms, and ICUs across verified pharmacy networks.
              </p>
            </div>

            {/* 4 Workspaces Showcase */}
            <div className="space-y-2.5 pt-2">
              <p className="text-[10px] font-bold uppercase tracking-widest text-blue-300/80">Platform Workspaces</p>

              <div className="space-y-2 text-xs">
                <div className="p-2.5 rounded-xl bg-white/5 border border-white/10 flex items-start gap-2.5">
                  <User className="w-4 h-4 text-blue-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-bold text-white">Patient (CareLink):</span>
                    <span className="text-slate-300 ml-1">AI search, distance routing & 15-minute emergency holds.</span>
                  </div>
                </div>

                <div className="p-2.5 rounded-xl bg-white/5 border border-white/10 flex items-start gap-2.5">
                  <Package className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-bold text-white">Pharmacist (Scriptly):</span>
                    <span className="text-slate-300 ml-1">Digital prescription review & real-time stock adjustments.</span>
                  </div>
                </div>

                <div className="p-2.5 rounded-xl bg-white/5 border border-white/10 flex items-start gap-2.5">
                  <Users className="w-4 h-4 text-purple-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-bold text-white">Manager:</span>
                    <span className="text-slate-300 ml-1">Branch staff scheduling & low-stock safety thresholds.</span>
                  </div>
                </div>

                <div className="p-2.5 rounded-xl bg-white/5 border border-white/10 flex items-start gap-2.5">
                  <ShieldCheck className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-bold text-white">Admin (Co-Doctor):</span>
                    <span className="text-slate-300 ml-1">Multi-city network telemetry & comprehensive audit trails.</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Left Footer: Pharmacy Partnership & Emergency Helpline */}
          <div className="relative z-10 pt-6 mt-6 border-t border-white/10 space-y-3">
            <Link
              to="/pharmacy-partnership"
              className="group flex items-center justify-between p-3 rounded-xl bg-blue-600/30 border border-blue-400/30 hover:bg-blue-600/50 transition-colors text-xs text-white"
            >
              <div className="flex items-center gap-2">
                <Building2 className="w-4 h-4 text-blue-300 shrink-0" />
                <span className="font-semibold">Join Pharmacy Network (Zoho)</span>
              </div>
              <span className="text-[11px] text-blue-200 group-hover:translate-x-0.5 transition-transform">Apply →</span>
            </Link>

            <div className="flex items-center justify-between text-[11px] text-slate-400">
              <span>Medical Emergency:</span>
              <span className="font-bold text-white bg-red-600/80 px-2 py-0.5 rounded-md">Dial 108 / 112</span>
            </div>
          </div>
        </div>

        {/* Right Column: Interactive Login / Register Form (7 cols on lg) */}
        <div className="lg:col-span-7 p-6 sm:p-8 lg:p-10 flex flex-col justify-center space-y-6">
          {/* Portfolio Sandbox Notice */}
          <div className="p-3 bg-amber-50 border border-amber-200/90 rounded-2xl flex items-start gap-2.5 text-amber-900 text-xs">
            <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
            <div className="leading-relaxed">
              <span className="font-bold">Portfolio Demonstration:</span> Synthetic availability & sandbox data. Destructive deletions are blocked under demo protection.
            </div>
          </div>

          {/* Header & Mode Switcher */}
          <div>
            <div className="flex items-center justify-between">
              <div>
                <div className="flex items-center gap-2 mb-2 lg:hidden">
                  <img src={logoMainImg} alt="SentinelGrid" className="h-8 w-auto object-contain" />
                </div>
                <h3 className="text-xl sm:text-2xl font-black text-slate-900 tracking-tight">
                  {mode === 'login' ? 'Sign In' : 'Create Account'}
                </h3>
                <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
                  {mode === 'login'
                    ? 'Select a demonstration role or enter your credentials below.'
                    : 'Register for a new patient account to start searching medicines.'}
                </p>
              </div>

              <div className="hidden sm:inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-blue-50 text-blue-700 text-xs font-semibold">
                <Sparkles className="w-3.5 h-3.5" />
                <span>Live Demo</span>
              </div>
            </div>

            {/* Mode Toggle (Sign In vs Register) */}
            <div className="flex bg-slate-100 p-1 rounded-xl mt-4">
              <button
                type="button"
                onClick={() => {
                  setMode('login');
                  setError('');
                }}
                className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all flex items-center justify-center gap-1.5 ${
                  mode === 'login' ? 'bg-white text-blue-700 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <LogIn className="w-3.5 h-3.5" />
                Sign In
              </button>
              <button
                type="button"
                onClick={() => {
                  setMode('register');
                  setError('');
                }}
                className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all flex items-center justify-center gap-1.5 ${
                  mode === 'register' ? 'bg-white text-blue-700 shadow-xs' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <UserPlus className="w-3.5 h-3.5" />
                Create Patient Account
              </button>
            </div>
          </div>

          {/* Demo Role Presets (Patient, Pharmacist, Manager, Admin) */}
          {mode === 'login' && (
            <div className="space-y-2.5">
              <div className="flex items-center justify-between">
                <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                  One-Click Demo Profiles:
                </span>
                <span className="text-[10px] text-blue-700 font-semibold bg-blue-50 px-2 py-0.5 rounded-full border border-blue-200">
                  Select Any Role
                </span>
              </div>

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {(Object.keys(DEMO_PRESETS) as DemoRole[]).map((roleKey) => {
                  const preset = DEMO_PRESETS[roleKey];
                  const Icon = preset.icon;
                  const isSelected = selectedDemoRole === roleKey && username === preset.username;

                  return (
                    <button
                      key={roleKey}
                      type="button"
                      onClick={() => handleSelectDemoRole(roleKey)}
                      className={`p-2.5 rounded-xl border text-left transition-all flex flex-col justify-between ${
                        isSelected
                          ? 'border-blue-600 bg-blue-50/90 ring-2 ring-blue-500/20 shadow-xs'
                          : 'border-slate-200 bg-slate-50/70 hover:bg-slate-100/80 text-slate-700'
                      }`}
                    >
                      <div className="flex items-center justify-between w-full mb-1">
                        <Icon className={`w-4 h-4 ${isSelected ? 'text-blue-600' : 'text-slate-500'}`} />
                        {isSelected && <CheckCircle2 className="w-3.5 h-3.5 text-blue-600" />}
                      </div>
                      <div>
                        <p className={`text-xs font-bold truncate ${isSelected ? 'text-blue-900' : 'text-slate-800'}`}>
                          {preset.label}
                        </p>
                        <p className="text-[10px] text-slate-450 truncate font-mono">{preset.username}</p>
                      </div>
                    </button>
                  );
                })}
              </div>

              {/* Active Role Description Snippet */}
              <div className="px-3 py-2 bg-slate-50 rounded-xl border border-slate-200/80 text-[11px] text-slate-600 flex items-center justify-between">
                <div className="flex items-center gap-1.5 truncate">
                  <span className="font-bold text-slate-800">{DEMO_PRESETS[selectedDemoRole].roleName}:</span>
                  <span className="text-slate-500 truncate">{DEMO_PRESETS[selectedDemoRole].desc}</span>
                </div>
                <span className="text-[10px] font-mono text-slate-400 shrink-0 ml-2">password123</span>
              </div>
            </div>
          )}

          {/* Error Message */}
          {error && (
            <div className="p-3.5 bg-red-50 border border-red-200 rounded-xl flex items-center gap-2.5 text-red-700 text-xs leading-relaxed">
              <AlertCircle className="w-4 h-4 shrink-0 text-red-600" />
              <span>{error}</span>
            </div>
          )}

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                Username
              </label>
              <div className="relative">
                <User className="w-4 h-4 text-slate-400 absolute left-3.5 top-3.5" />
                <input
                  type="text"
                  required
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder={mode === 'register' ? 'Choose username (min 3 chars)' : 'Enter username'}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl pl-10 pr-4 py-2.5 text-slate-800 placeholder-slate-400 text-sm focus:outline-none focus:border-blue-500 focus:bg-white transition-all"
                />
              </div>
            </div>

            {mode === 'register' && (
              <div>
                <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                  Email Address
                </label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-slate-400 absolute left-3.5 top-3.5" />
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="name@example.com"
                    className="w-full bg-slate-50 border border-slate-200 rounded-xl pl-10 pr-4 py-2.5 text-slate-800 placeholder-slate-400 text-sm focus:outline-none focus:border-blue-500 focus:bg-white transition-all"
                  />
                </div>
              </div>
            )}

            <div>
              <label className="block text-[11px] font-bold text-slate-500 uppercase tracking-wider mb-1.5">
                Password
              </label>
              <div className="relative">
                <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-3.5" />
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={mode === 'register' ? 'Create password (min 6 chars)' : 'Enter password'}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl pl-10 pr-4 py-2.5 text-slate-800 placeholder-slate-400 text-sm focus:outline-none focus:border-blue-500 focus:bg-white transition-all"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 rounded-xl shadow-md shadow-blue-500/20 flex items-center justify-center gap-2 text-sm sm:text-base transition-all disabled:opacity-50 mt-2"
            >
              {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <ShieldCheck className="w-4 h-4" />}
              <span>
                {mode === 'register'
                  ? 'Create Patient Account'
                  : `Sign In as ${DEMO_PRESETS[selectedDemoRole].label} (${username})`}
              </span>
            </button>
          </form>

          {/* Bottom Onboarding Link */}
          <div className="pt-2 border-t border-slate-100 text-center">
            <p className="text-xs text-slate-500">
              Are you a licensed pharmacy seeking to join the network?{' '}
              <Link
                to="/pharmacy-partnership"
                className="font-bold text-blue-600 hover:text-blue-700 hover:underline"
              >
                Apply for Pharmacy Partnership
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
