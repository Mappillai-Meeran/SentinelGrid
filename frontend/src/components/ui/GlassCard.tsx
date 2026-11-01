import React from 'react';

interface GlassCardProps {
  children: React.ReactNode;
  className?: string;
  variant?: 'default' | 'red' | 'blue' | 'purple' | 'amber' | 'emerald';
}

export const GlassCard: React.FC<GlassCardProps> = ({
  children,
  className = '',
  variant = 'default',
}) => {
  let variantStyle = 'glass-panel border-slate-200/80';

  if (variant === 'red') {
    variantStyle = 'glass-card-red border-red-200/80';
  } else if (variant === 'blue') {
    variantStyle = 'glass-card-blue border-blue-200/80';
  } else if (variant === 'purple') {
    variantStyle = 'glass-card-purple border-purple-200/80';
  } else if (variant === 'amber') {
    variantStyle = 'bg-gradient-to-br from-amber-50/80 via-white/80 to-amber-50/40 backdrop-blur-md border-amber-200/80';
  } else if (variant === 'emerald') {
    variantStyle = 'bg-gradient-to-br from-emerald-50/80 via-white/80 to-emerald-50/40 backdrop-blur-md border-emerald-200/80';
  }

  return (
    <div className={`rounded-3xl p-6 border shadow-xs transition-all ${variantStyle} ${className}`}>
      {children}
    </div>
  );
};
