import React from 'react';
import type { LucideIcon } from 'lucide-react';

interface KpiCardProps {
  label: string;
  value: string | number;
  subtext?: string;
  icon: LucideIcon;
  variant?: 'default' | 'amber' | 'emerald' | 'red' | 'purple' | 'blue';
}

export const KpiCard: React.FC<KpiCardProps> = ({
  label,
  value,
  subtext,
  icon: Icon,
  variant = 'default',
}) => {
  let borderClass = 'border-slate-200/80';
  let iconColor = 'text-blue-500';
  let valueColor = 'text-slate-900';

  if (variant === 'amber') {
    borderClass = 'border-amber-200/80';
    iconColor = 'text-amber-500';
    valueColor = 'text-amber-600';
  } else if (variant === 'emerald') {
    borderClass = 'border-emerald-200/80';
    iconColor = 'text-emerald-500';
    valueColor = 'text-emerald-600';
  } else if (variant === 'red') {
    borderClass = 'border-red-200/80';
    iconColor = 'text-red-500';
    valueColor = 'text-red-600';
  } else if (variant === 'purple') {
    borderClass = 'border-purple-200/80';
    iconColor = 'text-purple-500';
    valueColor = 'text-purple-600';
  } else if (variant === 'blue') {
    borderClass = 'border-blue-200/80';
    iconColor = 'text-blue-500';
    valueColor = 'text-blue-600';
  }

  return (
    <div className={`glass-panel p-5 rounded-2xl space-y-2 border ${borderClass}`}>
      <div className="flex justify-between items-center text-slate-500">
        <span className="text-[11px] font-bold uppercase tracking-wider">{label}</span>
        <Icon className={`w-4 h-4 ${iconColor}`} />
      </div>
      <p className={`text-2xl font-black ${valueColor}`}>{value}</p>
      {subtext && <span className="text-[10px] text-slate-450 block font-medium">{subtext}</span>}
    </div>
  );
};
