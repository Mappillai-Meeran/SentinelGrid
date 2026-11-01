import React from 'react';
import { Link } from 'react-router-dom';
import { Activity, ArrowRight, Clock, PackageCheck, Search, ShieldCheck } from 'lucide-react';
import { SectionHeader } from '../components/ui/SectionHeader';

const workflowSteps = [
  ['Patient searches medicine', 'Search live emergency stock by medicine and city.', Search],
  ['Patient reserves hold', 'A short stock hold prevents double-booking.', Clock],
  ['Pharmacist verifies pickup', 'Counter verification deducts stock from inventory.', ShieldCheck],
  ['Audit remains traceable', 'Every sensitive action is logged for review.', PackageCheck],
];

export const EmergencyOperationsPage: React.FC = () => {
  return (
    <div className="space-y-6">
      <SectionHeader
        title="Emergency Operations"
        subtitle="A focused handoff view for the core emergency medicine workflow"
        icon={Activity}
      />

      <section className="bg-white border border-slate-200 rounded-3xl p-6">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          {workflowSteps.map(([title, detail, Icon], index) => (
            <div key={title as string} className="rounded-2xl bg-slate-50 border border-slate-200 p-5">
              <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 border border-blue-100 flex items-center justify-center mb-4">
                <Icon className="w-5 h-5" />
              </div>
              <p className="text-[10px] font-bold text-slate-400 uppercase">Step {index + 1}</p>
              <h3 className="font-black text-slate-950 mt-1">{title as string}</h3>
              <p className="text-xs text-slate-500 mt-2">{detail as string}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Link to="/search" className="group bg-blue-600 hover:bg-blue-700 text-white rounded-3xl p-6 transition-colors">
          <Search className="w-8 h-8 text-blue-100" />
          <h3 className="text-xl font-black mt-4">Patient Medicine Search</h3>
          <p className="text-sm text-blue-100 mt-2">Find medicine, select pharmacy, reserve stock, and start directions.</p>
          <span className="inline-flex items-center gap-2 mt-5 text-sm font-bold">
            Open Search <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
          </span>
        </Link>

        <Link to="/inventory" className="group bg-white hover:bg-slate-50 border border-slate-200 rounded-3xl p-6 transition-colors">
          <ShieldCheck className="w-8 h-8 text-emerald-600" />
          <h3 className="text-xl font-black text-slate-950 mt-4">Pharmacist Verification</h3>
          <p className="text-sm text-slate-500 mt-2">Verify reservation pickup and keep emergency inventory accurate.</p>
          <span className="inline-flex items-center gap-2 mt-5 text-sm font-bold text-blue-600">
            Open Inventory <ArrowRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
          </span>
        </Link>
      </section>
    </div>
  );
};
