import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { CheckCircle2, Clock, MapPin, Navigation, Pill } from 'lucide-react';
import { reservationService } from '../api/services';
import type { Reservation } from '../types';
import { buildGoogleMapsDirectionsUrl } from '../utils/maps';
import { PharmacyRouteMap } from '../components/ui/PharmacyRouteMap';
import { StatusBadge } from '../components/ui/StatusBadge';
import { ReservationCountdown } from '../components/ui/ReservationCountdown';

export const SecuredMedicinePage: React.FC = () => {
  const { id } = useParams();
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadReservation = async () => {
      if (!id) return;
      setLoading(true);
      try {
        const data = await reservationService.getReservation(Number(id));
        setReservation(data);
      } finally {
        setLoading(false);
      }
    };

    loadReservation();
  }, [id]);

  if (loading) {
    return <div className="text-sm text-slate-500">Loading secured medicine details...</div>;
  }

  if (!reservation) {
    return (
      <div className="bg-white border border-slate-200 rounded-2xl p-8 text-center">
        <h2 className="text-lg font-bold text-slate-900">Reservation not found</h2>
        <Link to="/search" className="text-sm text-blue-600 font-bold mt-2 inline-block">Back to search</Link>
      </div>
    );
  }

  const directionsUrl = buildGoogleMapsDirectionsUrl(reservation.pharmacy);

  return (
    <div className="space-y-6">
      <section className="bg-emerald-50 border border-emerald-200 rounded-3xl p-6 flex flex-col lg:flex-row lg:items-center justify-between gap-5">
        <div className="flex items-start gap-4">
          <div className="w-12 h-12 rounded-2xl bg-emerald-600 text-white flex items-center justify-center shrink-0">
            <CheckCircle2 className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-2xl font-black text-slate-950">Medicine secured</h1>
            <p className="text-sm text-emerald-800 mt-1">
              Reservation #{reservation.id} is holding stock at {reservation.pharmacy.name}.
            </p>
            <div className="inline-flex items-center gap-1.5 px-2.5 py-0.5 bg-amber-100 border border-amber-300 text-amber-900 rounded-full text-[10px] font-bold mt-2">
              <span>Synthetic Hold Simulation</span>
              <span>•</span>
              <span>Demonstration sandbox item (Not for real medical collection)</span>
            </div>
          </div>
        </div>

        <a
          href={directionsUrl}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center justify-center gap-3 px-6 py-4 bg-emerald-600 hover:bg-emerald-700 text-white rounded-2xl text-sm font-black shadow-lg shadow-emerald-600/15"
        >
          <Navigation className="w-5 h-5" />
          Start Directions
        </a>
      </section>

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_420px] gap-6 items-start">
        <section className="bg-white border border-slate-200 rounded-3xl p-6 space-y-5">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-[10px] font-bold text-slate-400 uppercase">Reserved medicine</p>
              <h2 className="text-xl font-black text-slate-950 mt-1">{reservation.medicine.name}</h2>
              <p className="text-sm text-slate-500">{reservation.medicine.brand} • {reservation.medicine.dosage}</p>
            </div>
            <StatusBadge status={reservation.status} />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="rounded-2xl bg-slate-50 border border-slate-200 p-4">
              <p className="text-[10px] font-bold text-slate-400 uppercase">Quantity</p>
              <p className="text-lg font-black text-slate-900 mt-1 flex items-center gap-2">
                <Pill className="w-4 h-4 text-blue-600" />
                {reservation.quantity} unit
              </p>
            </div>
            <div className="rounded-2xl bg-slate-50 border border-slate-200 p-4">
              <p className="text-[10px] font-bold text-slate-400 uppercase">Hold timer</p>
              <p className="text-lg font-black text-amber-700 mt-1 flex items-center gap-2">
                <Clock className="w-4 h-4" />
                <ReservationCountdown
                  expiresAt={reservation.expiresAt}
                  remainingSeconds={reservation.remainingSeconds}
                />
              </p>
            </div>
            <div className="md:col-span-2 rounded-2xl bg-slate-50 border border-slate-200 p-4">
              <p className="text-[10px] font-bold text-slate-400 uppercase">Pickup pharmacy</p>
              <p className="text-base font-black text-slate-900 mt-1">{reservation.pharmacy.name}</p>
              <p className="text-sm text-slate-500 mt-1 flex gap-2">
                <MapPin className="w-4 h-4 text-blue-600 shrink-0" />
                {reservation.pharmacy.address}, {reservation.pharmacy.city}
              </p>
            </div>
          </div>
        </section>

        <PharmacyRouteMap target={reservation.pharmacy} title="Pickup Route" subtitle="Open Google Maps for turn-by-turn directions." />
      </div>
    </div>
  );
};
