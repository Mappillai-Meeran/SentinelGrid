import React, { useEffect, useState } from 'react';
import { dashboardService } from '../api/services';
import type { DashboardSummary, LowStockItem } from '../types';
import { AlertTriangle, FileSpreadsheet, PieChart as PieIcon, ShieldCheck } from 'lucide-react';
import { ChartSkeleton } from '../components/ui/Skeleton';
import { ReservationLookupPanel } from '../components/reservations/ReservationLookupPanel';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

export const AnalyticsPage: React.FC = () => {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [lowStock, setLowStock] = useState<LowStockItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchAnalytics = async () => {
      setLoading(true);
      try {
        const [summaryData, lowStockData] = await Promise.all([
          dashboardService.getSummary(),
          dashboardService.getLowStock(10),
        ]);

        setSummary(summaryData);
        setLowStock(lowStockData);
      } catch (err) {
        console.error('Failed to load analytics workspace data', err);
      } finally {
        setLoading(false);
      }
    };

    fetchAnalytics();
  }, []);

  const exportCSV = () => {
    if (!summary) return;
    const csvContent =
      'data:text/csv;charset=utf-8,' +
      'Metric,Value\n' +
      `Total Reservations,${summary.totalReservations}\n` +
      `Pending Reservations,${summary.pendingReservations}\n` +
      `Confirmed Reservations,${summary.confirmedReservations}\n` +
      `Expired Reservations,${summary.expiredReservations}\n` +
      `Cancelled Reservations,${summary.cancelledReservations}\n` +
      `Low Stock Items,${lowStock.length}\n`;

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `SentinelGrid_Operational_Analytics_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const reservationStatusData = summary
    ? [
        { name: 'Pending', value: summary.pendingReservations, color: '#f59e0b' },
        { name: 'Confirmed', value: summary.confirmedReservations, color: '#10b981' },
        { name: 'Expired', value: summary.expiredReservations, color: '#ef4444' },
        { name: 'Cancelled', value: summary.cancelledReservations, color: '#64748b' },
      ]
    : [];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-900 flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-blue-500" />
            Operational Analytics
          </h2>
          <p className="text-xs text-slate-500">Focused charts for stock risk and reservation reliability.</p>
        </div>

        <button
          onClick={exportCSV}
          className="px-4 py-2.5 bg-white hover:bg-slate-50 text-slate-700 font-bold rounded-xl text-xs flex items-center gap-2 border border-slate-200 shadow-xs transition-colors"
        >
          <FileSpreadsheet className="w-4 h-4 text-emerald-600" />
          Export CSV
        </button>
      </div>

      <ReservationLookupPanel scopeLabel="Admin access can inspect reservation details across all pharmacy branches." />

      {loading ? (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <ChartSkeleton />
          <ChartSkeleton />
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <section className="glass-panel p-6 rounded-3xl border border-slate-200/80 space-y-4">
            <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 text-amber-500" />
              Low Stock / Critical Medicines
            </h3>
            <div className="h-72">
              {lowStock.length === 0 ? (
                <div className="h-full flex items-center justify-center text-xs text-slate-400">No low stock items right now.</div>
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={lowStock}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
                    <XAxis dataKey="medicineName" stroke="#94a3b8" tick={{ fontSize: 10 }} interval={0} />
                    <YAxis stroke="#94a3b8" allowDecimals={false} />
                    <Tooltip contentStyle={{ backgroundColor: '#ffffff', borderColor: '#e2e8f0', borderRadius: '12px', fontSize: '12px' }} />
                    <Bar dataKey="availableQuantity" fill="#f59e0b" radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>
          </section>

          <section className="glass-panel p-6 rounded-3xl border border-slate-200/80 space-y-4">
            <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
              <PieIcon className="w-4 h-4 text-blue-500" />
              Reservation Status Overview
            </h3>
            <div className="h-56">
              {!summary || summary.totalReservations === 0 ? (
                <div className="h-full flex items-center justify-center text-xs text-slate-400">No reservation records collected yet.</div>
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={reservationStatusData} cx="50%" cy="50%" innerRadius={58} outerRadius={86} paddingAngle={4} dataKey="value">
                      {reservationStatusData.map((entry) => (
                        <Cell key={entry.name} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={{ backgroundColor: '#ffffff', borderColor: '#e2e8f0', borderRadius: '12px', fontSize: '12px' }} />
                  </PieChart>
                </ResponsiveContainer>
              )}
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {reservationStatusData.map((item) => (
                <div key={item.name} className="rounded-2xl bg-white border border-slate-200 p-3 text-center">
                  <p className="text-lg font-bold text-slate-900">{item.value}</p>
                  <p className="text-[10px] font-bold text-slate-500 uppercase">{item.name}</p>
                </div>
              ))}
            </div>
          </section>
        </div>
      )}
    </div>
  );
};
