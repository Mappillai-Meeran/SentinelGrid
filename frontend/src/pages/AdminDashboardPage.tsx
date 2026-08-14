import React, { useEffect, useState } from 'react';
import api from '../api/client';
import type { DashboardSummary, TopMedicine, TopPharmacy, LowStockItem } from '../types';
import {
  Users,
  Building2,
  Pill,
  TrendingUp,
  ShieldAlert
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
  CartesianGrid
} from 'recharts';

export const AdminDashboardPage: React.FC = () => {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [topMedicines, setTopMedicines] = useState<TopMedicine[]>([]);
  const [topPharmacies, setTopPharmacies] = useState<TopPharmacy[]>([]);
  const [lowStock, setLowStock] = useState<LowStockItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        const [sumRes, topMedRes, topPharmRes, lowStockRes] = await Promise.all([
          api.get('/admin/dashboard/summary'),
          api.get('/admin/dashboard/top-medicines?limit=5'),
          api.get('/admin/dashboard/pharmacies/top?limit=5'),
          api.get('/admin/dashboard/inventory/low-stock?threshold=10'),
        ]);

        setSummary(sumRes.data.data);
        setTopMedicines(topMedRes.data.data || []);
        setTopPharmacies(topPharmRes.data.data || []);
        setLowStock(lowStockRes.data.data || []);
      } catch (err) {
        console.error('Failed to load dashboard data', err);
      } finally {
        setLoading(false);
      }
    };

    fetchDashboardData();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64 text-slate-500 font-medium">
        Loading SentinelGrid Analytics...
      </div>
    );
  }

  const reservationPieData = summary
    ? [
        { name: 'Pending', value: summary.pendingReservations, color: '#3B82F6' },
        { name: 'Confirmed', value: summary.confirmedReservations, color: '#10B981' },
        { name: 'Expired', value: summary.expiredReservations, color: '#F59E0B' },
        { name: 'Cancelled', value: summary.cancelledReservations, color: '#EF4444' },
      ]
    : [];

  return (
    <div className="space-y-8">
      {/* Metric Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs flex items-center gap-4">
          <div className="p-3.5 bg-blue-50 text-blue-600 rounded-xl">
            <Users className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Total System Users</p>
            <h3 className="text-2xl font-bold text-slate-900 mt-1">{summary?.totalUsers || 0}</h3>
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs flex items-center gap-4">
          <div className="p-3.5 bg-indigo-50 text-indigo-600 rounded-xl">
            <Building2 className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Active Pharmacies</p>
            <h3 className="text-2xl font-bold text-slate-900 mt-1">{summary?.totalPharmacies || 0}</h3>
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs flex items-center gap-4">
          <div className="p-3.5 bg-emerald-50 text-emerald-600 rounded-xl">
            <Pill className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Total Inventory Stock</p>
            <h3 className="text-2xl font-bold text-slate-900 mt-1">{summary?.totalInventoryUnits || 0} units</h3>
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs flex items-center gap-4">
          <div className="p-3.5 bg-purple-50 text-purple-600 rounded-xl">
            <TrendingUp className="w-6 h-6" />
          </div>
          <div>
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Total Reservations</p>
            <h3 className="text-2xl font-bold text-slate-900 mt-1">{summary?.totalReservations || 0}</h3>
          </div>
        </div>
      </div>

      {/* Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Reservation Status Distribution */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
          <h3 className="text-base font-bold text-slate-800 mb-4">Reservation Lifecycle Status</h3>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={reservationPieData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={80}
                  paddingAngle={5}
                  dataKey="value"
                >
                  {reservationPieData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="grid grid-cols-2 gap-2 mt-4 pt-4 border-t border-slate-100 text-xs font-medium">
            {reservationPieData.map((item) => (
              <div key={item.name} className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full" style={{ backgroundColor: item.color }}></span>
                <span className="text-slate-600">{item.name}:</span>
                <span className="font-bold text-slate-800">{item.value}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Top Reserved Medicines */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs lg:col-span-2">
          <h3 className="text-base font-bold text-slate-800 mb-4">Most Reserved Emergency Medicines</h3>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={topMedicines}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="medicineName" tick={{ fontSize: 12 }} />
                <YAxis allowDecimals={false} />
                <Tooltip />
                <Bar dataKey="reservationCount" fill="#2563EB" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Low Stock Table & Top Pharmacies */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs lg:col-span-2">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-base font-bold text-slate-800 flex items-center gap-2">
              <ShieldAlert className="w-5 h-5 text-amber-500" />
              Low Stock Alerts (&lt; 10 Available Units)
            </h3>
          </div>
          {lowStock.length === 0 ? (
            <p className="text-sm text-slate-500 py-4">All pharmacy stock levels are sufficient.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-50 text-slate-500 font-semibold border-b border-slate-200">
                  <tr>
                    <th className="p-3">Pharmacy</th>
                    <th className="p-3">Medicine</th>
                    <th className="p-3">Total Quantity</th>
                    <th className="p-3">Reserved</th>
                    <th className="p-3">Available</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 font-medium">
                  {lowStock.map((item) => (
                    <tr key={item.inventoryId} className="hover:bg-slate-50">
                      <td className="p-3 font-semibold text-slate-900">{item.pharmacyName}</td>
                      <td className="p-3 text-slate-700">{item.medicineName}</td>
                      <td className="p-3">{item.quantity}</td>
                      <td className="p-3 text-amber-600 font-bold">{item.reservedQuantity}</td>
                      <td className="p-3">
                        <span className="inline-flex px-2 py-0.5 rounded text-[11px] font-bold bg-red-50 text-red-600 border border-red-200">
                          {item.availableQuantity} left
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Top Pharmacies */}
        <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs">
          <h3 className="text-base font-bold text-slate-800 mb-4">Top Performing Pharmacies</h3>
          <div className="space-y-4">
            {topPharmacies.map((pharmacy, idx) => (
              <div key={idx} className="flex items-center justify-between p-3 bg-slate-50 rounded-xl">
                <div>
                  <p className="font-semibold text-xs text-slate-900">{pharmacy.pharmacyName}</p>
                  <p className="text-[10px] text-slate-500">Hub Partner</p>
                </div>
                <span className="px-2.5 py-1 bg-blue-100 text-blue-700 font-bold text-xs rounded-full">
                  {pharmacy.reservationCount} orders
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
