import React, { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowRight,
  Building2,
  CheckCircle2,
  ClipboardList,
  FileSpreadsheet,
  Package,
  RefreshCw,
  TrendingUp,
} from 'lucide-react';
import { Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { dashboardService } from '../../api/services';
import type { AuditLog, DailyReservation, DashboardSummary, LowStockItem, TopPharmacy } from '../../types';
import { useToast } from '../ui/Toast';

const formatDate = (value: string) =>
  new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' }).format(new Date(value));

const formatTimestamp = (value: string) =>
  new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }).format(new Date(value));

export const AdminDashboardView: React.FC = () => {
  const { showError, showToast } = useToast();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [lowStock, setLowStock] = useState<LowStockItem[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [topPharmacies, setTopPharmacies] = useState<TopPharmacy[]>([]);
  const [dailyTrend, setDailyTrend] = useState<DailyReservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const loadData = async (silent = false) => {
    if (silent) setRefreshing(true);
    else setLoading(true);

    try {
      const [summaryData, stockData, logsData, pharmacyData, trendData] = await Promise.all([
        dashboardService.getSummary(),
        dashboardService.getLowStock(10),
        dashboardService.getAuditLogs(),
        dashboardService.getTopPharmacies(6),
        dashboardService.getDailyTrend(7),
      ]);
      setSummary(summaryData);
      setLowStock(stockData);
      setAuditLogs(logsData);
      setTopPharmacies(pharmacyData);
      setDailyTrend(trendData);
    } catch (error) {
      console.error('Failed to load network operations data', error);
      showError(error, 'Unable to refresh network operations data');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const reservationStatus = useMemo(() => {
    if (!summary) return [];
    return [
      { label: 'Pending', value: summary.pendingReservations, color: 'bg-amber-500', text: 'text-amber-700' },
      { label: 'Confirmed', value: summary.confirmedReservations, color: 'bg-emerald-500', text: 'text-emerald-700' },
      { label: 'Expired', value: summary.expiredReservations, color: 'bg-red-500', text: 'text-red-700' },
      { label: 'Cancelled', value: summary.cancelledReservations, color: 'bg-slate-400', text: 'text-slate-600' },
    ];
  }, [summary]);

  const criticalStock = lowStock.filter((item) => item.availableQuantity <= 3);
  const totalReservationStates = reservationStatus.reduce((total, item) => total + item.value, 0);

  const exportAuditCSV = () => {
    if (auditLogs.length === 0) return;
    const headers = 'ID,Timestamp,Action,Entity,EntityID,PerformedBy,Details\n';
    const rows = auditLogs
      .map((log) => `"${log.id}","${log.timestamp}","${log.action}","${log.entityName}","${log.entityId}","${log.performedBy}","${log.details.replace(/"/g, '""')}"`)
      .join('\n');
    const link = document.createElement('a');
    link.href = encodeURI(`data:text/csv;charset=utf-8,${headers}${rows}`);
    link.download = `SentinelGrid_Audit_Report_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('Audit report exported', 'success');
  };

  const kpis = [
    { label: 'Active reservations', value: summary?.pendingReservations ?? 0, detail: 'Awaiting pharmacy action', icon: ClipboardList, tone: 'blue' },
    { label: 'Critical stock risks', value: criticalStock.length, detail: 'At 3 units or fewer', icon: AlertTriangle, tone: 'amber' },
    { label: 'Verified pharmacies', value: summary?.totalPharmacies ?? 0, detail: 'Participating in the network', icon: Building2, tone: 'emerald' },
    { label: 'Available inventory', value: summary?.totalInventoryUnits ?? 0, detail: 'Units currently tracked', icon: Package, tone: 'slate' },
  ] as const;

  const toneClasses = {
    blue: 'bg-blue-50 text-blue-700 ring-blue-100',
    amber: 'bg-amber-50 text-amber-700 ring-amber-100',
    emerald: 'bg-emerald-50 text-emerald-700 ring-emerald-100',
    slate: 'bg-slate-100 text-slate-700 ring-slate-200',
  };

  return (
    <div className="mx-auto max-w-[1440px] space-y-6 pb-10">
      <header className="flex flex-col gap-4 border-b border-slate-200 pb-6 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.14em] text-blue-700">Platform administration</p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">Network operations</h1>
          <p className="mt-2 max-w-2xl text-sm text-slate-500">Monitor reservation flow, stock exceptions, pharmacy activity, and audit events across SentinelGrid.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <button onClick={() => loadData(true)} disabled={refreshing} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3.5 py-2.5 text-xs font-semibold text-slate-700 shadow-xs transition-colors hover:bg-slate-50 disabled:opacity-60"><RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />Refresh data</button>
          <button onClick={exportAuditCSV} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3.5 py-2.5 text-xs font-semibold text-slate-700 shadow-xs transition-colors hover:bg-slate-50"><FileSpreadsheet className="h-4 w-4 text-emerald-600" />Export audit</button>
          <Link to="/analytics" className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3.5 py-2.5 text-xs font-semibold text-white shadow-sm shadow-blue-600/20 transition-colors hover:bg-blue-700">View analytics<ArrowRight className="h-4 w-4" /></Link>
        </div>
      </header>

      <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4" aria-label="Operational summary">
        {kpis.map((kpi) => {
          const Icon = kpi.icon;
          return <div key={kpi.label} className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs"><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-semibold text-slate-500">{kpi.label}</p><p className="mt-2 text-3xl font-bold tracking-tight text-slate-950">{loading ? '—' : kpi.value}</p></div><span className={`grid h-9 w-9 place-items-center rounded-lg ring-1 ${toneClasses[kpi.tone]}`}><Icon className="h-4 w-4" /></span></div><p className="mt-3 text-xs text-slate-500">{kpi.detail}</p></div>;
        })}
      </section>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1.35fr)_minmax(320px,0.65fr)]">
        <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xs">
          <div className="flex flex-col gap-3 border-b border-slate-200 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"><div><h2 className="text-sm font-bold text-slate-950">Stock exceptions</h2><p className="mt-1 text-xs text-slate-500">Medicines below the operational stock threshold.</p></div><Link to="/analytics" className="inline-flex items-center gap-1 text-xs font-semibold text-blue-700 hover:text-blue-800">Review all <ArrowRight className="h-3.5 w-3.5" /></Link></div>
          {lowStock.length === 0 && !loading ? <div className="flex min-h-52 flex-col items-center justify-center px-5 text-center"><CheckCircle2 className="h-7 w-7 text-emerald-600" /><p className="mt-3 text-sm font-semibold text-slate-800">No stock exceptions</p><p className="mt-1 text-xs text-slate-500">All tracked medicine lines are above the configured threshold.</p></div> : <div className="overflow-x-auto"><table className="w-full min-w-[620px] text-left text-sm"><thead className="bg-slate-50 text-[11px] font-bold uppercase tracking-wide text-slate-500"><tr><th className="px-5 py-3">Medicine</th><th className="px-5 py-3">Pharmacy</th><th className="px-5 py-3 text-right">Available</th><th className="px-5 py-3 text-right">Reserved</th><th className="px-5 py-3">Risk</th></tr></thead><tbody className="divide-y divide-slate-100">{loading ? Array.from({ length: 4 }).map((_, index) => <tr key={index}><td colSpan={5} className="px-5 py-4"><div className="h-4 w-full animate-pulse rounded bg-slate-100" /></td></tr>) : lowStock.slice(0, 6).map((item) => { const critical = item.availableQuantity <= 3; return <tr key={item.inventoryId} className="hover:bg-slate-50/70"><td className="px-5 py-3.5 font-semibold text-slate-900">{item.medicineName}</td><td className="px-5 py-3.5 text-xs text-slate-600">{item.pharmacyName}</td><td className="px-5 py-3.5 text-right font-bold text-slate-900">{item.availableQuantity}</td><td className="px-5 py-3.5 text-right text-slate-600">{item.reservedQuantity}</td><td className="px-5 py-3.5"><span className={`inline-flex rounded-full px-2.5 py-1 text-[11px] font-bold ${critical ? 'bg-red-50 text-red-700' : 'bg-amber-50 text-amber-700'}`}>{critical ? 'Critical' : 'Low'}</span></td></tr>; })}</tbody></table></div>}
        </section>

        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs"><div className="flex items-start justify-between gap-3"><div><h2 className="text-sm font-bold text-slate-950">Reservation status</h2><p className="mt-1 text-xs text-slate-500">Current distribution of reservation outcomes.</p></div><ClipboardList className="h-5 w-5 text-blue-600" /></div><div className="mt-6 space-y-4">{reservationStatus.map((status) => { const percentage = totalReservationStates ? Math.round((status.value / totalReservationStates) * 100) : 0; return <div key={status.label}><div className="mb-1.5 flex items-center justify-between text-xs"><span className="font-semibold text-slate-700">{status.label}</span><span className={status.text}>{status.value} <span className="text-slate-400">({percentage}%)</span></span></div><div className="h-2 overflow-hidden rounded-full bg-slate-100"><div className={`h-full rounded-full ${status.color}`} style={{ width: `${percentage}%` }} /></div></div>; })}</div><Link to="/analytics" className="mt-6 inline-flex items-center gap-1 text-xs font-semibold text-blue-700 hover:text-blue-800">Open reservation analytics <ArrowRight className="h-3.5 w-3.5" /></Link></section>
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs"><div className="flex items-start justify-between gap-3"><div><h2 className="text-sm font-bold text-slate-950">Reservation volume</h2><p className="mt-1 text-xs text-slate-500">Reservations created per day over the last seven days.</p></div><TrendingUp className="h-5 w-5 text-blue-600" /></div><div className="mt-5 h-56">{dailyTrend.length === 0 && !loading ? <div className="grid h-full place-items-center text-xs text-slate-400">No reservation trend data yet.</div> : <ResponsiveContainer width="100%" height="100%"><LineChart data={dailyTrend} margin={{ top: 8, left: -22, right: 8, bottom: 0 }}><XAxis dataKey="date" tickFormatter={formatDate} tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} /><YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#64748b' }} axisLine={false} tickLine={false} /><Tooltip labelFormatter={(value) => formatDate(String(value))} contentStyle={{ borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 12 }} /><Line type="monotone" dataKey="count" stroke="#2563eb" strokeWidth={2.5} dot={{ r: 3, fill: '#2563eb' }} activeDot={{ r: 4 }} /></LineChart></ResponsiveContainer>}</div></section>

        <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xs"><div className="flex items-start justify-between gap-3 border-b border-slate-200 px-5 py-4"><div><h2 className="text-sm font-bold text-slate-950">Pharmacy activity</h2><p className="mt-1 text-xs text-slate-500">Ranked by recorded reservation activity.</p></div><Building2 className="h-5 w-5 text-blue-600" /></div><div className="divide-y divide-slate-100">{loading ? Array.from({ length: 4 }).map((_, index) => <div key={index} className="px-5 py-4"><div className="h-4 animate-pulse rounded bg-slate-100" /></div>) : topPharmacies.length === 0 ? <div className="px-5 py-12 text-center text-xs text-slate-400">No pharmacy activity has been recorded yet.</div> : topPharmacies.slice(0, 5).map((pharmacy, index) => <div key={pharmacy.pharmacyName} className="flex items-center gap-3 px-5 py-3.5"><span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-slate-100 text-xs font-bold text-slate-500">{index + 1}</span><div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold text-slate-900">{pharmacy.pharmacyName}</p><p className="mt-0.5 text-xs text-slate-500">{pharmacy.reservationCount} reservations recorded</p></div>{pharmacy.fulfillmentRate == null ? <span className="text-xs text-slate-400">Rate unavailable</span> : <span className="text-sm font-bold text-emerald-700">{pharmacy.fulfillmentRate}%</span>}</div>)}</div></section>
      </div>

      <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xs"><div className="flex flex-col gap-3 border-b border-slate-200 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"><div><h2 className="text-sm font-bold text-slate-950">Recent audit activity</h2><p className="mt-1 text-xs text-slate-500">Latest immutable record entries from the platform.</p></div><Link to="/audit" className="inline-flex items-center gap-1 text-xs font-semibold text-blue-700 hover:text-blue-800">Open audit trail <ArrowRight className="h-3.5 w-3.5" /></Link></div>{auditLogs.length === 0 && !loading ? <div className="px-5 py-12 text-center text-xs text-slate-400">No audit activity recorded yet.</div> : <div className="divide-y divide-slate-100">{loading ? Array.from({ length: 3 }).map((_, index) => <div key={index} className="px-5 py-4"><div className="h-4 animate-pulse rounded bg-slate-100" /></div>) : auditLogs.slice(0, 5).map((log) => <div key={log.id} className="flex flex-col gap-2 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><span className="rounded bg-blue-50 px-2 py-0.5 text-[10px] font-bold text-blue-700">{log.action}</span><span className="text-xs font-semibold text-slate-800">{log.entityName} #{log.entityId}</span></div><p className="mt-1 truncate text-xs text-slate-500">{log.details}</p></div><div className="shrink-0 text-xs text-slate-400"><span className="font-semibold text-slate-600">{log.performedBy}</span> · {formatTimestamp(log.timestamp)}</div></div>)}</div>}</section>
    </div>
  );
};

export default AdminDashboardView;
