import React, { useEffect, useState } from 'react';
import api from '../api/client';
import type { InventoryItem, Reservation } from '../types';
import { Search, MapPin, Sparkles, Clock, CheckCircle2, AlertCircle, Loader2, Zap } from 'lucide-react';

export const EmergencyOperationsPage: React.FC = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [city, setCity] = useState('Puducherry');
  const [aiQuery, setAiQuery] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  const [aiResult, setAiResult] = useState<any>(null);

  const [searchResults, setSearchResults] = useState<InventoryItem[]>([]);
  const [myReservations, setMyReservations] = useState<Reservation[]>([]);
  const [loadingSearch, setLoadingSearch] = useState(false);

  const [reservingId, setReservingId] = useState<number | null>(null);
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
        quantity: 1,
      });
      setReservationSuccess(`Stock Reserved Successfully! Reservation ID #${res.data.data.id}`);
      fetchMyReservations();
      handleSearch();
    } catch (err: any) {
      setReservationError(err.response?.data?.message || 'Failed to create reservation.');
    } finally {
      setReservingId(null);
    }
  };

  return (
    <div className="space-y-6">
      {/* Title */}
      <div>
        <h2 className="text-xl font-bold text-white flex items-center gap-2">
          <Zap className="w-5 h-5 text-amber-400" />
          Emergency Operations Workspace
        </h2>
        <p className="text-xs text-slate-400">Search emergency medicines, utilize AI query normalization, and hold active stock</p>
      </div>

      {/* AI Assistant Glass Box */}
      <div className="glass-card-purple rounded-3xl p-6 border border-purple-500/30 space-y-4">
        <div className="flex items-center gap-2 text-purple-300 text-xs font-bold uppercase tracking-wider">
          <Sparkles className="w-4 h-4 text-purple-400" />
          Gemini LLM Natural Language Normalization
        </div>
        <h3 className="text-lg font-extrabold text-white">Ask AI to normalize emergency terms into exact medical names</h3>

        <form onSubmit={handleAiSearch} className="flex flex-col sm:flex-row gap-3">
          <input
            type="text"
            value={aiQuery}
            onChange={(e) => setAiQuery(e.target.value)}
            placeholder="Type informal query e.g. 'snake bite injection' or 'blood thinner'..."
            className="flex-1 bg-slate-900/90 border border-slate-700/80 rounded-xl px-4 py-3 text-white placeholder-slate-400 text-xs focus:outline-none focus:border-purple-500"
          />
          <button
            type="submit"
            disabled={aiLoading}
            className="px-6 py-3 bg-purple-600 hover:bg-purple-500 text-white font-bold rounded-xl text-xs flex items-center justify-center gap-2 shadow-lg shadow-purple-600/30 transition-all disabled:opacity-50"
          >
            {aiLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}
            <span>Normalize Query</span>
          </button>
        </form>

        {aiResult && (
          <div className="p-4 bg-slate-900/80 border border-purple-500/30 rounded-xl text-xs space-y-1 text-slate-200">
            <p><strong className="text-purple-400">Normalized Medicine:</strong> {aiResult.normalizedMedicineName}</p>
            <p><strong className="text-purple-400">Clinical Explanation:</strong> {aiResult.explanation}</p>
          </div>
        )}
      </div>

      {/* Action Messages */}
      {reservationSuccess && (
        <div className="p-4 bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 rounded-xl text-xs font-bold flex items-center gap-2">
          <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0" />
          <span>{reservationSuccess}</span>
        </div>
      )}

      {reservationError && (
        <div className="p-4 bg-red-500/10 border border-red-500/30 text-red-400 rounded-xl text-xs font-bold flex items-center gap-2">
          <AlertCircle className="w-5 h-5 text-red-400 shrink-0" />
          <span>{reservationError}</span>
        </div>
      )}

      {/* Active Holding Stock Banner */}
      {myReservations.length > 0 && (
        <div className="glass-panel p-5 rounded-2xl border border-amber-500/30 space-y-3">
          <h3 className="text-sm font-bold text-amber-400 flex items-center gap-2">
            <Clock className="w-4 h-4" />
            Your Active Pending Reservation (Holding Stock)
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {myReservations.map((res) => (
              <div key={res.id} className="p-4 bg-slate-900/80 rounded-xl border border-amber-500/20 text-xs space-y-1">
                <p className="font-bold text-white text-sm">{res.medicine.name}</p>
                <p className="text-slate-400">{res.pharmacy.name} • {res.pharmacy.city}</p>
                <span className="inline-block mt-1 px-2.5 py-0.5 bg-amber-500/20 text-amber-300 font-bold rounded text-[11px] border border-amber-500/30">
                  Expires in {res.remainingMinutes ?? 30} mins ({res.remainingSeconds ?? 1800}s)
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Search Grid Controls */}
      <div className="glass-panel p-6 rounded-3xl border border-slate-800 space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="relative md:col-span-2">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-3.5" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search medicine by name (e.g. Remdesivir, Anti-Venom)..."
              className="w-full bg-slate-900 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
            />
          </div>

          <div className="relative">
            <MapPin className="w-4 h-4 text-slate-400 absolute left-3.5 top-3.5" />
            <input
              type="text"
              value={city}
              onChange={(e) => setCity(e.target.value)}
              placeholder="City (e.g. Puducherry)"
              className="w-full bg-slate-900 border border-slate-800 rounded-xl pl-10 pr-4 py-2.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
            />
          </div>
        </div>

        <button
          onClick={handleSearch}
          disabled={loadingSearch}
          className="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-xl text-xs transition-colors shadow-lg shadow-blue-600/20 disabled:opacity-50"
        >
          {loadingSearch ? 'Searching Live Grid...' : 'Filter Availability Grid'}
        </button>

        {/* Inventory Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 pt-2">
          {searchResults.map((item) => (
            <div key={item.id} className="glass-panel p-5 rounded-2xl border border-slate-800 flex flex-col justify-between space-y-4">
              <div>
                <span className="text-[10px] font-bold uppercase tracking-wider text-blue-400 bg-blue-500/10 px-2 py-0.5 rounded border border-blue-500/20">
                  {item.medicineCategory || 'EMERGENCY'}
                </span>
                <h4 className="font-bold text-white text-base mt-2">{item.medicineName}</h4>
                <p className="text-xs text-slate-400 mt-1">{item.pharmacyName}</p>
              </div>

              <div className="border-t border-slate-800 pt-3 flex justify-between items-center text-xs">
                <div>
                  <p className="text-slate-400">Available Stock</p>
                  <p className="font-bold text-emerald-400 text-sm">{item.availableQuantity} units</p>
                </div>

                <button
                  onClick={() => handleCreateReservation(item.pharmacyId, item.medicineId)}
                  disabled={reservingId === item.medicineId || item.availableQuantity <= 0}
                  className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-xl text-xs shadow-md shadow-blue-600/25 disabled:opacity-50 transition-all"
                >
                  {reservingId === item.medicineId ? 'Reserving...' : 'Hold Stock'}
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
