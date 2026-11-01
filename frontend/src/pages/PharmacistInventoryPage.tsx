import React, { useEffect, useState } from 'react';
import { pharmacyService, reservationService } from '../api/services';
import type { InventoryItem, Reservation } from '../types';
import {
  Plus,
  Trash2,
  ShieldCheck,
  RefreshCw,
  AlertCircle,
  Clock,
  User,
  FileCheck2,
  Inbox,
  Boxes,
} from 'lucide-react';
import { TableSkeleton } from '../components/ui/Skeleton';
import { StatusBadge } from '../components/ui/StatusBadge';
import { EmptyState } from '../components/ui/EmptyState';
import { useToast } from '../components/ui/Toast';
import { useAuthStore } from '../store/authStore';

export const PharmacistInventoryPage: React.FC = () => {
  const { user } = useAuthStore();
  const pharmacyId = user?.pharmacyId;
  const [activeTab, setActiveTab] = useState<'queue' | 'inventory'>('queue');
  const { showToast, showError } = useToast();

  // Inventory States
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [loadingInventory, setLoadingInventory] = useState(true);

  // Reservation Queue States
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [resStatusFilter, setResStatusFilter] = useState<string>('PENDING');
  const [loadingReservations, setLoadingReservations] = useState(true);

  // Add Stock Modal States
  const [medicineId, setMedicineId] = useState<number>(1);
  const [quantity, setQuantity] = useState<number>(50);
  const [showAddModal, setShowAddModal] = useState(false);

  // Quick ID Lookup States
  const [confirmResId, setConfirmResId] = useState<string>('');
  const [lookupRes, setLookupRes] = useState<Reservation | null>(null);
  const [lookingUp, setLookingUp] = useState<boolean>(false);
  const [lookupNotice, setLookupNotice] = useState<string>('');

  const fetchInventory = async (silent = false) => {
    if (!pharmacyId) {
      setLoadingInventory(false);
      return;
    }
    if (!silent) setLoadingInventory(true);
    try {
      const data = await pharmacyService.getPharmacyInventory(pharmacyId);
      setInventory(data);
    } catch (err) {
      console.error('Failed to fetch inventory', err);
      showError(err, 'Failed to fetch inventory');
    } finally {
      if (!silent) setLoadingInventory(false);
    }
  };

  const fetchReservations = async (status = resStatusFilter, silent = false) => {
    if (!pharmacyId) {
      setLoadingReservations(false);
      return;
    }
    if (!silent) setLoadingReservations(true);
    try {
      const data = await pharmacyService.getPharmacyReservations(pharmacyId, status);
      setReservations(data);
    } catch (err) {
      console.error('Failed to fetch pharmacy reservations', err);
      showError(err, 'Failed to fetch incoming reservations');
    } finally {
      if (!silent) setLoadingReservations(false);
    }
  };

  useEffect(() => {
    if (pharmacyId) {
      fetchInventory();
      fetchReservations(resStatusFilter);
    } else {
      setLoadingInventory(false);
      setLoadingReservations(false);
    }
  }, [pharmacyId, resStatusFilter]);

  // Client-side countdown ticker for pending holds in the queue
  useEffect(() => {
    const timer = setInterval(() => {
      setReservations((prev) =>
        prev.map((res) => {
          if (res.status !== 'PENDING') return res;
          const nextSeconds = (res.remainingSeconds !== undefined ? res.remainingSeconds : 1800) - 1;
          if (nextSeconds <= 0) {
            return {
              ...res,
              status: 'EXPIRED',
              remainingSeconds: 0,
              remainingMinutes: 0,
            };
          }
          return {
            ...res,
            remainingSeconds: nextSeconds,
            remainingMinutes: Math.floor(nextSeconds / 60),
          };
        })
      );
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  const refreshAll = () => {
    fetchInventory();
    fetchReservations(resStatusFilter);
  };

  const handleAddStock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pharmacyId) {
      showToast('No pharmacy branch assigned to this account.', 'error');
      return;
    }
    if (quantity <= 0) {
      showToast('Quantity must be greater than zero.', 'warning');
      return;
    }
    try {
      await pharmacyService.addInventory(pharmacyId, Number(medicineId), Number(quantity));
      showToast('Stock record created successfully!', 'success');
      setShowAddModal(false);
      fetchInventory(true);
    } catch (err: any) {
      showError(err, 'Failed to create stock record');
    }
  };

  const handleAdjustDelta = async (inventoryId: number, delta: number) => {
    if (!pharmacyId) return;
    try {
      await pharmacyService.adjustInventory(pharmacyId, inventoryId, delta);
      showToast(`Inventory #${inventoryId} adjusted by ${delta > 0 ? '+' : ''}${delta} units.`, 'success');
      fetchInventory(true);
    } catch (err: any) {
      showError(err, 'Failed to adjust stock');
    }
  };

  const handleDeleteInventory = async (inventoryId: number) => {
    if (!pharmacyId) return;
    if (!window.confirm('Are you sure you want to delete this stock entry?')) return;
    try {
      await pharmacyService.deleteInventory(pharmacyId, inventoryId);
      showToast(`Inventory stock record #${inventoryId} deleted successfully.`, 'success');
      fetchInventory(true);
    } catch (err: any) {
      showError(err, 'Failed to delete inventory record');
    }
  };

  const handleLookupReservation = async () => {
    if (!confirmResId) return;
    setLookingUp(true);
    setLookupNotice('');
    try {
      const res = await reservationService.getReservation(Number(confirmResId));
      setLookupRes(res);
    } catch (err: any) {
      const status = err?.response?.status;
      if (status === 403) {
        setLookupNotice(`Reservation #${confirmResId} is not assigned to ${user?.pharmacyName || 'your pharmacy branch'}. Only staff at the pickup pharmacy can view, verify, or confirm it.`);
      } else {
        showError(err, 'Failed to find reservation');
      }
      setLookupRes(null);
    } finally {
      setLookingUp(false);
    }
  };

  const handleVerifyPrescriptionAction = async (resId: number) => {
    try {
      const updated = await reservationService.verifyPrescription(resId);
      showToast(`Prescription verified for Reservation #${resId}! Ready for pickup.`, 'success');
      if (lookupRes && lookupRes.id === resId) {
        setLookupRes(updated);
      }
      fetchReservations(resStatusFilter, true);
    } catch (err: any) {
      showError(err, 'Failed to verify prescription');
    }
  };

  const handleConfirmPickupAction = async (resId: number) => {
    try {
      await reservationService.confirmPickup(resId);
      showToast(`Pickup completed for Reservation #${resId}! Stock ledger updated.`, 'success');
      if (lookupRes && lookupRes.id === resId) {
        setLookupRes(null);
        setConfirmResId('');
      }
      fetchReservations(resStatusFilter, true);
      fetchInventory(true);
    } catch (err: any) {
      showError(err, 'Failed to complete pickup');
    }
  };

  const handleCancelReservationAction = async (resId: number) => {
    if (!window.confirm(`Cancel Reservation #${resId} and return reserved stock to inventory?`)) return;
    try {
      await reservationService.cancelReservation(resId);
      showToast(`Reservation #${resId} cancelled. Stock restored to active inventory.`, 'success');
      if (lookupRes && lookupRes.id === resId) {
        setLookupRes(null);
        setConfirmResId('');
      }
      fetchReservations(resStatusFilter, true);
      fetchInventory(true);
    } catch (err: any) {
      showError(err, 'Failed to cancel reservation');
    }
  };

  if (!pharmacyId) {
    return (
      <div className="bg-amber-50 border border-amber-200 rounded-2xl p-8 text-center max-w-xl mx-auto space-y-3 mt-8">
        <AlertCircle className="w-10 h-10 text-amber-600 mx-auto" />
        <h2 className="text-base font-bold text-amber-900">No Pharmacy Assigned</h2>
        <p className="text-xs text-amber-800">
          Your account is not linked to an active pharmacy branch. Please contact an administrator to assign your account to a pharmacy.
        </p>
      </div>
    );
  }

  const pendingCount = reservations.filter((r) => r.status === 'PENDING').length;

  return (
    <div className="space-y-6">
      {/* Top Controls Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-black text-slate-900 tracking-tight">Pharmacist Workspace</h2>
          <p className="text-xs text-slate-500 font-medium">
            Branch #{pharmacyId} {user?.pharmacyName ? `• ${user.pharmacyName}` : ''}
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={refreshAll}
            className="p-2.5 bg-white hover:bg-slate-50 text-slate-700 rounded-xl border border-slate-200 transition-colors shadow-xs"
            title="Refresh All"
          >
            <RefreshCw className="w-4 h-4" />
          </button>
          <button
            onClick={() => setShowAddModal(true)}
            className="px-4 py-2.5 bg-blue-600 hover:bg-blue-500 text-white font-semibold rounded-xl text-xs flex items-center gap-2 shadow-xs transition-colors"
          >
            <Plus className="w-4 h-4" /> Add Stock Record
          </button>
        </div>
      </div>

      {/* Main Mode Tabs */}
      <div className="flex gap-2 border-b border-slate-200 pb-2">
        <button
          onClick={() => setActiveTab('queue')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold transition-all ${
            activeTab === 'queue'
              ? 'bg-blue-600 text-white shadow-md shadow-blue-600/15'
              : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-50'
          }`}
        >
          <Inbox className="w-4 h-4" />
          <span>Incoming Reservations Queue</span>
          {pendingCount > 0 && (
            <span
              className={`px-2 py-0.5 rounded-full text-[10px] font-black ${
                activeTab === 'queue' ? 'bg-white text-blue-700' : 'bg-amber-100 text-amber-800'
              }`}
            >
              {pendingCount} PENDING
            </span>
          )}
        </button>

        <button
          onClick={() => setActiveTab('inventory')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold transition-all ${
            activeTab === 'inventory'
              ? 'bg-blue-600 text-white shadow-md shadow-blue-600/15'
              : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-50'
          }`}
        >
          <Boxes className="w-4 h-4" />
          <span>Stock Inventory</span>
          <span
            className={`px-2 py-0.5 rounded-full text-[10px] font-black ${
              activeTab === 'inventory' ? 'bg-white text-blue-700' : 'bg-slate-100 text-slate-700'
            }`}
          >
            {inventory.length} LINES
          </span>
        </button>
      </div>

      {/* Quick Pickup ID Lookup Widget */}
      <div className="bg-white p-5 rounded-2xl border border-slate-200 shadow-xs space-y-3">
        <h3 className="text-xs font-bold text-slate-900 uppercase tracking-wider flex items-center gap-2">
          <ShieldCheck className="w-4 h-4 text-blue-600 shrink-0" />
          Quick Reservation Lookup by ID
        </h3>
        <div className="flex flex-col sm:flex-row gap-3 max-w-xl">
          <input
            type="number"
            min={1}
            value={confirmResId}
            onChange={(e) => {
              setConfirmResId(e.target.value);
              setLookupRes(null);
            }}
            placeholder="Enter customer ticket ID (e.g. 104)"
            className="flex-1 bg-slate-50 border border-slate-200 rounded-xl px-4 py-2 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
          />
          <button
            type="button"
            onClick={handleLookupReservation}
            disabled={!confirmResId || lookingUp}
            className="px-4 py-2 bg-blue-50 hover:bg-blue-100 text-blue-700 font-bold rounded-xl text-xs transition-colors disabled:opacity-50"
          >
            {lookingUp ? 'Searching...' : 'Find Hold'}
          </button>
          {lookupRes && lookupRes.status === 'PENDING' && (
            <button
              type="button"
              onClick={() => handleConfirmPickupAction(lookupRes.id)}
              disabled={lookupRes.medicine.requiresPrescription && !lookupRes.prescriptionVerified}
              className="px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-xl text-xs transition-colors shadow-xs disabled:opacity-40"
            >
              Confirm Pickup
            </button>
          )}
        </div>

        {lookupRes && (
          <div className="p-4 bg-slate-50 border border-slate-200 rounded-xl space-y-3 text-xs animate-fade-in">
            <div className="flex justify-between items-center border-b border-slate-200 pb-2">
              <div className="flex items-center gap-2">
                <span className="font-bold text-slate-900 text-sm">#{lookupRes.id} • {lookupRes.medicine.name}</span>
                <span className="text-slate-500">({lookupRes.quantity} unit for patient "{lookupRes.patientUsername}")</span>
              </div>
              <StatusBadge status={lookupRes.status} />
            </div>

            {lookupRes.medicine.requiresPrescription ? (
              <div className="space-y-2">
                <div className="flex justify-between items-center">
                  <span className="font-semibold text-slate-700">Prescription Verification Status:</span>
                  <span
                    className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                      lookupRes.prescriptionVerified
                        ? 'bg-emerald-100 text-emerald-800'
                        : 'bg-rose-100 text-rose-800'
                    }`}
                  >
                    {lookupRes.prescriptionVerified
                      ? `✓ Verified by ${lookupRes.prescriptionVerifiedBy || 'Pharmacist'}`
                      : '⚠ Prescription Inspection Required'}
                  </span>
                </div>
                {lookupRes.prescriptionNumber && (
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-[11px] text-slate-600 bg-white p-2.5 rounded-lg border border-slate-200">
                    <div><span className="font-semibold">Rx #:</span> {lookupRes.prescriptionNumber}</div>
                    <div><span className="font-semibold">Dr:</span> {lookupRes.doctorName || 'N/A'}</div>
                    <div><span className="font-semibold">Dr Reg #:</span> {lookupRes.doctorRegNumber || 'N/A'}</div>
                  </div>
                )}
                {!lookupRes.prescriptionVerified && lookupRes.status === 'PENDING' && (
                  <button
                    type="button"
                    onClick={() => handleVerifyPrescriptionAction(lookupRes.id)}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl text-xs shadow-xs transition-colors"
                  >
                    Physically Inspect &amp; Verify Prescription
                  </button>
                )}
              </div>
            ) : (
              <div className="text-emerald-700 font-semibold text-[11px]">
                ✓ Over-the-counter medicine — No prescription required for dispensing.
              </div>
            )}
          </div>
        )}

        {lookupNotice && (
          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs font-medium text-amber-900">
            {lookupNotice}
          </div>
        )}
      </div>

      {/* TAB 1: INCOMING RESERVATIONS QUEUE */}
      {activeTab === 'queue' && (
        <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden space-y-4 p-6">
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
            <div>
              <h3 className="font-black text-slate-950 text-sm">Live Branch Reservation Queue</h3>
              <p className="text-xs text-slate-500">Holds placed by emergency patients awaiting counter pickup</p>
            </div>

            {/* Status Filter Buttons */}
            <div className="flex gap-1.5 bg-slate-100 p-1 rounded-xl">
              {['ALL', 'PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED'].map((st) => (
                <button
                  key={st}
                  onClick={() => setResStatusFilter(st)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all ${
                    resStatusFilter === st
                      ? 'bg-white text-blue-700 shadow-xs'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  {st}
                </button>
              ))}
            </div>
          </div>

          {loadingReservations ? (
            <TableSkeleton rows={4} cols={6} />
          ) : reservations.length === 0 ? (
            <EmptyState
              title={`No ${resStatusFilter === 'ALL' ? '' : resStatusFilter} Reservations`}
              description={`There are currently no ${resStatusFilter.toLowerCase()} holds recorded for this branch.`}
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-50 text-slate-500 font-bold uppercase tracking-wider border-b border-slate-200/80">
                  <tr>
                    <th className="p-3.5">ID</th>
                    <th className="p-3.5">Patient</th>
                    <th className="p-3.5">Medicine &amp; Qty</th>
                    <th className="p-3.5">Hold Timer / Status</th>
                    <th className="p-3.5">Prescription Check</th>
                    <th className="p-3.5 text-right pr-4">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
                  {reservations.map((res) => {
                    const isRx = res.medicine?.requiresPrescription;
                    const isVerified = res.prescriptionVerified;
                    const canConfirm = res.status === 'PENDING' && (!isRx || isVerified);
                    const needsVerify = res.status === 'PENDING' && isRx && !isVerified;

                    return (
                      <tr key={res.id} className="hover:bg-slate-50/70 transition-colors">
                        <td className="p-3.5 font-bold text-slate-500">#{res.id}</td>
                        <td className="p-3.5">
                          <span className="inline-flex items-center gap-1.5 font-bold text-slate-900">
                            <User className="w-3.5 h-3.5 text-slate-400" />
                            {res.patientUsername || `Patient #${res.patientId}`}
                          </span>
                        </td>
                        <td className="p-3.5">
                          <div className="font-semibold text-slate-900">{res.medicine?.name}</div>
                          <div className="text-[11px] text-slate-500">
                            {res.medicine?.brand} • {res.quantity} {res.quantity === 1 ? 'unit' : 'units'}
                          </div>
                        </td>
                        <td className="p-3.5">
                          {res.status === 'PENDING' ? (
                            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-[11px] font-bold bg-amber-50 text-amber-800 border border-amber-200">
                              <Clock className="w-3.5 h-3.5 animate-spin" />
                              {res.remainingMinutes ?? 30}m remaining
                            </span>
                          ) : (
                            <StatusBadge status={res.status} />
                          )}
                        </td>
                        <td className="p-3.5">
                          {!isRx ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-600">
                              OTC (No Rx)
                            </span>
                          ) : isVerified ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-800 border border-emerald-200">
                              <FileCheck2 className="w-3 h-3 text-emerald-600" />
                              Verified
                            </span>
                          ) : (
                            <div className="space-y-1">
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-rose-50 text-rose-800 border border-rose-200">
                                ⚠ Verify Required
                              </span>
                              {res.prescriptionNumber && (
                                <div className="text-[10px] text-slate-500 font-mono">
                                  Rx: {res.prescriptionNumber}
                                </div>
                              )}
                            </div>
                          )}
                        </td>
                        <td className="p-3.5 text-right pr-4 space-x-1.5">
                          {needsVerify && (
                            <button
                              onClick={() => handleVerifyPrescriptionAction(res.id)}
                              className="px-2.5 py-1.5 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg text-xs transition-colors shadow-xs"
                              title="Physically inspect and verify patient's paper prescription"
                            >
                              Verify Rx
                            </button>
                          )}
                          {canConfirm && (
                            <button
                              onClick={() => handleConfirmPickupAction(res.id)}
                              className="px-2.5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold rounded-lg text-xs transition-colors shadow-xs"
                              title="Confirm patient pickup and deduct inventory"
                            >
                              Confirm Pickup
                            </button>
                          )}
                          {res.status === 'PENDING' && (
                            <button
                              onClick={() => handleCancelReservationAction(res.id)}
                              className="px-2 py-1.5 bg-slate-100 hover:bg-red-50 text-slate-600 hover:text-red-600 font-bold rounded-lg text-xs transition-colors"
                              title="Cancel hold and restore reserved stock"
                            >
                              Cancel
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* TAB 2: STOCK INVENTORY MANAGEMENT */}
      {activeTab === 'inventory' && (
        <div className="bg-white rounded-2xl border border-slate-200 shadow-xs overflow-hidden">
          <div className="p-6 border-b border-slate-100 flex justify-between items-center bg-slate-50/50">
            <div>
              <h3 className="font-black text-slate-950 text-sm">Live Inventory Stock Ledger</h3>
              <p className="text-xs text-slate-500">Manage medicine inventory levels and active holds</p>
            </div>
            <span className="text-xs text-slate-400 font-bold uppercase tracking-wider">
              {inventory.length} Medicine Lines Monitored
            </span>
          </div>

          {loadingInventory ? (
            <TableSkeleton rows={5} cols={6} />
          ) : inventory.length === 0 ? (
            <div className="p-12 text-center text-xs text-slate-400 font-semibold">
              No stock records found for this pharmacy. Click "Add Stock Record" to begin.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-slate-50 text-slate-500 font-bold uppercase tracking-wider border-b border-slate-200/80">
                  <tr>
                    <th className="p-4">ID</th>
                    <th className="p-4">Medicine Name</th>
                    <th className="p-4">Total Stock</th>
                    <th className="p-4">Reserved Stock</th>
                    <th className="p-4">Available</th>
                    <th className="p-4 text-right pr-6">Quick Stock Adjustment</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 font-medium text-slate-700">
                  {inventory.map((item) => (
                    <tr key={item.id} className="hover:bg-slate-50/60 transition-colors">
                      <td className="p-4 font-bold text-slate-400">#{item.id}</td>
                      <td className="p-4">
                        <div className="font-semibold text-slate-900">{item.medicineName}</div>
                        <div className="text-[10px] text-slate-400">
                          {item.requiresPrescription ? 'Prescription Required' : 'Over-the-Counter'}
                        </div>
                      </td>
                      <td className="p-4 text-slate-500 font-bold">{item.quantity} units</td>
                      <td className="p-4 text-amber-600 font-bold">{item.reservedQuantity} reserved</td>
                      <td className="p-4 font-black text-emerald-600">{item.availableQuantity} available</td>
                      <td className="p-4 text-right pr-6 space-x-1.5">
                        <button
                          onClick={() => handleAdjustDelta(item.id, +10)}
                          className="px-2.5 py-1 bg-slate-100 hover:bg-slate-200/80 text-slate-700 font-bold rounded text-[11px] transition-colors border border-slate-200/60"
                          title="Add 10 units"
                        >
                          +10
                        </button>
                        <button
                          onClick={() => handleAdjustDelta(item.id, -5)}
                          className="px-2.5 py-1 bg-slate-100 hover:bg-slate-200/80 text-slate-700 font-bold rounded text-[11px] transition-colors border border-slate-200/60"
                          title="Deduct 5 units"
                        >
                          -5
                        </button>
                        <button
                          onClick={() => handleDeleteInventory(item.id)}
                          className="p-1.5 bg-red-50 hover:bg-red-100 text-red-600 rounded transition-colors border border-red-200"
                          title="Delete stock entry"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Add Stock Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="bg-white rounded-2xl p-6 max-w-md w-full border border-slate-200 shadow-2xl space-y-4">
            <h3 className="font-bold text-slate-900 text-base">Add New Inventory Stock</h3>
            <form onSubmit={handleAddStock} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">
                  Medicine Catalog Selection
                </label>
                <select
                  value={medicineId}
                  onChange={(e) => setMedicineId(Number(e.target.value))}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl p-2.5 text-xs text-slate-800 font-semibold focus:outline-none focus:border-blue-500"
                >
                  <option value={1}>1 - Remdesivir 100mg Injection (Rx)</option>
                  <option value={2}>2 - Tocilizumab 400mg (Rx)</option>
                  <option value={3}>3 - Enoxaparin 40mg Injection (Rx)</option>
                  <option value={4}>4 - Favipiravir 400mg (Rx)</option>
                  <option value={5}>5 - Oxygen Cylinder 10L (OTC)</option>
                  <option value={6}>6 - Anti-Venom Polyvalent Injection (Rx)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-500 uppercase tracking-wider mb-1.5">
                  Initial Physical Quantity
                </label>
                <input
                  type="number"
                  min={1}
                  required
                  value={quantity}
                  onChange={(e) => setQuantity(Number(e.target.value))}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl p-2.5 text-xs text-slate-800 font-semibold focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-xl text-xs font-bold shadow-xs transition-colors"
                >
                  Save Stock Record
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
