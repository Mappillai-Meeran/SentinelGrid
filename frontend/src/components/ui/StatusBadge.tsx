import React from 'react';

export type StatusType =
  | 'PENDING'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'EXPIRED'
  | 'IN_STOCK'
  | 'LOW_STOCK'
  | 'CRITICAL'
  | 'OUT_OF_STOCK'
  | string;

interface StatusBadgeProps {
  status: StatusType;
  className?: string;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, className = '' }) => {
  let badgeStyle = 'bg-slate-100 text-slate-700 border-slate-200';
  let label = status;

  switch (status) {
    case 'PENDING':
      badgeStyle = 'bg-amber-50 text-amber-700 border-amber-200/80';
      label = 'Pending Hold';
      break;
    case 'CONFIRMED':
      badgeStyle = 'bg-emerald-50 text-emerald-700 border-emerald-200/80';
      label = 'Confirmed Pickup';
      break;
    case 'CANCELLED':
      badgeStyle = 'bg-slate-100 text-slate-600 border-slate-200';
      label = 'Cancelled';
      break;
    case 'EXPIRED':
      badgeStyle = 'bg-red-50 text-red-700 border-red-200/80';
      label = 'Expired';
      break;
    case 'IN_STOCK':
    case 'AVAILABLE':
      badgeStyle = 'bg-emerald-50 text-emerald-700 border-emerald-200/80';
      label = 'In Stock';
      break;
    case 'LOW_STOCK':
      badgeStyle = 'bg-amber-50 text-amber-700 border-amber-200/80';
      label = 'Low Stock';
      break;
    case 'CRITICAL':
    case 'OUT_OF_STOCK':
      badgeStyle = 'bg-red-50 text-red-700 border-red-200/80';
      label = status === 'CRITICAL' ? 'Critical Stock' : 'Out of Stock';
      break;
  }

  return (
    <span
      className={`inline-flex items-center px-2.5 py-1 rounded-md text-[10px] font-bold border uppercase tracking-wide ${badgeStyle} ${className}`}
    >
      {label}
    </span>
  );
};
