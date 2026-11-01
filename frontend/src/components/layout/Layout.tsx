import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { useCityStore, type SupportedCity } from '../../store/cityStore';
import api from '../../api/client';
import {
  Activity,
  Building2,
  ChevronLeft,
  ChevronRight,
  Clock,
  FileText,
  Handshake,
  LayoutDashboard,
  LogOut,
  MapPin,
  Menu,
  Package,
  Search,
  Server,
  Users,
  X,
} from 'lucide-react';
import brandMarkImg from '../../assets/logo/brand-mark.png';

interface LayoutProps {
  children: React.ReactNode;
}

type NavigationItem = {
  path: string;
  label: string;
  icon: React.ElementType;
};

const cityLabels: Record<SupportedCity, string> = {
  Puducherry: 'Puducherry',
  Chennai: 'Chennai',
  Bangalore: 'Bengaluru',
};

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem('sentinel_sidebar_collapsed') === 'true');
  const [mobileOpen, setMobileOpen] = useState(false);
  const { selectedCity, setSelectedCity } = useCityStore();
  const [apiConnected, setApiConnected] = useState(true);

  const isPatient = user?.role === 'PATIENT';
  const isPharmacist = user?.role === 'PHARMACIST';
  const isManager = user?.role === 'PHARMACY_MANAGER';
  const isAdmin = user?.role === 'ADMIN';

  useEffect(() => {
    localStorage.setItem('sentinel_sidebar_collapsed', String(collapsed));
  }, [collapsed]);

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    const checkConnection = async () => {
      try {
        await api.get('/medicines/search');
        setApiConnected(true);
      } catch {
        setApiConnected(false);
      }
    };

    checkConnection();
    const interval = window.setInterval(checkConnection, 30000);
    return () => window.clearInterval(interval);
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const patientNavigation: NavigationItem[] = [
    { path: '/', label: 'Overview', icon: LayoutDashboard },
    { path: '/search', label: 'Find medicine', icon: Search },
    { path: '/reservations', label: 'My reservations', icon: Clock },
    { path: '/pharmacies', label: 'Pharmacy network', icon: Building2 },
    { path: '/pharmacy-partnership', label: 'Pharmacy partnership', icon: Handshake },
  ];

  const branchNavigation: NavigationItem[] = [
    { path: '/', label: 'Branch overview', icon: LayoutDashboard },
    { path: '/inventory', label: 'Reservations and stock', icon: Package },
    ...(isManager ? [{ path: '/manager', label: 'Branch management', icon: Users }] : []),
    { path: '/pharmacy-partnership', label: 'Pharmacy partnership', icon: Handshake },
  ];

  const adminNavigation = [
    {
      label: 'OPERATIONS',
      items: [
        { path: '/', label: 'Network overview', icon: LayoutDashboard },
        { path: '/analytics', label: 'Analytics', icon: Activity },
        { path: '/pharmacy-partnership', label: 'Pharmacy partnership', icon: Handshake },
      ],
    },
    {
      label: 'GOVERNANCE',
      items: [
        { path: '/audit', label: 'Audit trail', icon: FileText },
        { path: '/system-health', label: 'System health', icon: Server },
      ],
    },
  ];

  const workspaceLabel = isPatient
    ? 'Patient workspace'
    : isManager
      ? 'Pharmacy manager'
      : isPharmacist
        ? 'Pharmacy operations'
        : 'Platform administration';

  const NavItem = ({ item, compact }: { item: NavigationItem; compact: boolean }) => {
    const Icon = item.icon;
    const active = location.pathname === item.path;
    return (
      <Link
        to={item.path}
        title={compact ? item.label : undefined}
        className={`group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
          active
            ? 'bg-blue-50 text-blue-700'
            : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950'
        }`}
      >
        <Icon className={`h-4 w-4 shrink-0 ${active ? 'text-blue-600' : 'text-slate-400 group-hover:text-slate-600'}`} />
        {!compact && <span className="truncate">{item.label}</span>}
      </Link>
    );
  };

  const SidebarContent = ({ compact = false, mobile = false }: { compact?: boolean; mobile?: boolean }) => (
    <>
      <div className="flex h-[72px] items-center justify-between border-b border-slate-200 px-4">
        <Link to="/" className="flex min-w-0 items-center gap-3" aria-label="SentinelGrid home">
          {compact ? (
            <img src={brandMarkImg} alt="SentinelGrid" className="h-9 w-9 shrink-0 object-contain rounded-lg" />
          ) : (
            <div className="flex items-center gap-2">
              <img src={brandMarkImg} alt="SentinelGrid" className="h-9 w-9 shrink-0 object-contain rounded-lg" />
              <div className="min-w-0">
                <p className="truncate text-sm font-extrabold tracking-tight text-slate-950">SentinelGrid</p>
                <p className="truncate text-[10px] font-semibold uppercase tracking-wide text-slate-400">Emergency medicine network</p>
              </div>
            </div>
          )}
        </Link>
        {mobile ? (
          <button onClick={() => setMobileOpen(false)} className="rounded-md p-2 text-slate-500 hover:bg-slate-100" aria-label="Close navigation">
            <X className="h-5 w-5" />
          </button>
        ) : (
          <button
            onClick={() => setCollapsed((current) => !current)}
            className="hidden rounded-md p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700 lg:inline-flex"
            title={compact ? 'Expand sidebar' : 'Collapse sidebar'}
            aria-label={compact ? 'Expand sidebar' : 'Collapse sidebar'}
          >
            {compact ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
          </button>
        )}
      </div>

      <div className="flex-1 overflow-y-auto px-3 py-5">
        {!compact && <p className="mb-2 px-3 text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">{workspaceLabel}</p>}
        <nav className="space-y-1" aria-label="Primary navigation">
          {isPatient && patientNavigation.map((item) => <NavItem key={item.path} item={item} compact={compact} />)}
          {(isPharmacist || isManager) && branchNavigation.map((item) => <NavItem key={item.path} item={item} compact={compact} />)}
          {isAdmin && adminNavigation.map((section) => (
            <div key={section.label} className="mb-5 space-y-1">
              {!compact && <p className="mb-2 mt-1 px-3 text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">{section.label}</p>}
              {section.items.map((item) => <NavItem key={item.path} item={item} compact={compact} />)}
            </div>
          ))}
        </nav>
      </div>

      <div className="border-t border-slate-200 p-3">
        {!compact ? (
          <div className="flex items-center gap-3 rounded-lg bg-slate-50 p-3">
            <div className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-slate-200 text-xs font-bold text-slate-700">
              {user?.username?.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-bold text-slate-800">{user?.username}</p>
              <p className="truncate text-[10px] font-semibold uppercase tracking-wide text-slate-400">{user?.role.replace('_', ' ')}</p>
            </div>
            <button onClick={handleLogout} className="rounded-md p-2 text-slate-400 hover:bg-red-50 hover:text-red-600" title="Sign out" aria-label="Sign out">
              <LogOut className="h-4 w-4" />
            </button>
          </div>
        ) : (
          <button onClick={handleLogout} className="flex w-full justify-center rounded-lg p-3 text-slate-400 hover:bg-red-50 hover:text-red-600" title="Sign out" aria-label="Sign out">
            <LogOut className="h-5 w-5" />
          </button>
        )}
      </div>
    </>
  );

  return (
    <div className="flex h-screen w-screen overflow-hidden bg-slate-50 font-sans text-slate-700">
      <aside className={`hidden shrink-0 flex-col border-r border-slate-200 bg-white transition-[width] duration-200 lg:flex ${collapsed ? 'w-[76px]' : 'w-64'}`}>
        <SidebarContent compact={collapsed} />
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <button className="absolute inset-0 bg-slate-950/35" onClick={() => setMobileOpen(false)} aria-label="Close navigation overlay" />
          <aside className="relative flex h-full w-[min(19rem,86vw)] flex-col bg-white shadow-2xl">
            <SidebarContent mobile />
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <div className="bg-amber-50/90 border-b border-amber-200/80 px-4 py-1.5 text-center text-[11px] font-medium text-amber-900 flex items-center justify-center gap-2 shrink-0">
          <span className="font-bold uppercase tracking-wider text-[9px] bg-amber-200/80 px-1.5 py-0.5 rounded text-amber-800">Portfolio Demo</span>
          <span className="truncate">Synthetic stock & demonstration pharmacy network. Not for real medicine fulfillment. Dial 108/112 in emergencies.</span>
        </div>
        <header className="flex min-h-[72px] shrink-0 items-center justify-between gap-3 border-b border-slate-200 bg-white px-4 sm:px-6">
          <div className="flex min-w-0 flex-1 items-center gap-3">
            <button onClick={() => setMobileOpen(true)} className="rounded-md p-2 text-slate-500 hover:bg-slate-100 lg:hidden" aria-label="Open navigation">
              <Menu className="h-5 w-5" />
            </button>
            {isPatient ? (
              <form
                onSubmit={(event) => {
                  event.preventDefault();
                  const target = event.currentTarget.elements.namedItem('patientSearch') as HTMLInputElement;
                  if (target?.value.trim()) navigate(`/search?name=${encodeURIComponent(target.value.trim())}`);
                }}
                className="relative w-full max-w-xl"
              >
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <input name="patientSearch" type="search" placeholder="Search emergency medicines" className="w-full rounded-lg border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm text-slate-800 outline-none transition-colors placeholder:text-slate-400 focus:border-blue-500 focus:bg-white" />
              </form>
            ) : (
              <div className="min-w-0">
                <p className="truncate text-sm font-bold text-slate-900">{isAdmin ? 'SentinelGrid operations' : user?.pharmacyName ?? `Pharmacy branch #${user?.pharmacyId}`}</p>
                <p className="hidden text-xs text-slate-500 sm:block">{isAdmin ? 'Network governance and operational oversight' : 'Emergency medicine availability workspace'}</p>
              </div>
            )}
          </div>

          <div className="flex shrink-0 items-center gap-2 sm:gap-3">
            {isPatient && (
              <label className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-2.5 py-2 text-xs font-semibold text-slate-700 shadow-xs">
                <MapPin className="h-4 w-4 text-blue-600" />
                <select value={selectedCity} onChange={(event) => setSelectedCity(event.target.value as SupportedCity)} className="max-w-24 bg-transparent outline-none sm:max-w-none">
                  {Object.entries(cityLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </select>
              </label>
            )}
            <span className={`hidden items-center gap-2 rounded-full px-2.5 py-1.5 text-[11px] font-semibold sm:inline-flex ${apiConnected ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}`}>
              <span className={`h-1.5 w-1.5 rounded-full ${apiConnected ? 'bg-emerald-500' : 'bg-red-500'}`} />
              {apiConnected ? 'System online' : 'System offline'}
            </span>
          </div>
        </header>

        <main className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8">{children}</main>
      </div>
    </div>
  );
};
