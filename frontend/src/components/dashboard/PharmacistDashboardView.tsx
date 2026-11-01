import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowRight,
  CheckCircle2,
  Clock,
  FileCheck2,
  FileSpreadsheet,
  Package,
  RefreshCw,
  Search,
} from 'lucide-react';
import { pharmacyService, reservationService } from '../../api/services';
import type { InventoryItem, Reservation } from '../../types';
import { useAuthStore } from '../../store/authStore';
import { useToast } from '../ui/Toast';

const statusClass = (status: Reservation['status']) => {
  if (status === 'CONFIRMED') return 'bg-emerald-50 text-emerald-700';
  if (status === 'PENDING') return 'bg-amber-50 text-amber-700';
  if (status === 'CANCELLED') return 'bg-slate-100 text-slate-600';
  return 'bg-red-50 text-red-700';
};

export const PharmacistDashboardView: React.FC = () => {
  const { user } = useAuthStore();
  const { showToast, showError } = useToast();
  const pharmacyId = user?.pharmacyId;
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [ticketInput, setTicketInput] = useState('');
  const [lookupResult, setLookupResult] = useState<Reservation | null>(null);
  const [lookupLoading, setLookupLoading] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);
  const [tableFilter, setTableFilter] = useState<'ALL' | 'LOW' | 'OUT' | 'AMPLE'>('ALL');
  const [searchTerm, setSearchTerm] = useState('');

  const loadBranchData = async (silent = false) => {
    if (!pharmacyId) {
      setLoading(false);
      return;
    }
    if (silent) setRefreshing(true);
    else setLoading(true);
    try {
      const [reservationData, inventoryData] = await Promise.all([
        pharmacyService.getPharmacyReservations(pharmacyId),
        pharmacyService.getPharmacyInventory(pharmacyId),
      ]);
      setReservations(reservationData);
      setInventory(inventoryData);
    } catch (error) {
      console.error('Failed to load branch operations data', error);
      showError(error, 'Failed to load branch operations data');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadBranchData();
  }, [pharmacyId]);

  const handleLookup = async (event?: React.FormEvent) => {
    event?.preventDefault();
    const reservationId = ticketInput.trim().replace(/^#?RES-?/i, '');
    if (!reservationId || Number.isNaN(Number(reservationId))) {
      showToast('Please enter a valid reservation ID', 'error');
      return;
    }
    setLookupLoading(true);
    setLookupResult(null);
    try {
      const reservation = await reservationService.getReservation(Number(reservationId));
      if (reservation.pharmacy?.id !== pharmacyId && user?.role !== 'ADMIN') {
        showToast(`Reservation #RES-${reservationId} belongs to another branch`, 'error');
        return;
      }
      setLookupResult(reservation);
    } catch (error) {
      showError(error, `Reservation #RES-${reservationId} not found`);
    } finally {
      setLookupLoading(false);
    }
  };

  const handleVerifyPrescription = async (reservationId: number) => {
    setActionLoadingId(reservationId);
    try {
      await reservationService.verifyPrescription(reservationId);
      showToast(`Prescription for #RES-${reservationId} verified`, 'success');
      await loadBranchData(true);
      setLookupResult((current) => current?.id === reservationId ? { ...current, prescriptionVerified: true } : current);
    } catch (error) {
      showError(error, 'Prescription verification failed');
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleConfirmPickup = async (reservationId: number) => {
    setActionLoadingId(reservationId);
    try {
      await reservationService.confirmPickup(reservationId);
      showToast(`Pickup confirmed for #RES-${reservationId}`, 'success');
      await loadBranchData(true);
      setLookupResult((current) => current?.id === reservationId ? { ...current, status: 'CONFIRMED' } : current);
    } catch (error) {
      showError(error, 'Pickup confirmation failed');
    } finally {
      setActionLoadingId(null);
    }
  };

  const pendingQueue = reservations.filter((reservation) => reservation.status === 'PENDING');
  const readyForPickup = pendingQueue.filter((reservation) => !reservation.medicine?.requiresPrescription || reservation.prescriptionVerified);
  const outOfStockItems = inventory.filter((item) => item.availableQuantity === 0);
  const lowStockItems = inventory.filter((item) => item.availableQuantity > 0 && item.availableQuantity < 10);
  const ampleStockItems = inventory.filter((item) => item.availableQuantity >= 10);
  const totalStockUnits = inventory.reduce((total, item) => total + item.quantity, 0);
  const filteredInventory = inventory.filter((item) => {
    const matchesSearch = item.medicineName.toLowerCase().includes(searchTerm.toLowerCase()) || item.medicineCategory?.toLowerCase().includes(searchTerm.toLowerCase());
    if (!matchesSearch) return false;
    if (tableFilter === 'OUT') return item.availableQuantity === 0;
    if (tableFilter === 'LOW') return item.availableQuantity > 0 && item.availableQuantity < 10;
    if (tableFilter === 'AMPLE') return item.availableQuantity >= 10;
    return true;
  });

  const exportInventoryCSV = () => {
    if (inventory.length === 0) return;
    const headers = 'ShelfID,MedicineName,Category,InStock,Reserved,Available,Status\n';
    const rows = inventory.map((item) => `"S-${item.medicineId}","${item.medicineName}","${item.medicineCategory ?? 'RX'}","${item.quantity}","${item.reservedQuantity}","${item.availableQuantity}","${item.availableQuantity === 0 ? 'OUT_OF_STOCK' : item.availableQuantity < 10 ? 'LOW_STOCK' : 'SUFFICIENT'}"`).join('\n');
    const link = document.createElement('a');
    link.href = encodeURI(`data:text/csv;charset=utf-8,${headers}${rows}`);
    link.download = `Branch_${pharmacyId}_Inventory_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('Inventory report exported', 'success');
  };

  if (!pharmacyId) {
    return <div className="rounded-lg border border-amber-200 bg-amber-50 p-6 text-amber-900"><h3 className="flex items-center gap-2 text-sm font-bold"><AlertTriangle className="h-5 w-5 text-amber-600" />No assigned pharmacy branch</h3><p className="mt-2 text-xs text-amber-800">Your account is not linked to a branch. Please contact an administrator.</p></div>;
  }

  const stockCards = [
    { key: 'OUT' as const, label: 'Out of stock', value: outOfStockItems.length, detail: 'Medicine lines depleted', tone: 'red' },
    { key: 'LOW' as const, label: 'Low stock', value: lowStockItems.length, detail: 'Below 10 available units', tone: 'amber' },
    { key: 'AMPLE' as const, label: 'Sufficient stock', value: ampleStockItems.length, detail: 'At least 10 available units', tone: 'emerald' },
  ] as const;
  const cardTone: Record<'red' | 'amber' | 'emerald', string> = { red: 'bg-red-50 text-red-700 ring-red-100', amber: 'bg-amber-50 text-amber-700 ring-amber-100', emerald: 'bg-emerald-50 text-emerald-700 ring-emerald-100' };

  return (
    <div className="mx-auto max-w-[1440px] space-y-6 pb-10">
      <header className="flex flex-col gap-4 border-b border-slate-200 pb-6 lg:flex-row lg:items-end lg:justify-between">
        <div><p className="text-xs font-bold uppercase tracking-[0.14em] text-emerald-700">Pharmacy operations</p><h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">Branch overview</h1><p className="mt-2 max-w-2xl text-sm text-slate-500">{user?.pharmacyName ?? `Branch #${pharmacyId}`} · reservations, prescription checks, and stock readiness in one workspace.</p></div>
        <div className="flex flex-wrap items-center gap-2"><button onClick={() => loadBranchData(true)} disabled={refreshing} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3.5 py-2.5 text-xs font-semibold text-slate-700 shadow-xs hover:bg-slate-50 disabled:opacity-60"><RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} />Refresh data</button><button onClick={exportInventoryCSV} className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3.5 py-2.5 text-xs font-semibold text-slate-700 shadow-xs hover:bg-slate-50"><FileSpreadsheet className="h-4 w-4 text-emerald-600" />Export inventory</button><Link to="/inventory" className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3.5 py-2.5 text-xs font-semibold text-white shadow-sm shadow-blue-600/20 hover:bg-blue-700">Manage inventory<ArrowRight className="h-4 w-4" /></Link></div>
      </header>

      <section className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {stockCards.map((card) => <button key={card.key} onClick={() => setTableFilter(card.key)} className={`rounded-lg border border-slate-200 bg-white p-5 text-left shadow-xs transition-colors hover:border-slate-300 ${tableFilter === card.key ? 'ring-2 ring-blue-100' : ''}`}><div className="flex items-start justify-between"><div><p className="text-xs font-semibold text-slate-500">{card.label}</p><p className="mt-2 text-3xl font-bold tracking-tight text-slate-950">{loading ? '—' : card.value}</p></div><span className={`mt-0.5 h-3 w-3 rounded-full ring-4 ${cardTone[card.tone]}`} /></div><p className="mt-3 text-xs text-slate-500">{card.detail}</p></button>)}
        <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs"><div className="flex items-start justify-between"><div><p className="text-xs font-semibold text-slate-500">Reservation queue</p><p className="mt-2 text-3xl font-bold tracking-tight text-slate-950">{loading ? '—' : pendingQueue.length}</p></div><span className="grid h-9 w-9 place-items-center rounded-lg bg-blue-50 text-blue-700 ring-1 ring-blue-100"><Clock className="h-4 w-4" /></span></div><p className="mt-3 text-xs text-slate-500">{readyForPickup.length} ready for pickup</p></div>
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs"><div className="flex flex-col gap-3 border-b border-slate-100 pb-4 sm:flex-row sm:items-center sm:justify-between"><div><h2 className="flex items-center gap-2 text-sm font-bold text-slate-950"><Search className="h-4 w-4 text-blue-600" />Reservation lookup</h2><p className="mt-1 text-xs text-slate-500">Find a reservation before verifying a prescription or confirming collection.</p></div><span className="text-xs font-medium text-slate-400">Branch #{pharmacyId}</span></div><form onSubmit={handleLookup} className="mt-4 flex flex-col gap-2 sm:flex-row"><input type="text" value={ticketInput} onChange={(event) => setTicketInput(event.target.value)} placeholder="Enter reservation ID, for example RES-501" className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-slate-50 px-3.5 py-2.5 text-sm text-slate-800 outline-none placeholder:text-slate-400 focus:border-blue-500 focus:bg-white" /><button type="submit" disabled={lookupLoading} className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-60">{lookupLoading ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}Look up</button></form>
        {lookupResult && <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-4"><div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between"><div><div className="flex flex-wrap items-center gap-2"><span className="text-xs font-bold text-blue-700">#RES-{lookupResult.id}</span><span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${statusClass(lookupResult.status)}`}>{lookupResult.status}</span>{lookupResult.medicine?.requiresPrescription ? <span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${lookupResult.prescriptionVerified ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'}`}>{lookupResult.prescriptionVerified ? 'Prescription verified' : 'Prescription review needed'}</span> : <span className="rounded-full bg-slate-200 px-2.5 py-1 text-[10px] font-bold text-slate-600">OTC</span>}</div><p className="mt-3 text-sm font-semibold text-slate-900">{lookupResult.medicine?.name} · {lookupResult.quantity} unit(s)</p><p className="mt-1 text-xs text-slate-500">Patient: <span className="font-semibold text-slate-700">{lookupResult.patientUsername}</span>{lookupResult.prescriptionNumber && ` · Rx: ${lookupResult.prescriptionNumber}`}{lookupResult.doctorName && ` · Dr. ${lookupResult.doctorName}`}</p></div><div className="flex flex-wrap items-center gap-2">{lookupResult.status === 'PENDING' && lookupResult.medicine?.requiresPrescription && !lookupResult.prescriptionVerified && <button onClick={() => handleVerifyPrescription(lookupResult.id)} disabled={actionLoadingId === lookupResult.id} className="inline-flex items-center gap-1.5 rounded-lg border border-blue-200 bg-white px-3 py-2 text-xs font-semibold text-blue-700 hover:bg-blue-50 disabled:opacity-60"><FileCheck2 className="h-4 w-4" />Verify prescription</button>}{lookupResult.status === 'PENDING' && (!lookupResult.medicine?.requiresPrescription || lookupResult.prescriptionVerified) && <button onClick={() => handleConfirmPickup(lookupResult.id)} disabled={actionLoadingId === lookupResult.id} className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold text-white hover:bg-emerald-700 disabled:opacity-60"><CheckCircle2 className="h-4 w-4" />Confirm pickup</button>}<button onClick={() => setLookupResult(null)} className="rounded-lg px-3 py-2 text-xs font-semibold text-slate-500 hover:bg-white hover:text-slate-700">Clear</button></div></div></div>}
      </section>

      <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xs"><div className="flex flex-col gap-4 border-b border-slate-200 px-5 py-4 lg:flex-row lg:items-center lg:justify-between"><div><h2 className="flex items-center gap-2 text-sm font-bold text-slate-950"><Package className="h-4 w-4 text-blue-600" />Inventory readiness</h2><p className="mt-1 text-xs text-slate-500">Showing {filteredInventory.length} of {inventory.length} medicine lines · {totalStockUnits} total units on hand.</p></div><div className="flex flex-col gap-2 sm:flex-row"><div className="relative"><Search className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400" /><input type="search" value={searchTerm} onChange={(event) => setSearchTerm(event.target.value)} placeholder="Filter medicines" className="w-full rounded-lg border border-slate-200 bg-slate-50 py-2 pl-8 pr-3 text-xs text-slate-700 outline-none placeholder:text-slate-400 focus:border-blue-500 sm:w-44" /></div><div className="flex rounded-lg border border-slate-200 bg-slate-50 p-1 text-[11px] font-semibold"><button onClick={() => setTableFilter('ALL')} className={`rounded-md px-2.5 py-1.5 ${tableFilter === 'ALL' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500'}`}>All</button><button onClick={() => setTableFilter('OUT')} className={`rounded-md px-2.5 py-1.5 ${tableFilter === 'OUT' ? 'bg-red-50 text-red-700' : 'text-slate-500'}`}>Out</button><button onClick={() => setTableFilter('LOW')} className={`rounded-md px-2.5 py-1.5 ${tableFilter === 'LOW' ? 'bg-amber-50 text-amber-700' : 'text-slate-500'}`}>Low</button></div></div></div>
        <div className="overflow-x-auto"><table className="w-full min-w-[760px] text-left text-sm"><thead className="bg-slate-50 text-[11px] font-bold uppercase tracking-wide text-slate-500"><tr><th className="px-5 py-3">Medicine</th><th className="px-5 py-3">Form</th><th className="px-5 py-3">Category</th><th className="px-5 py-3 text-right">Available</th><th className="px-5 py-3 min-w-[180px]">Availability</th><th className="px-5 py-3 text-right">Reserved</th><th className="px-5 py-3">Status</th></tr></thead><tbody className="divide-y divide-slate-100">{loading ? Array.from({ length: 5 }).map((_, index) => <tr key={index}><td colSpan={7} className="px-5 py-4"><div className="h-4 w-full animate-pulse rounded bg-slate-100" /></td></tr>) : filteredInventory.length === 0 ? <tr><td colSpan={7} className="px-5 py-12 text-center text-xs text-slate-400">No medicine stock records match this filter.</td></tr> : filteredInventory.map((item) => { const ratio = item.quantity > 0 ? Math.min(100, Math.max(0, Math.round((item.availableQuantity / item.quantity) * 100))) : 0; const out = item.availableQuantity === 0; const low = !out && item.availableQuantity < 10; const barColor = out ? 'bg-red-500' : low ? 'bg-amber-500' : 'bg-emerald-500'; return <tr key={item.id} className="hover:bg-slate-50/70"><td className="px-5 py-3.5"><p className="font-semibold text-slate-900">{item.medicineName}</p><p className="mt-0.5 text-[11px] text-slate-400">ID #{item.medicineId}{item.medicineBrand ? ` · ${item.medicineBrand}` : ''}</p></td><td className="px-5 py-3.5 text-xs text-slate-600">{item.medicineDosage || (item.requiresPrescription ? 'Prescription required' : 'Standard')}</td><td className="px-5 py-3.5"><span className="rounded bg-slate-100 px-2 py-1 text-[10px] font-bold text-slate-600">{item.medicineCategory ?? 'RX'}</span></td><td className="px-5 py-3.5 text-right font-bold text-slate-900">{item.availableQuantity}</td><td className="px-5 py-3.5"><div className="flex items-center justify-between text-[10px] text-slate-500"><span>{ratio}% available</span><span>{item.availableQuantity}/{item.quantity}</span></div><div className="mt-1.5 h-2 overflow-hidden rounded-full bg-slate-100"><div className={`h-full rounded-full ${barColor}`} style={{ width: `${ratio}%` }} /></div></td><td className="px-5 py-3.5 text-right text-xs text-slate-600">{item.reservedQuantity}</td><td className="px-5 py-3.5"><span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${out ? 'bg-red-50 text-red-700' : low ? 'bg-amber-50 text-amber-700' : 'bg-emerald-50 text-emerald-700'}`}>{out ? 'Out of stock' : low ? 'Low stock' : 'Ready'}</span></td></tr>; })}</tbody></table></div>
      </section>
    </div>
  );
};

export default PharmacistDashboardView;
