import React, { useEffect, useState } from 'react';
import { reservationService } from '../api/services';
import type { Reservation } from '../types';
import { Clock, Navigation } from 'lucide-react';
import { LifecycleTracker } from '../components/ui/LifecycleTracker';
import { useToast } from '../components/ui/Toast';
import { GlassCard } from '../components/ui/GlassCard';
import { SectionHeader } from '../components/ui/SectionHeader';
import { StatusBadge } from '../components/ui/StatusBadge';
import { EmptyState } from '../components/ui/EmptyState';
import { buildGoogleMapsDirectionsUrl } from '../utils/maps';
import { ReservationCountdown } from '../components/ui/ReservationCountdown';

export const MyReservationsPage: React.FC = () => {
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [loading, setLoading] = useState(true);
  const { showToast, showError } = useToast();

  const fetchReservations = async () => {
    setLoading(true);
    try {
      const data = await reservationService.getMyReservations(statusFilter);
      setReservations(data);
    } catch (err) {
      console.error('Failed to fetch patient reservations', err);
      showError(err, 'Failed to fetch reservations');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReservations();
  }, [statusFilter]);

  const handleCancel = async (id: number) => {
    if (!window.confirm(`Are you sure you want to cancel hold for Reservation #${id}?`)) return;
    try {
      await reservationService.cancelReservation(id);
      showToast(`Reservation #${id} has been cancelled and stock released to the availability pool.`, 'success');
      fetchReservations();
    } catch (err: any) {
      showError(err, 'Failed to cancel reservation');
    }
  };

  return (
    <div className="space-y-6">
      <SectionHeader
        title="My Medicine Reservations"
        subtitle="Track active holdings, confirm pickup status, or cancel holds"
        icon={Clock}
      >
        {/* Status Filter Buttons */}
        <div className="flex gap-1.5 bg-white/70 p-1.5 rounded-xl border border-slate-200 shadow-xs backdrop-blur-md">
          {['ALL', 'PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED'].map((st) => (
            <button
              key={st}
              onClick={() => setStatusFilter(st)}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
                statusFilter === st
                  ? 'bg-blue-600 text-white shadow-xs'
                  : 'text-slate-600 hover:bg-slate-100/80'
              }`}
            >
              {st}
            </button>
          ))}
        </div>
      </SectionHeader>

      {loading ? (
        <div className="text-center py-12 text-slate-400 font-medium text-sm">Loading reservations...</div>
      ) : reservations.length === 0 ? (
        <EmptyState
          title="No Reservations Found"
          description="There are no active or historical medicine reservation records matching this filter."
          icon={Clock}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {reservations.map((res) => (
            <GlassCard key={res.id} className="space-y-5 flex flex-col justify-between">
              <div className="space-y-4">
                <div className="flex justify-between items-start">
                  <div>
                    <span className="text-[10px] font-bold text-slate-400">RESERVATION #{res.id}</span>
                    <h3 className="font-bold text-slate-900 text-base mt-0.5">{res.medicine.name}</h3>
                    <p className="text-xs text-slate-500">{res.pharmacy.name} • {res.pharmacy.city}</p>
                  </div>
                  <StatusBadge status={res.status} />
                </div>

                {/* Stepper Timeline Tracker */}
                <div className="py-2 border-t border-b border-slate-100">
                  <LifecycleTracker status={res.status} />
                </div>

                <div className="bg-slate-50 p-4 rounded-xl text-xs space-y-2 border border-slate-200/60 font-sans">
                  <div className="flex justify-between text-slate-600">
                    <span>Reserved Quantity:</span>
                    <span className="font-bold text-slate-900">{res.quantity} units</span>
                  </div>
                  <div className="flex justify-between text-slate-600">
                    <span>Category:</span>
                    <span className="font-semibold text-slate-800">{res.medicine.category}</span>
                  </div>
                  {res.medicine.requiresPrescription && (
                    <div className="border-t border-slate-200/40 pt-2 space-y-1">
                      <div className="flex justify-between items-center text-slate-600">
                        <span>Prescription Status:</span>
                        <span
                          className={`font-bold px-2 py-0.5 rounded text-[10px] ${
                            res.prescriptionVerified
                              ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                              : 'bg-amber-50 text-amber-700 border border-amber-200'
                          }`}
                        >
                          {res.prescriptionVerified ? '✓ Verified by Pharmacist' : '⏳ Verification Pending at Pharmacy'}
                        </span>
                      </div>
                      {res.prescriptionNumber && (
                        <div className="flex justify-between text-[11px] text-slate-500">
                          <span>Rx Ref / Doctor:</span>
                          <span className="font-medium text-slate-700">
                            {res.prescriptionNumber} {res.doctorName ? `• ${res.doctorName}` : ''}
                          </span>
                        </div>
                      )}
                    </div>
                  )}
                  {res.status === 'PENDING' && (
                    <div className="flex justify-between text-amber-800 font-semibold border-t border-slate-200/40 pt-2 items-center">
                      <span>Remaining Hold Time:</span>
                      <span className="font-black text-sm tabular-nums tracking-wide bg-amber-50 border border-amber-200 px-2 py-0.5 rounded-md">
                        <ReservationCountdown
                          expiresAt={res.expiresAt}
                          remainingSeconds={res.remainingSeconds}
                        />
                      </span>
                    </div>
                  )}
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <a
                  href={buildGoogleMapsDirectionsUrl(res.pharmacy)}
                  target="_blank"
                  rel="noreferrer"
                  className="w-full mt-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl text-xs border border-blue-600 transition-colors shadow-xs inline-flex items-center justify-center gap-2"
                >
                  <Navigation className="w-4 h-4" />
                  Start Directions
                </a>
                {res.status === 'PENDING' && (
                  <button
                    onClick={() => handleCancel(res.id)}
                    className="w-full mt-4 py-2.5 bg-red-50 hover:bg-red-100 text-red-600 font-semibold rounded-xl text-xs border border-red-200 transition-colors shadow-xs"
                  >
                    Cancel Hold &amp; Release Stock
                  </button>
                )}
              </div>
            </GlassCard>
          ))}
        </div>
      )}
    </div>
  );
};
