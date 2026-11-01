import React from 'react';

interface SkeletonProps {
  className?: string;
}

export const Skeleton: React.FC<SkeletonProps> = ({ className = '' }) => {
  return (
    <div
      className={`bg-slate-200/70 rounded-lg animate-pulse motion-reduce:animate-none ${className}`}
    />
  );
};

export const KpiSkeleton: React.FC = () => {
  return (
    <div className="grid grid-cols-2 lg:grid-cols-6 gap-4">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="glass-panel p-5 rounded-2xl space-y-3 border border-slate-200/80">
          <div className="flex justify-between items-center">
            <Skeleton className="h-3.5 w-16" />
            <Skeleton className="h-4 w-4 rounded-full" />
          </div>
          <Skeleton className="h-8 w-12" />
          <Skeleton className="h-2.5 w-20" />
        </div>
      ))}
    </div>
  );
};

export const TableSkeleton: React.FC<{ rows?: number; cols?: number }> = ({ rows = 5, cols = 5 }) => {
  return (
    <div className="space-y-4 p-4">
      {Array.from({ length: rows }).map((_, r) => (
        <div key={r} className="flex gap-4 items-center justify-between border-b border-slate-100 pb-4 last:border-0 last:pb-0">
          {Array.from({ length: cols }).map((_, c) => (
            <Skeleton key={c} className={`h-4 ${c === 0 ? 'w-28' : 'w-16'}`} />
          ))}
        </div>
      ))}
    </div>
  );
};

export const CardSkeleton: React.FC = () => {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      {Array.from({ length: 6 }).map((_, i) => (
        <div key={i} className="glass-panel p-5 rounded-2xl border border-slate-200/80 flex flex-col justify-between space-y-4">
          <div className="space-y-3">
            <Skeleton className="h-4.5 w-20" />
            <Skeleton className="h-6 w-3/4 animate-pulse" />
            <Skeleton className="h-4 w-1/2" />
          </div>
          <div className="border-t border-slate-100 pt-3 flex justify-between items-center">
            <div className="space-y-1">
              <Skeleton className="h-3 w-12" />
              <Skeleton className="h-5 w-16" />
            </div>
            <Skeleton className="h-8 w-24 rounded-xl" />
          </div>
        </div>
      ))}
    </div>
  );
};

export const ChartSkeleton: React.FC = () => {
  return (
    <div className="glass-panel p-6 rounded-3xl border border-slate-200/80 space-y-4 h-64 flex flex-col justify-between">
      <div className="flex items-center gap-2">
        <Skeleton className="h-4.5 w-4 rounded-full" />
        <Skeleton className="h-4 w-40" />
      </div>
      <div className="flex-1 flex items-end gap-3 pt-6 px-4">
        <Skeleton className="h-1/3 flex-1 rounded-t-lg" />
        <Skeleton className="h-2/3 flex-1 rounded-t-lg" />
        <Skeleton className="h-1/2 flex-1 rounded-t-lg" />
        <Skeleton className="h-3/4 flex-1 rounded-t-lg" />
        <Skeleton className="h-5/6 flex-1 rounded-t-lg" />
      </div>
    </div>
  );
};

export const ActivityFeedSkeleton: React.FC = () => {
  return (
    <div className="space-y-3">
      {Array.from({ length: 4 }).map((_, i) => (
        <div key={i} className="p-3.5 bg-slate-50/80 rounded-2xl border border-slate-250/60 space-y-2">
          <div className="flex justify-between">
            <Skeleton className="h-4.5 w-24" />
            <Skeleton className="h-3.5 w-12" />
          </div>
          <Skeleton className="h-4 w-full" />
          <div className="flex justify-between pt-1">
            <Skeleton className="h-3.5 w-16" />
            <Skeleton className="h-3.5 w-20" />
          </div>
        </div>
      ))}
    </div>
  );
};
