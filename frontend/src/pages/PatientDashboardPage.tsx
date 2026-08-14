import React, { useEffect, useState } from 'react';
import api from '../api/client';
import type { InventoryItem, Reservation } from '../types';
import { Search, MapPin, Sparkles, AlertCircle, Clock, CheckCircle2, Loader2 } from 'lucide-react';

export const PatientDashboardPage: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [city, setCity] = useState('Puducherry');
  const [aiQuery, setAiQuery] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  const [aiResult, setAiResult] = useState<any>(null);

  const [searchResults, setSearchResults] = useState<InventoryItem[]>([]);
  const [myReservations, setMyReservations] = useState<Reservation[]>([]);
  const [loadingSearch, setLoadingSearch] = useState(false);

  const [reservingId, setReservingId] = useState<number | null>(null);
  const [quantity] = useState(1);
  const [reservationSuccess, setReservationSuccess] = useState('');
  const [reservationError, setReservationError] = useState('');

  const fetchMyReservations = async () => {
    try {
      const res = await api.get('/reservations/my?status=PENDING');
      setMyReservations(res.data.data || []);
    } catch (err) {
      console.error('Failed to fetch patient reservations', err);
    }
  };

  useEffect(() => {
    fetchMyReservations();
    handleSearch();
  }, []);

  const handleSearch = async () => {
    setLoadingSearch(true);
    try {
      const res = await api.get('/medicines/search', {
        params: { name: searchTerm, city },
      });
      setSearchResults(res.data.data || []);
    } catch (err) {
      console.error('Search failed', err);
    } finally {
      setLoadingSearch(false);
    }
  };

  const handleAiSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!aiQuery.trim()) return;
    setAiLoading(true);
    setAiResult(null);

    try {
      const res = await api.post('/ai/search-assist', { query: aiQuery, city });
      setAiResult(res.data.data);
      if (res.data.data?.normalizedMedicineName) {
        setSearchTerm(res.data.data.normalizedMedicineName);
        handleSearch();
      }
    } catch (err) {
      console.error('AI Search assist error', err);
    } finally {
      setAiLoading(false);
    }
  };

  const handleCreateReservation = async (pharmacyId: number, medicineId: number) => {
    setReservationError('');
    setReservationSuccess('');
    setReservingId(medicineId);

    try {
      const res = await api.post('/reservations', {
        pharmacyId,
        medicineId,
        quantity,
      });
      setReservationSuccess(`Stock reserved! Reservation ID #${res.data.data.id}`);
      fetchMyReservations();
      handleSearch();
    } catch (err: any) {
      setReservationError(err.response?.data?.message || 'Failed to create reservation.');
    } finally {
      setReservingId(null);
    }
  };

  return (
    <div className="space-y-8">
      {/* AI Assistant Banner */}
      <div className="bg-gradient-to-r from-blue-700 via-indigo-700 to-blue-900 text-white rounded-3xl p-8 shadow-xl relative overflow-hidden">
        <div className="relative z-10 max-w-2xl">
          <div className="inline-flex items-center gap-2 px-3 py-1 bg-white/10 backdrop-blur-md rounded-full text-xs font-semibold text-blue-200 border border-white/20 mb-4">
            <Sparkles className="w-4 h-4 text-amber-300" />
            Gemini Emergency AI Assistant
          </div>
          <h2 className="text-2xl font-bold tracking-tight mb-2">
            Describe symptoms or informal terms in natural language
          </h2>
          <p className="text-blue-100 text-sm mb-6">
            E.g., <span className="italic text-white">"snake bite injection near me"</span> or <span className="italic text-white">"blood thinner injection"</span>
          </p>

          <form onSubmit={handleAiSearch} className="flex gap-3">
            <input
              type="text"
              value={aiQuery}
              onChange={(e) => setAiQuery(e.target.value)}
              placeholder="Ask AI Assistant..."
              className="flex-1 bg-white/10 backdrop-blur-md border border-white/20 rounded-xl px-4 py-3 text-white placeholder-blue-200 text-sm focus:outline-none focus:ring-2 focus:ring-white/50"
            />
            <button
              type="submit"
              disabled={aiLoading}
              className="bg-white text-blue-900 font-bold px-6 py-3 rounded-xl hover:bg-blue-50 transition-colors flex items-center gap-2 text-sm shadow-lg disabled:opacity-50"
            >
              {aiLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4 text-amber-500" />}
              <span>Normalize &amp; Find</span>
            </button>
          </form>

          {aiResult && (
            <div className="mt-4 p-4 bg-white/10 backdrop-blur-md border border-white/20 rounded-xl text-xs space-y-1">
              <p><span className="font-bold text-blue-200">Normalized Term:</span> {aiResult.normalizedMedicineName}</p>
              <p><span className="font-bold text-blue-200">AI Advice:</span> {aiResult.explanation}</p>
            </div>
          )}
        </div>
      </div>

      {/* Reservation Notifications */}
      {reservationSuccess && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-700 rounded-xl text-sm font-semibold flex items-center gap-2">
          <CheckCircle2 className="w-5 h-5 text-emerald-600" />
          {reservationSuccess}
        </div>
      )}

      {reservationError && (
        <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-xl text-sm font-semibold flex items-center gap-2">
          <AlertCircle className="w-5 h-5 text-red-600" />
          {reservationError}
        </div>
      )}

      {/* Active Reservations Widget */}
      {myReservations.length > 0 && (
        <div className="bg-amber-50 border border-amber-200 rounded-2xl p-6 shadow-xs">
          <h3 className="text-base font-bold text-amber-900 mb-3 flex items-center gap-2">
            <Clock className="w-5 h-5 text-amber-600" />
            Your Active Pending Reservation (Stock Held)
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {myReservations.map((res) => (
              <div key={res.id} className="bg-white p-4 rounded-xl border border-amber-200 shadow-xs flex justify-between items-center">
                <div>
                  <p className="font-bold text-slate-900 text-sm">{res.medicine.name}</p>
                  <p className="text-xs text-slate-600">{res.pharmacy.name} • {res.pharmacy.city}</p>
                  <span className="inline-block mt-2 px-2 py-0.5 bg-amber-100 text-amber-800 text-[10px] font-bold rounded">
                    Expires in {res.remainingMinutes ?? 30} mins ({res.remainingSeconds ?? 1800}s)
                  </span>
                </div>
                <div className="text-right">
                  <span className="text-xs text-slate-500 font-medium">Qty: {res.quantity}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Medicine Search Section */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs space-y-6">
        <h3 className="text-lg font-bold text-slate-800">Search Live Emergency Inventory</h3>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="relative md:col-span-2">
            <Search className="w-5 h-5 text-slate-400 absolute left-3.5 top-3.5" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search medicine name (e.g. Remdesivir, Anti-Venom)..."
              className="w-full bg-slate-50 border border-slate-200 rounded-xl pl-11 pr-4 py-3 text-slate-800 text-sm focus:outline-none focus:border-blue-500"
            />
          </div>

          <div className="relative">
            <MapPin className="w-5 h-5 text-slate-400 absolute left-3.5 top-3.5" />
            <input
              type="text"
              value={city}
              onChange={(e) => setCity(e.target.value)}
              placeholder="City (e.g. Puducherry)"
              className="w-full bg-slate-50 border border-slate-200 rounded-xl pl-11 pr-4 py-3 text-slate-800 text-sm focus:outline-none focus:border-blue-500"
            />
          </div>
        </div>

        <button
          onClick={handleSearch}
          disabled={loadingSearch}
          className="px-6 py-2.5 bg-blue-600 hover:bg-blue-500 text-white font-semibold rounded-xl text-sm transition-colors"
        >
          {loadingSearch ? 'Searching...' : 'Filter Availability Grid'}
        </button>

        {/* Results Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 pt-4">
          {searchResults.map((item) => (
            <div key={item.id} className="bg-slate-50 p-5 rounded-2xl border border-slate-200 flex flex-col justify-between space-y-4">
              <div>
                <span className="text-[10px] font-bold uppercase tracking-wider text-blue-600 bg-blue-50 px-2 py-0.5 rounded border border-blue-200">
                  {item.medicineCategory || 'EMERGENCY'}
                </span>
                <h4 className="font-bold text-slate-900 text-base mt-2">{item.medicineName}</h4>
                <p className="text-xs text-slate-600 mt-1">{item.pharmacyName}</p>
              </div>

              <div className="border-t border-slate-200/80 pt-3 flex justify-between items-center text-xs">
                <div>
                  <p className="text-slate-500">Available Stock</p>
                  <p className="font-bold text-emerald-600 text-sm">{item.availableQuantity} units</p>
                </div>

                <button
                  onClick={() => handleCreateReservation(item.pharmacyId, item.medicineId)}
                  disabled={reservingId === item.medicineId || item.availableQuantity <= 0}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl text-xs shadow-xs disabled:opacity-50 transition-colors"
                >
                  {reservingId === item.medicineId ? 'Reserving...' : 'Reserve Stock'}
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
