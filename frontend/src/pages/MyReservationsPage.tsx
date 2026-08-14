import React, { useEffect, useState } from 'react';
import api from '../api/client';
import type { Reservation } from '../types';
import { Clock, CheckCircle2, XCircle, AlertOctagon } from 'lucide-react';

export const MyReservationsPage: React.FC = () => {
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [loading, setLoading] = useState(true);
  const [actionMessage, setActionMessage] = useState('');

  const fetchReservations = async () => {
    setLoading(true);
    try {
      const url = statusFilter === 'ALL' ? '/reservations/my' : `/reservations/my?status=${statusFilter}`;
      const res = await api.get(url);
      setReservations(res.data.data || []);
    } catch (err) {
      console.error('Failed to fetch patient reservations', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReservations();
  }, [statusFilter]);

  const handleCancel = async (id: number) => {
    try {
      await api.post(`/reservations/${id}/cancel`);
      setActionMessage(`Reservation #${id} has been cancelled and stock released.`);
      fetchReservations();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to cancel reservation');
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PENDING':
        return <span className="px-3 py-1 bg-amber-100 text-amber-800 border border-amber-200 font-bold rounded-full text-xs flex items-center gap-1"><Clock className="w-3.5 h-3.5" /> PENDING</span>;
      case 'CONFIRMED':
        return <span className="px-3 py-1 bg-emerald-100 text-emerald-800 border border-emerald-200 font-bold rounded-full text-xs flex items-center gap-1"><CheckCircle2 className="w-3.5 h-3.5" /> CONFIRMED</span>;
      case 'CANCELLED':
        return <span className="px-3 py-1 bg-slate-100 text-slate-700 border border-slate-200 font-bold rounded-full text-xs flex items-center gap-1"><XCircle className="w-3.5 h-3.5" /> CANCELLED</span>;
      case 'EXPIRED':
        return <span className="px-3 py-1 bg-red-100 text-red-800 border border-red-200 font-bold rounded-full text-xs flex items-center gap-1"><AlertOctagon className="w-3.5 h-3.5" /> EXPIRED</span>;
      default:
        return null;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-slate-800">My Medicine Reservations</h2>
          <p className="text-xs text-slate-500">Track active holdings, confirm pickup status, or cancel holds</p>
        </div>

        {/* Status Filter Buttons */}
        <div className="flex gap-2 bg-white p-1.5 rounded-xl border border-slate-200 shadow-xs">
          {['ALL', 'PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED'].map((st) => (
            <button
              key={st}
              onClick={() => setStatusFilter(st)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                statusFilter === st
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'text-slate-600 hover:bg-slate-100'
              }`}
            >
              {st}
            </button>
          ))}
        </div>
      </div>

      {actionMessage && (
        <div className="p-4 bg-blue-50 border border-blue-200 text-blue-800 rounded-xl text-xs font-semibold">
          {actionMessage}
        </div>
      )}

      {loading ? (
        <div className="text-center py-12 text-slate-500 font-medium text-sm">Loading reservations...</div>
      ) : reservations.length === 0 ? (
        <div className="bg-white p-12 text-center rounded-2xl border border-slate-200">
          <Clock className="w-12 h-12 text-slate-300 mx-auto mb-3" />
          <h3 className="font-bold text-slate-700">No Reservations Found</h3>
          <p className="text-xs text-slate-400 mt-1">There are no reservation records matching this filter.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {reservations.map((res) => (
            <div key={res.id} className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs space-y-4">
              <div className="flex justify-between items-start">
                <div>
                  <span className="text-[10px] font-bold text-slate-400">RESERVATION #{res.id}</span>
                  <h3 className="font-bold text-slate-900 text-base">{res.medicine.name}</h3>
                  <p className="text-xs text-slate-500">{res.pharmacy.name} • {res.pharmacy.city}</p>
                </div>
                {getStatusBadge(res.status)}
              </div>

              <div className="bg-slate-50 p-4 rounded-xl text-xs space-y-2 border border-slate-100">
                <div className="flex justify-between text-slate-600">
                  <span>Reserved Quantity:</span>
                  <span className="font-bold text-slate-900">{res.quantity} units</span>
                </div>
                <div className="flex justify-between text-slate-600">
                  <span>Category:</span>
                  <span className="font-semibold text-slate-800">{res.medicine.category}</span>
                </div>
                {res.status === 'PENDING' && (
                  <div className="flex justify-between text-amber-700 font-medium border-t border-slate-200/60 pt-2">
                    <span>Remaining Hold Time:</span>
                    <span className="font-bold">{res.remainingMinutes ?? 30} mins ({res.remainingSeconds ?? 1800}s)</span>
                  </div>
                )}
              </div>

              {res.status === 'PENDING' && (
                <button
                  onClick={() => handleCancel(res.id)}
                  className="w-full py-2.5 bg-red-50 hover:bg-red-100 text-red-600 font-semibold rounded-xl text-xs border border-red-200 transition-colors"
                >
                  Cancel Hold &amp; Release Stock
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
