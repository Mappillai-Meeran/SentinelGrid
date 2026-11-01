import React from 'react';
import { Check, X, Clock, AlertTriangle } from 'lucide-react';

interface LifecycleTrackerProps {
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED';
}

export const LifecycleTracker: React.FC<LifecycleTrackerProps> = ({ status }) => {
  const steps = [
    {
      label: 'Stock Held',
      desc: 'Medicine locked in pool',
      isCompleted: true,
      isActive: false,
      color: 'bg-emerald-500 border-emerald-500 text-white',
      icon: Check,
    },
    {
      label: 'Verification',
      desc: status === 'PENDING' ? 'Awaiting verification' : status === 'CONFIRMED' ? 'Verified & Completed' : 'Bypassed',
      isCompleted: status === 'CONFIRMED',
      isActive: status === 'PENDING',
      isFailed: status === 'CANCELLED' || status === 'EXPIRED',
      color: status === 'CONFIRMED'
        ? 'bg-emerald-500 border-emerald-500 text-white'
        : status === 'PENDING'
          ? 'bg-blue-50 border-blue-500 text-blue-600 animate-pulse'
          : 'bg-slate-100 border-slate-300 text-slate-400',
      icon: status === 'CONFIRMED' ? Check : Clock,
    },
    {
      label: status === 'CANCELLED' ? 'Cancelled' : status === 'EXPIRED' ? 'Expired' : 'Deducted',
      desc: status === 'PENDING' ? 'Pending final outcome' : status === 'CONFIRMED' ? 'Stock updated' : 'Released to pool',
      isCompleted: status !== 'PENDING',
      isActive: false,
      isFailed: status === 'CANCELLED' || status === 'EXPIRED',
      color: status === 'CONFIRMED'
        ? 'bg-emerald-500 border-emerald-500 text-white'
        : status === 'CANCELLED'
          ? 'bg-red-500 border-red-500 text-white'
          : status === 'EXPIRED'
            ? 'bg-amber-500 border-amber-500 text-white'
            : 'bg-slate-150 border-slate-200 text-slate-350',
      icon: status === 'CONFIRMED'
        ? Check
        : status === 'CANCELLED'
          ? X
          : status === 'EXPIRED'
            ? AlertTriangle
            : Clock,
    },
  ];

  return (
    <div className="pt-2 relative">
      {/* Connecting Line */}
      <div className="absolute top-6 left-[15%] right-[15%] h-0.5 bg-slate-200/80 -z-10">
        <div
          className="h-full bg-emerald-500 transition-all duration-500"
          style={{
            width: status === 'CONFIRMED' ? '100%' : '50%',
            backgroundColor: status === 'CANCELLED' ? '#ef4444' : status === 'EXPIRED' ? '#f59e0b' : '#10b981',
          }}
        />
      </div>

      <div className="flex items-start justify-between">
        {/* Step Nodes */}
        {steps.map((step, idx) => {
          const Icon = step.icon;
          return (
            <div key={idx} className="flex flex-col items-center text-center max-w-[120px] flex-1">
              <div
                className={`w-8 h-8 rounded-full border-2 flex items-center justify-center font-bold text-xs bg-white transition-all z-10 ${step.color}`}
              >
                {step.isFailed ? (
                  <X className="w-4 h-4 text-slate-400" />
                ) : (
                  <Icon className="w-4 h-4" />
                )}
              </div>
              <p className="text-[11px] font-bold text-slate-900 mt-2">{step.label}</p>
              <p className="text-[9px] text-slate-450 mt-0.5 leading-snug">{step.desc}</p>
            </div>
          );
        })}
      </div>
    </div>
  );
};
