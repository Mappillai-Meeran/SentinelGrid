import React, { useEffect, useState } from 'react';
import api from '../api/client';
import type { DashboardSummary, LowStockItem, AuditLog } from '../types';
import {
  Activity,
  Building2,
  Pill,
  Clock,
  CheckCircle2,
  AlertTriangle,
  FileText,
  RefreshCw,
  Radio
} from 'lucide-react';

export const OverviewPage: React.FC = () => {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [lowStock, setLowStock] = useState<LowStockItem[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [lastSynced, setLastSynced] = useState<string>('');

  const fetchOverviewData = async () => {
    setLoading(true);
    try {
      const [sumRes, lowStockRes, auditRes] = await Promise.all([
        api.get('/admin/dashboard/summary'),
        api.get('/admin/dashboard/inventory/low-stock?threshold=10'),
        api.get('/admin/dashboard/audit-logs'),
      ]);

      setSummary(sumRes.data.data);
      setLowStock(lowStockRes.data.data || []);
      setAuditLogs((auditRes.data.data || []).slice(0, 8));
      setLastSynced(new Date().toLocaleTimeString());
    } catch (err) {
      console.error('Failed to load overview data', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOverviewData();
  }, []);

  return (
    <div className="space-y-6">
      {/* Hero Glass Card */}
      <div className="relative overflow-hidden rounded-3xl bg-gradient-to-r from-slate-900 via-blue-950/60 to-slate-900 p-8 border border-blue-500/20 shadow-2xl">
        <div className="absolute -top-24 -right-24 w-96 h-96 bg-blue-600/10 rounded-full blur-3xl pointer-events-none"></div>
        <div className="absolute -bottom-24 -left-24 w-96 h-96 bg-purple-600/10 rounded-full blur-3xl pointer-events-none"></div>

        <div className="relative z-10 flex flex-col lg:flex-row justify-between items-start lg:items-center gap-6">
          <div className="space-y-3 max-w-2xl">
            <div className="inline-flex items-center gap-2 px-3 py-1 bg-blue-500/10 border border-blue-500/30 rounded-full text-xs font-bold text-blue-400">
              <Radio className="w-3.5 h-3.5 text-blue-400 animate-pulse" />
              EMERGENCY OPERATIONS CONSOLE
            </div>
            <h1 className="text-3xl font-extrabold text-white tracking-tight">
              Emergency Medicine Availability &amp; Reservation Platform
            </h1>
            <p className="text-sm text-slate-300">
              Real-time synchronization engine protecting against double-booking with JPA Optimistic Locking (@Version) &amp; Redis Cache Eviction.
            </p>
          </div>

          <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 w-full lg:w-auto">
            <div className="p-4 bg-slate-900/80 border border-slate-800 rounded-2xl text-xs space-y-1">
              <div className="flex items-center justify-between gap-4 text-slate-400">
                <span>Environment:</span>
                <span className="font-bold text-emerald-400">Local Demo</span>
              </div>
              <div className="flex items-center justify-between gap-4 text-slate-400">
                <span>Last Sync:</span>
                <span className="font-bold text-slate-200">{lastSynced || 'Syncing...'}</span>
              </div>
            </div>

            <button
              onClick={fetchOverviewData}
              disabled={loading}
              className="px-5 py-3.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-2xl text-xs flex items-center justify-center gap-2 shadow-lg shadow-blue-600/30 transition-all disabled:opacity-50"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
              <span>Refresh Operations</span>
            </button>
          </div>
        </div>
      </div>

      {/* Executive KPI Strip (Real Metrics) */}
      <div className="grid grid-cols-2 lg:grid-cols-6 gap-4">
        <div className="glass-panel p-5 rounded-2xl space-y-2 border border-slate-800">
          <div className="flex justify-between items-center text-slate-400">
            <span className="text-[11px] font-bold uppercase tracking-wider">Total Orders</span>
            <Activity className="w-4 h-4 text-blue-400" />
          </div>
          <p className="text-2xl font-black text-white">{summary?.totalReservations ?? 0}</p>
          <span className="text-[10px] text-slate-400 block">Updated just now</span>
        </div>

        <div className="glass-panel p-5 rounded-2xl space-y-2 border border-amber-500/20">
          <div className="flex justify-between items-center text-amber-400">
            <span className="text-[11px] font-bold uppercase tracking-wider">Pending</span>
            <Clock className="w-4 h-4 text-amber-400" />
          </div>
          <p className="text-2xl font-black text-amber-400">{summary?.pendingReservations ?? 0}</p>
          <span className="text-[10px] text-slate-400 block">Stock held</span>
        </div>

        <div className="glass-panel p-5 rounded-2xl space-y-2 border border-emerald-500/20">
          <div className="flex justify-between items-center text-emerald-400">
            <span className="text-[11px] font-bold uppercase tracking-wider">Confirmed</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          </div>
          <p className="text-2xl font-black text-emerald-400">{summary?.confirmedReservations ?? 0}</p>
          <span className="text-[10px] text-slate-400 block">Pickups completed</span>
        </div>

        <div className="glass-panel p-5 rounded-2xl space-y-2 border border-red-500/20">
          <div className="flex justify-between items-center text-red-400">
            <span className="text-[11px] font-bold uppercase tracking-wider">Expired</span>
            <AlertTriangle className="w-4 h-4 text-red-400" />
          </div>
          <p className="text-2xl font-black text-red-400">{summary?.expiredReservations ?? 0}</p>
          <span className="text-[10px] text-slate-400 block">TTL released</span>
        </div>

        <div className="glass-panel p-5 rounded-2xl space-y-2 border border-purple-500/20">
          <div className="flex justify-between items-center text-purple-400">
            <span className="text-[11px] font-bold uppercase tracking-wider">Medicines</span>
            <Pill className="w-4 h-4 text-purple-400" />
          </div>
          <p className="text-2xl font-black text-purple-300">{summary?.totalMedicines ?? 0}</p>
          <span className="text-[10px] text-slate-400 block">Catalog items</span>
        </div>

        <div className="glass-panel p-5 rounded-2xl space-y-2 border border-slate-800">
          <div className="flex justify-between items-center text-slate-400">
            <span className="text-[11px] font-bold uppercase tracking-wider">Pharmacies</span>
            <Building2 className="w-4 h-4 text-blue-400" />
          </div>
          <p className="text-2xl font-black text-slate-100">{summary?.totalPharmacies ?? 0}</p>
          <span className="text-[10px] text-slate-400 block">Active hubs</span>
        </div>
      </div>

      {/* Main Grid: Critical Stock Watchlist & Activity Feed */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Critical Stock Watchlist (Red Accent Panel) */}
        <div className="lg:col-span-2 glass-card-red rounded-3xl p-6 border border-red-500/30 space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2.5 bg-red-500/20 text-red-400 rounded-xl border border-red-500/30">
                <AlertTriangle className="w-5 h-5 animate-bounce text-red-400" />
              </div>
              <div>
                <h3 className="font-bold text-white text-base">Critical Stock Watchlist</h3>
                <p className="text-xs text-red-300/80">Inventory items with available quantity below 10 units</p>
              </div>
            </div>

            <span className="px-3 py-1 bg-red-500/20 border border-red-500/40 text-red-300 font-bold text-xs rounded-full">
              {lowStock.length} Alerts Active
            </span>
          </div>

          {lowStock.length === 0 ? (
            <div className="p-8 text-center bg-slate-900/60 rounded-2xl border border-slate-800 text-slate-400 text-xs font-semibold">
              All pharmacy emergency stock levels are above threshold.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-900/80 text-slate-400 font-bold uppercase tracking-wider border-b border-slate-800">
                  <tr>
                    <th className="p-3">Pharmacy</th>
                    <th className="p-3">Medicine</th>
                    <th className="p-3">Total Qty</th>
                    <th className="p-3">Reserved</th>
                    <th className="p-3">Available</th>
                    <th className="p-3">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60 font-medium">
                  {lowStock.map((item) => (
                    <tr key={item.inventoryId} className="hover:bg-slate-900/40">
                      <td className="p-3 font-semibold text-white">{item.pharmacyName}</td>
                      <td className="p-3 text-slate-300">{item.medicineName}</td>
                      <td className="p-3 text-slate-400">{item.quantity}</td>
                      <td className="p-3 text-amber-400 font-bold">{item.reservedQuantity}</td>
                      <td className="p-3">
                        <span className="font-bold text-red-400">{item.availableQuantity} units</span>
                      </td>
                      <td className="p-3">
                        <span className="inline-flex px-2.5 py-0.5 rounded-md text-[10px] font-bold bg-red-500/20 text-red-400 border border-red-500/40">
                          {item.availableQuantity <= 3 ? 'CRITICAL' : 'LOW STOCK'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Real-time Activity Feed (Audit Console) */}
        <div className="glass-panel rounded-3xl p-6 border border-slate-800 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="font-bold text-white text-base flex items-center gap-2">
              <FileText className="w-5 h-5 text-blue-400" />
              Live Audit Console
            </h3>
            <span className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">Real Audit Log</span>
          </div>

          <div className="space-y-3 max-h-[380px] overflow-y-auto pr-1">
            {auditLogs.length === 0 ? (
              <p className="text-xs text-slate-500 py-6 text-center">No recent audit log entries.</p>
            ) : (
              auditLogs.map((log) => (
                <div key={log.id} className="p-3.5 bg-slate-900/70 rounded-2xl border border-slate-800 text-xs space-y-1">
                  <div className="flex justify-between items-center">
                    <span className="font-bold text-blue-400">{log.action}</span>
                    <span className="text-[10px] text-slate-400">
                      {new Date(log.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </span>
                  </div>
                  <p className="text-slate-300 font-medium">{log.details}</p>
                  <div className="flex justify-between text-[10px] text-slate-400 pt-1">
                    <span>By: <strong className="text-slate-300">{log.performedBy}</strong></span>
                    <span>{log.entityName} #{log.entityId}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
