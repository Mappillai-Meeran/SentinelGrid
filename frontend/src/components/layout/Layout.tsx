import React from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import {
  Activity,
  LayoutDashboard,
  Search,
  Clock,
  Package,
  LogOut
} from 'lucide-react';

interface LayoutProps {
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const getNavLinks = () => {
    if (!user) return [];

    const links = [
      { path: '/search', label: 'Medicine Search', icon: Search, roles: ['PATIENT', 'PHARMACIST', 'ADMIN'] },
    ];

    if (user.role === 'PATIENT' || user.role === 'ADMIN') {
      links.push({ path: '/patient-dashboard', label: 'Patient Dashboard', icon: Activity, roles: ['PATIENT', 'ADMIN'] });
      links.push({ path: '/my-reservations', label: 'My Reservations', icon: Clock, roles: ['PATIENT', 'ADMIN'] });
    }

    if (user.role === 'PHARMACIST' || user.role === 'ADMIN') {
      links.push({ path: '/inventory', label: 'Pharmacy Stock', icon: Package, roles: ['PHARMACIST', 'ADMIN'] });
    }

    if (user.role === 'ADMIN') {
      links.push({ path: '/admin-dashboard', label: 'Admin Analytics', icon: LayoutDashboard, roles: ['ADMIN'] });
    }

    return links;
  };

  const navLinks = getNavLinks();

  return (
    <div className="min-h-screen bg-slate-50 flex">
      {/* Sidebar */}
      <aside className="w-64 bg-slate-900 text-white flex flex-col shadow-xl z-20">
        <div className="p-6 flex items-center gap-3 border-b border-slate-800">
          <div className="bg-blue-600 p-2 rounded-lg text-white shadow-lg shadow-blue-500/30">
            <Activity className="w-6 h-6 animate-pulse" />
          </div>
          <div>
            <h1 className="font-bold text-lg tracking-tight">SentinelGrid</h1>
            <p className="text-xs text-blue-400 font-medium">Emergency Health SaaS</p>
          </div>
        </div>

        <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
          {navLinks.map((link) => {
            const Icon = link.icon;
            const isActive = location.pathname === link.path;
            return (
              <Link
                key={link.path}
                to={link.path}
                className={`flex items-center gap-3 px-4 py-3 rounded-xl font-medium text-sm transition-all duration-200 ${
                  isActive
                    ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20'
                    : 'text-slate-400 hover:text-white hover:bg-slate-800/60'
                }`}
              >
                <Icon className={`w-5 h-5 ${isActive ? 'text-white' : 'text-slate-400'}`} />
                <span>{link.label}</span>
              </Link>
            );
          })}
        </nav>

        {/* User Card */}
        <div className="p-4 border-t border-slate-800 bg-slate-950/40">
          <div className="flex items-center gap-3 mb-3 px-2">
            <div className="w-9 h-9 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-blue-400 font-semibold text-sm">
              {user?.username.charAt(0).toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-semibold text-white truncate">{user?.username}</p>
              <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-blue-500/10 text-blue-400 border border-blue-500/20">
                {user?.role}
              </span>
            </div>
          </div>

          <button
            onClick={handleLogout}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 text-xs font-semibold text-slate-400 hover:text-red-400 hover:bg-red-500/10 rounded-lg transition-colors"
          >
            <LogOut className="w-4 h-4" />
            <span>Sign Out</span>
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <header className="bg-white border-b border-slate-200 px-8 py-4 flex items-center justify-between shadow-xs">
          <div>
            <h2 className="text-xl font-bold text-slate-800">
              {navLinks.find((l) => l.path === location.pathname)?.label || 'Healthcare Portal'}
            </h2>
            <p className="text-xs text-slate-500">Real-Time Emergency Stock & Reservation Intelligence</p>
          </div>

          <div className="flex items-center gap-4">
            <div className="flex items-center gap-2 px-3 py-1.5 bg-emerald-50 text-emerald-700 border border-emerald-200 rounded-full text-xs font-medium">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-ping"></span>
              Live Grid Active
            </div>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto p-8 bg-slate-50/50">
          {children}
        </main>
      </div>
    </div>
  );
};
