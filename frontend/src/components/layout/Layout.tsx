import React, { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import {
  Activity,
  LayoutDashboard,
  Search,
  Clock,
  Package,
  LogOut,
  Sparkles,
  Building2,
  ShieldCheck,
  FileText,
  Server,
  ChevronLeft,
  ChevronRight,
  MapPin,
  CheckCircle2,
  Database,
  Terminal
} from 'lucide-react';

interface LayoutProps {
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const [selectedCity, setSelectedCity] = useState('Puducherry');

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const navLinks = [
    { path: '/', label: 'Overview', icon: LayoutDashboard, roles: ['PATIENT', 'PHARMACIST', 'ADMIN'] },
    { path: '/operations', label: 'Emergency Ops', icon: Activity, roles: ['PATIENT', 'PHARMACIST', 'ADMIN'] },
    { path: '/search', label: 'Medicine Search', icon: Search, roles: ['PATIENT', 'PHARMACIST', 'ADMIN'] },
    { path: '/reservations', label: 'Reservations', icon: Clock, roles: ['PATIENT', 'PHARMACIST', 'ADMIN'] },
    { path: '/pharmacies', label: 'Pharmacy Network', icon: Building2, roles: ['PATIENT', 'PHARMACIST', 'ADMIN'] },
    { path: '/inventory', label: 'Inventory Stock', icon: Package, roles: ['PHARMACIST', 'ADMIN'] },
    { path: '/ai-search', label: 'AI Search Assistant', icon: Sparkles, roles: ['PATIENT', 'PHARMACIST', 'ADMIN'] },
    { path: '/analytics', label: 'Analytics Workspace', icon: ShieldCheck, roles: ['ADMIN'] },
    { path: '/audit', label: 'Audit Center', icon: FileText, roles: ['ADMIN'] },
    { path: '/system-health', label: 'System Health', icon: Server, roles: ['ADMIN', 'PHARMACIST', 'PATIENT'] },
  ];

  const filteredNavLinks = navLinks.filter(
    (link) => !user || link.roles.includes(user.role)
  );

  return (
    <div className="min-h-screen bg-[#0b1020] text-slate-100 flex overflow-x-hidden font-sans">
      {/* Left Sidebar */}
      <aside
        className={`${
          collapsed ? 'w-20' : 'w-64'
        } bg-[#111827]/90 border-r border-slate-800/80 backdrop-blur-xl flex flex-col justify-between transition-all duration-300 z-30 fixed lg:static h-screen`}
      >
        <div>
          {/* Logo & Header */}
          <div className="p-4 border-b border-slate-800/80 flex items-center justify-between">
            <div className="flex items-center gap-3 overflow-hidden">
              <div className="bg-blue-600/90 p-2.5 rounded-xl text-white shadow-lg shadow-blue-500/20 shrink-0">
                <Activity className="w-6 h-6 animate-pulse text-blue-100" />
              </div>
              {!collapsed && (
                <div className="min-w-0">
                  <h1 className="font-bold text-base tracking-tight text-white truncate">SentinelGrid</h1>
                  <span className="inline-flex items-center gap-1 text-[10px] font-bold text-blue-400 uppercase tracking-widest">
                    <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-ping"></span>
                    LOCAL DEMO
                  </span>
                </div>
              )}
            </div>

            <button
              onClick={() => setCollapsed(!collapsed)}
              className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition-colors hidden lg:block"
              title={collapsed ? 'Expand Sidebar' : 'Collapse Sidebar'}
            >
              {collapsed ? <ChevronRight className="w-5 h-5" /> : <ChevronLeft className="w-5 h-5" />}
            </button>
          </div>

          {/* Nav Items */}
          <nav className="p-3 space-y-1">
            {filteredNavLinks.map((link) => {
              const Icon = link.icon;
              const isActive = location.pathname === link.path;
              return (
                <Link
                  key={link.path}
                  to={link.path}
                  className={`flex items-center gap-3 px-3.5 py-3 rounded-xl font-medium text-xs transition-all ${
                    isActive
                      ? 'bg-blue-600 text-white font-semibold shadow-lg shadow-blue-600/25 border border-blue-500/30'
                      : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/60'
                  }`}
                  title={collapsed ? link.label : undefined}
                >
                  <Icon className={`w-4 h-4 shrink-0 ${isActive ? 'text-white' : 'text-slate-400'}`} />
                  {!collapsed && <span className="truncate">{link.label}</span>}
                </Link>
              );
            })}
          </nav>
        </div>

        {/* User Card & Logout */}
        <div className="p-3 border-t border-slate-800/80 bg-slate-950/40">
          {!collapsed ? (
            <div className="flex items-center justify-between gap-2 p-2 rounded-xl bg-slate-900/60 border border-slate-800">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="w-8 h-8 rounded-lg bg-blue-600/20 border border-blue-500/30 flex items-center justify-center text-blue-400 font-bold text-xs shrink-0">
                  {user?.username.charAt(0).toUpperCase()}
                </div>
                <div className="min-w-0">
                  <p className="text-xs font-bold text-slate-100 truncate">{user?.username}</p>
                  <span className="text-[10px] text-blue-400 font-semibold uppercase">{user?.role}</span>
                </div>
              </div>

              <button
                onClick={handleLogout}
                className="p-2 text-slate-400 hover:text-red-400 hover:bg-red-500/10 rounded-lg transition-colors"
                title="Sign Out"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <button
              onClick={handleLogout}
              className="w-full flex justify-center p-3 text-slate-400 hover:text-red-400 hover:bg-red-500/10 rounded-xl transition-colors"
              title="Sign Out"
            >
              <LogOut className="w-5 h-5" />
            </button>
          )}
        </div>
      </aside>

      {/* Main Layout Area */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Top Header Bar */}
        <header className="bg-[#111827]/80 backdrop-blur-xl border-b border-slate-800/80 px-6 py-3.5 flex flex-wrap items-center justify-between gap-4 sticky top-0 z-20">
          <div className="flex items-center gap-4">
            <div>
              <h2 className="text-base font-bold text-white tracking-tight flex items-center gap-2">
                SentinelGrid
                <span className="text-xs font-normal text-slate-400 italic">Emergency Medicine Operations Platform</span>
              </h2>
            </div>
          </div>

          {/* Status Badges & City Switcher */}
          <div className="flex items-center gap-3">
            <div className="hidden sm:flex items-center gap-2 px-2.5 py-1 bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 rounded-full text-[11px] font-semibold">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
              LIVE DEMO
            </div>

            <div className="hidden md:flex items-center gap-2 px-2.5 py-1 bg-blue-500/10 border border-blue-500/20 text-blue-400 rounded-full text-[11px] font-semibold">
              <Database className="w-3.5 h-3.5 text-blue-400" />
              PostgreSQL Connected
            </div>

            <div className="hidden lg:flex items-center gap-2 px-2.5 py-1 bg-purple-500/10 border border-purple-500/20 text-purple-400 rounded-full text-[11px] font-semibold">
              <Terminal className="w-3.5 h-3.5 text-purple-400" />
              Spring Boot API Connected
            </div>

            {/* City Selector */}
            <div className="flex items-center gap-2 bg-slate-900/80 border border-slate-800 px-3 py-1.5 rounded-xl text-xs">
              <MapPin className="w-3.5 h-3.5 text-blue-400 shrink-0" />
              <select
                value={selectedCity}
                onChange={(e) => setSelectedCity(e.target.value)}
                className="bg-transparent text-slate-200 font-semibold focus:outline-none cursor-pointer"
              >
                <option value="Puducherry" className="bg-slate-900 text-white">Puducherry</option>
                <option value="Chennai" className="bg-slate-900 text-white">Chennai</option>
                <option value="Bengaluru" className="bg-slate-900 text-white">Bengaluru</option>
              </select>
            </div>
          </div>
        </header>

        {/* Page Content */}
        <main className="flex-1 overflow-y-auto p-6 lg:p-8 space-y-6">
          {children}
        </main>
      </div>
    </div>
  );
};
