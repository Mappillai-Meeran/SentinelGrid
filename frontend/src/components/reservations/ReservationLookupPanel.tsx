import { useState } from 'react';
import { Search, AlertCircle, MapPin } from 'lucide-react';
import { reservationService } from '../../api/services';
import type { Reservation } from '../../types';

type ReservationLookupPanelProps = {
  scopeLabel: string;
};

/**
 * Read-only lookup for operational roles. The server remains the authority for
 * patient ownership, branch scope, and ADMIN access; this component adds no
 * client-side permission bypass.
 */
export const ReservationLookupPanel: React.FC<ReservationLookupPanelProps> = ({ scopeLabel }) => {
  const [reservationId, setReservationId] = useState('');
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const lookupReservation = async (event: React.FormEvent) => {
    event.preventDefault();
    const id = Number(reservationId);
    if (!Number.isInteger(id) || id <= 0) {
      setReservation(null);
      setError('Enter a valid reservation number.');
      return;
    }

    setLoading(true);
    setError('');
    try {
      setReservation(await reservationService.getReservation(id));
    } catch (lookupError: any) {
      setReservation(null);
      setError(lookupError?.response?.data?.message || `Reservation #${id} could not be accessed in ${scopeLabel}.`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h2 className="text-sm font-bold text-slate-950">Reservation lookup</h2>
          <p className="mt-1 text-xs text-slate-500">{scopeLabel}</p>
        </div>
        <form onSubmit={lookupReservation} className="flex w-full gap-2 sm:w-auto">
          <input
            value={reservationId}
            onChange={(event) => setReservationId(event.target.value)}
            inputMode="numeric"
            placeholder="Reservation #"
            className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-800 outline-none focus:border-blue-500 sm:w-40"
          />
          <button type="submit" disabled={loading} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-60">
            <Search className="h-4 w-4" />{loading ? 'Finding...' : 'Find'}
          </button>
        </form>
      </div>

      {error && <p className="mt-4 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs font-medium text-amber-900"><AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />{error}</p>}

      {reservation && <div className="mt-4 grid grid-cols-1 gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs sm:grid-cols-3">
        <div><p className="font-bold text-slate-900">#{reservation.id} · {reservation.status}</p><p className="mt-1 text-slate-600">{reservation.medicine.name} · {reservation.quantity} unit{reservation.quantity === 1 ? '' : 's'}</p></div>
        <div><p className="font-semibold text-slate-800">{reservation.pharmacy.name}</p><p className="mt-1 flex items-center gap-1 text-slate-500"><MapPin className="h-3.5 w-3.5" />{reservation.pharmacy.city}</p></div>
        <div><p className="font-semibold text-slate-800">Prescription</p><p className="mt-1 text-slate-600">{reservation.medicine.requiresPrescription ? (reservation.prescriptionVerified ? 'Verified' : 'Pending verification') : 'Not required'}</p></div>
      </div>}
    </section>
  );
};
