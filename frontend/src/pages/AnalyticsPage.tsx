import React, { useEffect, useState } from 'react';
import api from '../api/client';
import type { DashboardSummary, TopMedicine, TopPharmacy, DailyReservation } from '../types';
import {
  ShieldCheck,
  BarChart3,
  TrendingUp,
  PieChart as PieIcon,
  Building2,
  FileSpreadsheet
} from 'lucide-react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  PieChart,
  Pie,
  Cell,
  LineChart,
  Line,
  CartesianGrid
} from 'recharts';

export const AnalyticsPage: React.FC = () => {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [topMedicines, setTopMedicines] = useState<TopMedicine[]>([]);
  const [topPharmacies, setTopPharmacies] = useState<TopPharmacy[]>([]);
  const [dailyTrend, setDailyTrend] = useState<DailyReservation[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchAnalytics = async () => {
      setLoading(true);
      try {
        const [sumRes, topMedRes, topPharmRes, trendRes] = await Promise.all([
          api.get('/admin/dashboard/summary'),
          api.get('/admin/dashboard/top-medicines?limit=5'),
          api.get('/admin/dashboard/pharmacies/top?limit=5'),
          api.get('/admin/dashboard/reservations/daily?days=7'),
        ]);

        setSummary(sumRes.data.data);
        setTopMedicines(topMedRes.data.data || []);
        setTopPharmacies(topPharmRes.data.data || []);
        setDailyTrend(trendRes.data.data || []);
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
      `Total Users,${summary.totalUsers}\n` +
      `Total Pharmacies,${summary.totalPharmacies}\n` +
      `Total Medicines,${summary.totalMedicines}\n` +
      `Total Reservations,${summary.totalReservations}\n` +
      `Pending Reservations,${summary.pendingReservations}\n` +
      `Confirmed Reservations,${summary.confirmedReservations}\n` +
      `Expired Reservations,${summary.expiredReservations}\n` +
      `Cancelled Reservations,${summary.cancelledReservations}\n` +
      `Total Inventory Stock,${summary.totalInventoryUnits}\n`;

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `SentinelGrid_Analytics_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const reservationPieData = summary
    ? [
        { name: 'Pending', value: summary.pendingReservations, color: '#F59E0B' },
        { name: 'Confirmed', value: summary.confirmedReservations, color: '#10B981' },
        { name: 'Expired', value: summary.expiredReservations, color: '#EF4444' },
        { name: 'Cancelled', value: summary.cancelledReservations, color: '#64748B' },
      ]
    : [];

  return (
    <div className="space-y-6">
      {/* Top Title & Export Button */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-white flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-blue-400" />
            Executive Analytics &amp; Intelligence Workspace
          </h2>
          <p className="text-xs text-slate-400">System telemetry, demand breakdown, and performance metrics</p>
        </div>

        <button
          onClick={exportCSV}
          className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 font-bold rounded-xl text-xs flex items-center gap-2 border border-slate-700 transition-colors"
        >
          <FileSpreadsheet className="w-4 h-4 text-emerald-400" />
          Export Analytics CSV
        </button>
      </div>

      {loading ? (
        <div className="text-center py-12 text-slate-500 font-medium text-xs">Loading analytics data...</div>
      ) : (
        <>
          {/* Charts Row 1: Line Trend & Donut Distribution */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Daily Reservation Trend Line Chart */}
            <div className="lg:col-span-2 glass-panel p-6 rounded-3xl border border-slate-800 space-y-4">
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <TrendingUp className="w-4 h-4 text-blue-400" />
                Daily Reservation Volume (Last 7 Days)
              </h3>
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={dailyTrend}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                    <XAxis dataKey="date" stroke="#64748b" tick={{ fontSize: 11 }} />
                    <YAxis stroke="#64748b" allowDecimals={false} />
                    <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '12px', fontSize: '12px' }} />
                    <Line type="monotone" dataKey="count" stroke="#3b82f6" strokeWidth={3} dot={{ fill: '#3b82f6', r: 5 }} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>

            {/* Reservation Status Donut Chart */}
            <div className="glass-panel p-6 rounded-3xl border border-slate-800 space-y-4">
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <PieIcon className="w-4 h-4 text-purple-400" />
                Status Distribution
              </h3>
              <div className="h-48">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={reservationPieData}
                      cx="50%"
                      cy="50%"
                      innerRadius={50}
                      outerRadius={75}
                      paddingAngle={5}
                      dataKey="value"
                    >
                      {reservationPieData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '12px', fontSize: '12px' }} />
                  </PieChart>
                </ResponsiveContainer>
              </div>

              <div className="grid grid-cols-2 gap-2 text-xs font-semibold pt-2 border-t border-slate-800">
                {reservationPieData.map((item) => (
                  <div key={item.name} className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: item.color }}></span>
                    <span className="text-slate-400">{item.name}:</span>
                    <span className="text-white font-bold">{item.value}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Charts Row 2: Top Reserved Medicines & Top Pharmacies */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="glass-panel p-6 rounded-3xl border border-slate-800 space-y-4">
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <BarChart3 className="w-4 h-4 text-emerald-400" />
                Most Reserved Emergency Medicines
              </h3>
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={topMedicines}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                    <XAxis dataKey="medicineName" stroke="#64748b" tick={{ fontSize: 11 }} />
                    <YAxis stroke="#64748b" allowDecimals={false} />
                    <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '12px', fontSize: '12px' }} />
                    <Bar dataKey="reservationCount" fill="#10b981" radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div className="glass-panel p-6 rounded-3xl border border-slate-800 space-y-4">
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <Building2 className="w-4 h-4 text-blue-400" />
                Top Performing Pharmacy Hubs
              </h3>
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={topPharmacies}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                    <XAxis dataKey="pharmacyName" stroke="#64748b" tick={{ fontSize: 11 }} />
                    <YAxis stroke="#64748b" allowDecimals={false} />
                    <Tooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '12px', fontSize: '12px' }} />
                    <Bar dataKey="reservationCount" fill="#3b82f6" radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
};
