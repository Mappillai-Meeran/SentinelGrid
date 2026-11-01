import React, { useEffect, useState } from 'react';
import { dashboardService } from '../api/services';
import type { AuditLog } from '../types';
import { FileText, Search, Filter, FileSpreadsheet } from 'lucide-react';
import { GlassCard } from '../components/ui/GlassCard';
import { SectionHeader } from '../components/ui/SectionHeader';
import { EmptyState } from '../components/ui/EmptyState';

export const AuditPage: React.FC = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedAction, setSelectedAction] = useState('ALL');

  useEffect(() => {
    const fetchAuditLogs = async () => {
      setLoading(true);
      try {
        const data = await dashboardService.getAuditLogs();
        setLogs(data);
      } catch (err) {
        console.error('Failed to fetch audit logs', err);
      } finally {
        setLoading(false);
      }
    };

    fetchAuditLogs();
  }, []);

  const filteredLogs = logs.filter((log) => {
    const matchesAction = selectedAction === 'ALL' || log.action === selectedAction;
    const matchesSearch =
      log.performedBy.toLowerCase().includes(searchTerm.toLowerCase()) ||
      log.details.toLowerCase().includes(searchTerm.toLowerCase()) ||
      log.entityName.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesAction && matchesSearch;
  });

  const exportCSV = () => {
    if (filteredLogs.length === 0) return;
    const headers = 'ID,Timestamp,Action,Entity,EntityID,PerformedBy,Details\n';
    const rows = filteredLogs
      .map(
        (l) =>
          `"${l.id}","${l.timestamp}","${l.action}","${l.entityName}","${l.entityId}","${l.performedBy}","${l.details.replace(/"/g, '""')}"`
      )
      .join('\n');

    const csvContent = 'data:text/csv;charset=utf-8,' + headers + rows;
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `SentinelGrid_AuditLogs_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const actionTypes = ['ALL', 'RESERVATION_CREATED', 'RESERVATION_CONFIRMED', 'RESERVATION_CANCELLED', 'INVENTORY_UPDATED', 'INVENTORY_CREATED'];

  return (
    <div className="space-y-6">
      <SectionHeader
        title="Audit & Compliance Center"
        subtitle="Immutable ledger recording all state transitions, inventory modifications, and user actions"
        icon={FileText}
      >
        <button
          onClick={exportCSV}
          className="px-4 py-2.5 bg-white hover:bg-slate-50 text-slate-700 font-bold rounded-xl text-xs flex items-center gap-2 border border-slate-200 transition-colors shadow-xs"
        >
          <FileSpreadsheet className="w-4 h-4 text-emerald-600" />
          Export Audit Trail CSV
        </button>
      </SectionHeader>

      {/* Filters Bar */}
      <GlassCard className="space-y-4 p-5 rounded-2xl">
        <div className="flex flex-col md:flex-row gap-4">
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-3.5" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search audit details, actor username, or entity..."
              className="w-full bg-slate-50 border border-slate-200 rounded-xl pl-10 pr-4 py-2.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-blue-500"
            />
          </div>

          <div className="flex items-center gap-2 overflow-x-auto pb-1 md:pb-0">
            <Filter className="w-4 h-4 text-slate-400 shrink-0" />
            {actionTypes.map((act) => (
              <button
                key={act}
                onClick={() => setSelectedAction(act)}
                className={`px-3 py-1.5 rounded-lg text-xs font-semibold shrink-0 transition-colors ${
                  selectedAction === act
                    ? 'bg-blue-600 text-white shadow-xs'
                    : 'bg-slate-50 text-slate-500 border border-slate-200 hover:text-slate-900'
                }`}
              >
                {act}
              </button>
            ))}
          </div>
        </div>
      </GlassCard>

      {/* Table */}
      <GlassCard className="overflow-hidden shadow-xs p-0">
        {loading ? (
          <div className="p-12 text-center text-xs text-slate-400 font-medium">Loading audit logs...</div>
        ) : filteredLogs.length === 0 ? (
          <EmptyState
            title="No Audit Log Matches"
            description="No system audit log records match your current search and action filter criteria."
            icon={FileText}
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50/90 text-slate-500 font-bold uppercase tracking-wider border-b border-slate-200/80">
                <tr>
                  <th className="p-4">Log ID</th>
                  <th className="p-4">Timestamp</th>
                  <th className="p-4">Action</th>
                  <th className="p-4">Entity</th>
                  <th className="p-4">Performed By</th>
                  <th className="p-4">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
                {filteredLogs.map((log) => (
                  <tr key={log.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="p-4 font-bold text-slate-400">#{log.id}</td>
                    <td className="p-4 text-slate-600">
                      {new Date(log.timestamp).toLocaleString()}
                    </td>
                    <td className="p-4">
                      <span className="inline-flex px-2.5 py-1 rounded-md text-[10px] font-bold bg-blue-50 text-blue-600 border border-blue-200/60">
                        {log.action}
                      </span>
                    </td>
                    <td className="p-4 text-slate-700">
                      {log.entityName} <span className="text-slate-400">#{log.entityId}</span>
                    </td>
                    <td className="p-4 text-slate-900 font-bold">{log.performedBy}</td>
                    <td className="p-4 text-slate-600">{log.details}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </GlassCard>
    </div>
  );
};
