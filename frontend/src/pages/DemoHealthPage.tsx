import React, { useEffect, useState } from 'react';
import { Activity, CheckCircle2, KeyRound, Server } from 'lucide-react';
import { dashboardService } from '../api/services';
import type { DashboardSummary } from '../types';

const demoAccounts = [
  ['Patient', 'patient1', 'password123'],
  ['Pharmacist', 'pharmacist1', 'password123'],
  ['Manager', 'manager1', 'password123'],
  ['Admin', 'admin1', 'password123'],
];

export const DemoHealthPage: React.FC = () => {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [status, setStatus] = useState<'checking' | 'online' | 'offline'>('checking');

  useEffect(() => {
    const load = async () => {
      try {
        const data = await dashboardService.getSummary();
        setSummary(data);
        setStatus('online');
      } catch (err) {
        setStatus('offline');
      }
    };

    load();
  }, []);

  return (
    <div className="space-y-6">
      <div className="bg-white border border-slate-200 rounded-3xl p-6 flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-black text-slate-950 flex items-center gap-2">
            <Server className="w-5 h-5 text-blue-600" />
            Demo Health
          </h2>
          <p className="text-sm text-slate-500 mt-1">Credentials, seed data, and backend status for a smooth product demo.</p>
        </div>
        <div className={`inline-flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold border ${
          status === 'online'
            ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
            : status === 'offline'
              ? 'bg-red-50 text-red-700 border-red-200'
              : 'bg-amber-50 text-amber-700 border-amber-200'
        }`}>
          <Activity className="w-4 h-4" />
          {status === 'online' ? 'Backend Online' : status === 'offline' ? 'Backend Offline' : 'Checking'}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <section className="bg-white border border-slate-200 rounded-3xl p-6 space-y-4">
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
            <KeyRound className="w-4 h-4 text-blue-600" />
            Demo Credentials
          </h3>
          <div className="space-y-2">
            {demoAccounts.map(([role, username, password]) => (
              <div key={username} className="grid grid-cols-3 gap-3 rounded-2xl bg-slate-50 border border-slate-200 p-3 text-xs">
                <span className="font-bold text-slate-900">{role}</span>
                <span className="font-mono text-slate-700">{username}</span>
                <span className="font-mono text-slate-700">{password}</span>
              </div>
            ))}
          </div>
        </section>

        <section className="bg-white border border-slate-200 rounded-3xl p-6 space-y-4">
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-600" />
            Seed Data Status
          </h3>
          <div className="grid grid-cols-2 gap-3">
            {[
              ['Users', summary?.totalUsers ?? '-'],
              ['Pharmacies', summary?.totalPharmacies ?? '-'],
              ['Medicines', summary?.totalMedicines ?? '-'],
              ['Reservations', summary?.totalReservations ?? '-'],
            ].map(([label, value]) => (
              <div key={label} className="rounded-2xl bg-slate-50 border border-slate-200 p-4">
                <p className="text-2xl font-black text-slate-950">{value}</p>
                <p className="text-[10px] font-bold text-slate-500 uppercase">{label}</p>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
};
