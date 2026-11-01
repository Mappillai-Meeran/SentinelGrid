import React, { useEffect, useState, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { medicineService, aiService, reservationService, routingService } from '../api/services';
import type { InventoryItem, Reservation } from '../types';
import { Search, MapPin, Sparkles, AlertCircle, Clock, CheckCircle2, Loader2, Navigation, ShieldCheck } from 'lucide-react';
import { buildGoogleMapsDirectionsUrl, calculateDistanceKm } from '../utils/maps';
import { getRecommendedPharmacy, rankPharmacies } from '../utils/recommendation';
import { PharmacyRouteMap } from '../components/ui/PharmacyRouteMap';
import { EmergencyMedicineCatalogue } from '../components/ui/EmergencyMedicineCatalogue';
import { ReservationCountdown } from '../components/ui/ReservationCountdown';
import { useCityStore, type SupportedCity } from '../store/cityStore';
import { useGeolocation } from '../hooks/useGeolocation';

export const PatientDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchTerm, setSearchTerm] = useState('');
  const { selectedCity, setSelectedCity } = useCityStore();
  const { latitude: userLat, longitude: userLng, loading: geoLoading, error: geoError, requestLocation } = useGeolocation();

  const [aiQuery, setAiQuery] = useState('');
  const [aiLoading, setAiLoading] = useState(false);
  const [aiResult, setAiResult] = useState<any>(null);
  const [aiError, setAiError] = useState('');

  const [searchResults, setSearchResults] = useState<InventoryItem[]>([]);
  const [myReservations, setMyReservations] = useState<Reservation[]>([]);
  const [loadingSearch, setLoadingSearch] = useState(false);
  const [searchError, setSearchError] = useState('');

  const [routeEstimates, setRouteEstimates] = useState<Record<number, { roadDistanceKm?: number | null; estimatedMinutes?: number | null; status: string }>>({});
  const [routingLoading, setRoutingLoading] = useState(false);
  const searchRequestIdRef = useRef(0);
  const automaticSearchKeyRef = useRef<string | null>(null);

  const [reservingId, setReservingId] = useState<number | null>(null);
  const [quantity] = useState(1);
  const [reservationSuccess, setReservationSuccess] = useState('');
  const [reservationError, setReservationError] = useState('');
  const [lastReservedPharmacy, setLastReservedPharmacy] = useState<InventoryItem | null>(null);
  const [selectedPharmacy, setSelectedPharmacy] = useState<InventoryItem | null>(null);
  const [rxModalItem, setRxModalItem] = useState<InventoryItem | null>(null);
  const [rxForm, setRxForm] = useState({
    prescriptionNumber: '',
    doctorName: '',
    doctorRegNumber: '',
  });

  const fetchMyReservations = async () => {
    try {
      const data = await reservationService.getMyReservations('PENDING');
      setMyReservations(data);
    } catch (err) {
      console.error('Failed to fetch patient reservations', err);
    }
  };

  useEffect(() => {
    const headerQuery = new URLSearchParams(location.search).get('name')?.trim() || '';
    const queryToSearch = headerQuery || searchTerm;
    const automaticSearchKey = `${queryToSearch}|${selectedCity}|${userLat ?? ''}|${userLng ?? ''}`;
    if (automaticSearchKeyRef.current === automaticSearchKey) return;

    automaticSearchKeyRef.current = automaticSearchKey;
    if (headerQuery && headerQuery !== searchTerm) setSearchTerm(headerQuery);
    fetchMyReservations();
    executeSearch(queryToSearch);
  }, [selectedCity, userLat, userLng, location.search]);

  // Fetch server-side ORS road routing estimates when patient location or search results change
  useEffect(() => {
    const fetchRouting = async () => {
      if (userLat == null || userLng == null || searchResults.length === 0) {
        setRouteEstimates({});
        return;
      }

      const destinationsMap = new Map<number, { id: number; latitude: number; longitude: number }>();
      searchResults.forEach((item) => {
        if (
          item.pharmacyId &&
          typeof item.pharmacyLatitude === 'number' &&
          Number.isFinite(item.pharmacyLatitude) &&
          typeof item.pharmacyLongitude === 'number' &&
          Number.isFinite(item.pharmacyLongitude)
        ) {
          destinationsMap.set(item.pharmacyId, {
            id: item.pharmacyId,
            latitude: item.pharmacyLatitude,
            longitude: item.pharmacyLongitude,
          });
        }
      });

      const destinations = Array.from(destinationsMap.values()).slice(0, 25);
      if (destinations.length === 0) return;

      setRoutingLoading(true);
      try {
        const res = await routingService.getRouteEstimates(
          { latitude: userLat, longitude: userLng },
          destinations
        );
        const newEstimates: Record<number, { roadDistanceKm?: number | null; estimatedMinutes?: number | null; status: string }> = {};
        if (res && res.routes) {
          res.routes.forEach((r) => {
            newEstimates[r.pharmacyId] = {
              roadDistanceKm: r.distanceKm,
              estimatedMinutes: r.durationMinutes,
              status: r.status,
            };
          });
        }
        setRouteEstimates(newEstimates);
      } catch (err) {
        console.error('Failed to fetch road routing estimates', err);
      } finally {
        setRoutingLoading(false);
      }
    };

    fetchRouting();
  }, [userLat, userLng, searchResults]);

  const executeSearch = async (termToSearch: string) => {
    const requestId = ++searchRequestIdRef.current;
    setLoadingSearch(true);
    setSearchError('');
    try {
      const data = await medicineService.searchMedicines(termToSearch, selectedCity, userLat, userLng, 25);
      // Discard stale responses if a newer search was initiated
      if (requestId === searchRequestIdRef.current) {
        setSearchResults(data);
        setSelectedPharmacy(data[0] ?? null);
      }
    } catch (err: any) {
      if (requestId === searchRequestIdRef.current) {
        console.error('Search failed', err);
        setSearchError(err.response?.data?.message || 'Failed to search medicine inventory. Please check your connection and retry.');
      }
    } finally {
      if (requestId === searchRequestIdRef.current) {
        setLoadingSearch(false);
      }
    }
  };

  const handleSearch = () => {
    executeSearch(searchTerm);
  };

  const handleCatalogueSearch = (query: string) => {
    setSearchTerm(query);
    executeSearch(query);
  };

  const handleAiSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!aiQuery.trim() || aiLoading) return;
    setAiLoading(true);
    setAiResult(null);
    setAiError('');

    try {
      const data = await aiService.searchAssist(aiQuery, selectedCity);
      setAiResult(data);
      const normalizedTerm = data?.normalizedMedicineName || data?.normalizedMedicine;
      if (normalizedTerm && data?.catalogMatch) {
        setSearchTerm(normalizedTerm);
        await executeSearch(normalizedTerm);
      } else {
        setSearchResults([]);
      }
    } catch (err: any) {
      console.error('AI Search assist error', err);
      setAiError(err.response?.data?.message || 'Natural language search assist is currently offline. Please enter exact medicine names below.');
    } finally {
      setAiLoading(false);
    }
  };

  const handleInitiateReservation = (item: InventoryItem) => {
    if (item.requiresPrescription) {
      setRxModalItem(item);
      setRxForm({ prescriptionNumber: '', doctorName: '', doctorRegNumber: '' });
      setReservationError('');
    } else {
      handleCreateReservation(item.pharmacyId, item.medicineId);
    }
  };

  const handleCreateReservation = async (
    pharmacyId: number,
    medicineId: number,
    prescriptionDetails?: { prescriptionNumber: string; doctorName: string; doctorRegNumber: string }
  ) => {
    setReservationError('');
    setReservationSuccess('');
    setReservingId(medicineId);

    try {
      const res = await reservationService.createReservation(pharmacyId, medicineId, quantity, prescriptionDetails);
      setReservationSuccess(`Stock reserved! Reservation ID #${res.id}`);
      setRxModalItem(null);
      const reservedItem = searchResults.find(
        (item) => item.pharmacyId === pharmacyId && item.medicineId === medicineId
      );
      setLastReservedPharmacy(reservedItem ?? null);
      fetchMyReservations();
      handleSearch();
      navigate(`/secured/${res.id}`);
    } catch (err: any) {
      setReservationError(err.response?.data?.message || 'Failed to create reservation.');
    } finally {
      setReservingId(null);
    }
  };

  // Calculate Haversine distance & attach road route estimates for each search result item
  const itemsWithDistance = searchResults.map((item) => {
    const distanceKm = calculateDistanceKm(
      userLat,
      userLng,
      item.pharmacyLatitude,
      item.pharmacyLongitude
    );
    const estimate = item.pharmacyId ? routeEstimates[item.pharmacyId] : null;

    return {
      ...item,
      distanceKm,
      roadDistanceKm: estimate?.status === 'SUCCESS' ? estimate.roadDistanceKm : null,
      estimatedMinutes: estimate?.status === 'SUCCESS' ? estimate.estimatedMinutes : null,
      routingStatus: estimate?.status === 'SUCCESS' ? ('SUCCESS' as const) : estimate ? ('UNAVAILABLE' as const) : undefined,
    };
  });

  const userLocation = userLat != null && userLng != null ? { latitude: userLat, longitude: userLng } : null;

  // Multi-tier emergency pharmacy ranking: Stock -> Operating Status -> Travel Time -> Road Distance -> Haversine -> Original Order
  const sortedResults = rankPharmacies(itemsWithDistance, userLocation);

  // Deterministic recommendation calculation
  const recommendation = getRecommendedPharmacy(sortedResults);

  // Calculate unique pharmacies represented in current search results
  const uniquePharmacyCount = new Set(
    searchResults.map((item) => item.pharmacyId ?? item.pharmacyName)
  ).size;

  return (
    <div className="space-y-8">
      <div className="bg-white border border-slate-200 rounded-2xl shadow-xs p-6">
        <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-5">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 bg-blue-50 border border-blue-100 rounded-full text-[10px] font-bold text-blue-700 uppercase tracking-wide mb-3">
              <ShieldCheck className="w-3.5 h-3.5" />
              Demonstration Stock Locator • Synthetic Availability
            </div>
            <h2 className="text-2xl font-black tracking-tight text-slate-950">Find, reserve, and navigate to emergency medicine</h2>
            <p className="text-sm text-slate-500 mt-1">Search live {selectedCity} pharmacy stock, secure a short hold, then open turn-by-turn directions.</p>
          </div>

          <form onSubmit={handleAiSearch} className="flex flex-col sm:flex-row gap-3 xl:w-[520px]">
            <div className="relative flex-1">
              <Sparkles className="w-4 h-4 text-blue-500 absolute left-3.5 top-3.5" />
              <input
                type="text"
                value={aiQuery}
                onChange={(e) => setAiQuery(e.target.value)}
                placeholder="Try: snake bite injection near me"
                className="w-full bg-slate-50 border border-slate-200 rounded-xl pl-10 pr-4 py-3 text-slate-800 placeholder-slate-400 text-sm focus:outline-none focus:border-blue-500"
              />
            </div>
            <button
              type="submit"
              disabled={aiLoading}
              className="bg-blue-600 text-white font-bold px-5 py-3 rounded-xl hover:bg-blue-700 transition-colors flex items-center justify-center gap-2 text-sm disabled:opacity-50"
            >
              {aiLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}
              Normalize
            </button>
          </form>
        </div>

        <div className="mt-5 border-t border-slate-100 pt-4">
          <div className="mb-3 flex items-center justify-between gap-3">
            <p className="text-xs font-semibold text-slate-700">Browse emergency medicine categories</p>
            <span className="text-[10px] font-bold uppercase tracking-wide text-slate-400">Searches verified live stock</span>
          </div>
          <EmergencyMedicineCatalogue onSelect={handleCatalogueSearch} />
        </div>

        {aiResult && (
          <div className={`mt-4 p-4 rounded-xl text-xs space-y-1 font-sans ${aiResult.symptomQuery ? 'bg-amber-50 border border-amber-200 text-amber-900' : 'bg-blue-50 border border-blue-100 text-blue-900'}`}>
            {aiResult.normalizedMedicineName && (
              <p><span className="font-bold">Catalog Term:</span> {aiResult.normalizedMedicineName}</p>
            )}
            <p><span className="font-bold">{aiResult.symptomQuery ? 'Clinical Notice:' : 'Query Note:'}</span> {aiResult.explanation}</p>
          </div>
        )}

        {aiError && (
          <div className="mt-4 p-4 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-800 flex items-start gap-2.5 font-semibold font-sans">
            <AlertCircle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
            <span>{aiError}</span>
          </div>
        )}
      </div>

      {/* Reservation Notifications */}
      {reservationSuccess && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-700 rounded-xl text-sm font-semibold flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-5 h-5 text-emerald-600" />
            {reservationSuccess}
          </div>
          {lastReservedPharmacy && (
            <a
              href={buildGoogleMapsDirectionsUrl(
                {
                  name: lastReservedPharmacy.pharmacyName,
                  address: lastReservedPharmacy.pharmacyAddress,
                  city: lastReservedPharmacy.pharmacyCity,
                  latitude: lastReservedPharmacy.pharmacyLatitude,
                  longitude: lastReservedPharmacy.pharmacyLongitude,
                },
                userLocation
              )}
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-emerald-600 hover:bg-emerald-700 text-white rounded-xl text-xs font-bold"
            >
              <Navigation className="w-4 h-4" />
              Start Directions
            </a>
          )}
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
        <div className="border border-amber-200 bg-amber-50 p-5 shadow-xs rounded-lg">
          <h3 className="mb-3 flex items-center gap-2 text-base font-bold text-amber-900">
            <Clock className="w-5 h-5 text-amber-600" />
            Active medicine hold
          </h3>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {myReservations.map((res) => (
              <div key={res.id} className="flex flex-col justify-between gap-4 rounded-lg border border-amber-200 bg-white p-4 sm:flex-row sm:items-center">
                <div>
                  <p className="font-bold text-slate-900 text-sm">{res.medicine.name}</p>
                  <p className="text-xs text-slate-600">{res.pharmacy.name} • {res.pharmacy.city}</p>
                  <span className="mt-2 inline-flex items-center gap-1.5 rounded bg-amber-100 px-2 py-1 text-[11px] font-bold text-amber-800">
                    <Clock className="h-3.5 w-3.5" />
                    <ReservationCountdown expiresAt={res.expiresAt} remainingSeconds={res.remainingSeconds} /> remaining
                  </span>
                </div>
                <div className="flex flex-wrap items-center gap-2 sm:justify-end">
                  <span className="text-xs text-slate-500 font-medium">Qty: {res.quantity}</span>
                  <button onClick={() => navigate(`/secured/${res.id}`)} className="rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-[10px] font-bold text-slate-700 hover:bg-slate-50">View hold</button>
                  <a
                    href={buildGoogleMapsDirectionsUrl(res.pharmacy, userLocation)}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-1.5 text-[10px] font-bold text-white hover:bg-blue-700"
                  >
                    <Navigation className="w-3.5 h-3.5" />
                    Directions
                  </a>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ⭐ Recommended Pharmacy Featured Card */}
      {recommendation.recommendedItem ? (
        <section className="space-y-4 rounded-lg border border-blue-200 bg-blue-50 p-5 shadow-xs">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-blue-100 pb-3">
            <div className="flex items-center gap-2">
              <span className="inline-flex items-center gap-1.5 rounded-full border border-blue-200 bg-white px-3 py-1 text-xs font-bold text-blue-700">
                <Sparkles className="w-3.5 h-3.5" />
                Best available option
              </span>
              <span className="text-xs font-medium text-slate-600">
                • {recommendation.reason}
              </span>
            </div>
            <span className="text-[10px] text-slate-500">
              Open status calculated from configured pharmacy hours
            </span>
          </div>

          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-5">
            <div className="space-y-1.5 min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded border border-blue-200 bg-white px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-blue-700">
                  {recommendation.recommendedItem.medicineCategory || 'EMERGENCY'}
                </span>
                <span className="rounded border border-emerald-200 bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-700">
                  {recommendation.recommendedItem.availableQuantity} units available
                </span>
                {recommendation.recommendedItem.pharmacyStatus === 'OPEN' && (
                  <span className="text-[10px] font-bold text-emerald-300 bg-emerald-950/60 border border-emerald-500/40 px-2 py-0.5 rounded flex items-center gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
                    Open ({recommendation.recommendedItem.pharmacyOperatingHours || '24x7'})
                  </span>
                )}
                {recommendation.recommendedItem.pharmacyStatus === 'CLOSED' && (
                  <span className="text-[10px] font-bold text-rose-300 bg-rose-950/60 border border-rose-500/40 px-2 py-0.5 rounded flex items-center gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-rose-400"></span>
                    Closed ({recommendation.recommendedItem.pharmacyOperatingHours || 'Hours unavailable'})
                  </span>
                )}
                {recommendation.recommendedItem.pharmacyStatus === 'UNKNOWN' && (
                  <span className="text-[10px] font-bold text-slate-300 bg-slate-800/80 border border-slate-600 px-2 py-0.5 rounded flex items-center gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-slate-400"></span>
                    Hours unavailable
                  </span>
                )}
                {recommendation.recommendedItem.roadDistanceKm != null && recommendation.recommendedItem.estimatedMinutes != null ? (
                  <span className="text-[10px] font-bold text-sky-200 bg-sky-950/60 border border-sky-500/40 px-2 py-0.5 rounded flex items-center gap-1.5">
                    <span>🚗 {recommendation.recommendedItem.roadDistanceKm} km</span>
                    <span>•</span>
                    <span>⏱️ ~{recommendation.recommendedItem.estimatedMinutes} min</span>
                  </span>
                ) : recommendation.recommendedItem.distanceKm != null ? (
                  <span className="text-[10px] font-bold text-slate-200 bg-slate-800/80 border border-slate-600 px-2 py-0.5 rounded">
                    ~{recommendation.recommendedItem.distanceKm} km (Approximate)
                  </span>
                ) : null}
              </div>

              <h3 className="truncate text-xl font-bold text-slate-950">
                {recommendation.recommendedItem.medicineName}
              </h3>
              <p className="text-sm font-bold text-slate-800">
                {recommendation.recommendedItem.pharmacyName}
              </p>
              {recommendation.recommendedItem.pharmacyAddress && (
                <p className="flex items-center gap-1.5 text-xs text-slate-600">
                  <MapPin className="w-3.5 h-3.5 shrink-0 text-blue-600" />
                  {recommendation.recommendedItem.pharmacyAddress}, {recommendation.recommendedItem.pharmacyCity}
                </p>
              )}
            </div>

            <div className="flex items-center gap-3 shrink-0 pt-2 lg:pt-0">
              <a
                href={buildGoogleMapsDirectionsUrl(
                  {
                    name: recommendation.recommendedItem.pharmacyName,
                    address: recommendation.recommendedItem.pharmacyAddress,
                    city: recommendation.recommendedItem.pharmacyCity,
                    latitude: recommendation.recommendedItem.pharmacyLatitude,
                    longitude: recommendation.recommendedItem.pharmacyLongitude,
                  },
                  userLocation
                )}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-2.5 text-xs font-bold text-slate-700 transition-colors hover:bg-slate-100"
              >
                <Navigation className="w-4 h-4 text-blue-600" />
                Directions
              </a>
              <button
                onClick={() => handleInitiateReservation(recommendation.recommendedItem!)}
                disabled={reservingId === recommendation.recommendedItem.medicineId || recommendation.recommendedItem.availableQuantity <= 0}
                className="px-5 py-2.5 bg-amber-500 hover:bg-amber-400 text-slate-950 font-black rounded-xl text-xs shadow-xs disabled:opacity-50 transition-colors cursor-pointer"
              >
                {reservingId === recommendation.recommendedItem.medicineId ? 'Reserving...' : 'Reserve Stock'}
              </button>
            </div>
          </div>
        </section>
      ) : searchResults.length > 0 ? (
        <div className="p-4 bg-amber-50 border border-amber-200 text-amber-800 rounded-2xl text-xs font-semibold flex items-center gap-2">
          <AlertCircle className="w-4 h-4 text-amber-600 shrink-0" />
          <span>{recommendation.reason}</span>
        </div>
      ) : null}

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_420px] gap-6 items-start">
        <section className="bg-white p-6 rounded-2xl border border-slate-200 shadow-xs space-y-6">
          <div className="flex flex-col lg:flex-row lg:items-end justify-between gap-4">
            <div>
              <h3 className="text-lg font-bold text-slate-900">Live Emergency Inventory</h3>
              <p className="text-xs text-slate-500 mt-1">Select a result to update the route map.</p>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-[minmax(0,1fr)_180px_auto] gap-3 lg:w-[680px]">
              <div className="relative">
                <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-3.5" />
                <input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Remdesivir, Anti-Venom, Oxygen..."
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl pl-10 pr-4 py-3 text-slate-800 text-sm focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="relative">
                <MapPin className="w-4 h-4 text-slate-400 absolute left-3.5 top-3.5 z-10" />
                <select
                  value={selectedCity}
                  onChange={(e) => setSelectedCity(e.target.value as SupportedCity)}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl pl-10 pr-4 py-3 text-slate-800 text-sm focus:outline-none focus:border-blue-500 font-medium cursor-pointer"
                >
                  <option value="Puducherry">Puducherry</option>
                  <option value="Chennai">Chennai</option>
                  <option value="Bangalore">Bengaluru</option>
                </select>
              </div>

              <button
                onClick={handleSearch}
                disabled={loadingSearch}
                className="px-5 py-3 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl text-sm transition-colors disabled:opacity-50"
              >
                {loadingSearch ? 'Searching...' : 'Search'}
              </button>
            </div>
          </div>

          {/* Search Result Summary Bar & Geolocation Action */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-3 border-t border-slate-100">
            <div className="flex items-center gap-2">
              <span className="text-xs font-black text-slate-800 uppercase tracking-wider">
                {uniquePharmacyCount > 0
                  ? `Available at ${uniquePharmacyCount} ${uniquePharmacyCount === 1 ? 'pharmacy' : 'pharmacies'}`
                  : 'No pharmacies found'}
              </span>
              {userLat != null && userLng != null && (
                <span className="text-[10px] font-bold text-blue-700 bg-blue-50 border border-blue-200 px-2 py-0.5 rounded-full flex items-center gap-1">
                  Sorted by nearest
                  {routingLoading && <Loader2 className="w-3 h-3 animate-spin text-blue-600" />}
                </span>
              )}
            </div>

            <div className="text-xs">
              {userLat != null && userLng != null ? (
                <span className="inline-flex items-center gap-1.5 text-emerald-700 text-xs font-semibold">
                  <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
                  Location active
                </span>
              ) : geoError ? (
                <span className="text-amber-700 text-xs font-medium">{geoError}</span>
              ) : (
                <button
                  type="button"
                  onClick={requestLocation}
                  disabled={geoLoading}
                  className="inline-flex items-center gap-1.5 text-xs font-bold text-blue-600 hover:text-blue-800 hover:underline cursor-pointer disabled:opacity-50"
                >
                  <Navigation className="w-3.5 h-3.5 text-blue-600" />
                  {geoLoading ? 'Detecting location...' : 'Use my location to find nearest pharmacy'}
                </button>
              )}
            </div>
          </div>

          {searchError && (
            <div className="p-4 bg-red-50 border border-red-200 text-red-700 rounded-xl text-xs font-semibold flex items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-red-600 shrink-0" />
                <span>{searchError}</span>
              </div>
              <button
                type="button"
                onClick={handleSearch}
                className="px-3 py-1 bg-red-600 hover:bg-red-700 text-white rounded-lg text-xs font-bold shrink-0 cursor-pointer"
              >
                Retry
              </button>
            </div>
          )}

          {loadingSearch && (
            <div className="p-8 text-center text-slate-500 flex flex-col items-center justify-center gap-2">
              <Loader2 className="w-6 h-6 animate-spin text-blue-600" />
              <p className="text-xs font-medium">Searching live emergency medicine inventory...</p>
            </div>
          )}

          {!loadingSearch && !searchError && sortedResults.length === 0 && (
            <div className="p-8 text-center bg-slate-50 border border-dashed border-slate-200 rounded-2xl space-y-2">
              <AlertCircle className="w-8 h-8 text-slate-400 mx-auto" />
              <h4 className="text-sm font-bold text-slate-800">No matching medicines found</h4>
              <p className="text-xs text-slate-500 max-w-md mx-auto">
                No active stock found {searchTerm ? `for "${searchTerm}" ` : ''}in {selectedCity}. Try searching by another name, checking spelling, or browsing all medicines.
              </p>
              <div className="mt-5 border-t border-slate-200 pt-4 text-left">
                <div className="mb-3 flex items-center gap-2"><span className="rounded bg-slate-200 px-2 py-1 text-[10px] font-bold text-slate-600">DEMO</span><p className="text-xs font-semibold text-slate-700">Explore the medicine catalogue</p></div>
                <p className="mb-3 text-xs text-slate-500">These names are search suggestions only. They do not represent live pharmacy stock.</p>
                <EmergencyMedicineCatalogue expanded onSelect={handleCatalogueSearch} />
              </div>
              {searchTerm && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchTerm('');
                    executeSearch('');
                  }}
                  className="mt-2 px-4 py-1.5 bg-slate-200 hover:bg-slate-300 text-slate-800 text-xs font-bold rounded-lg transition-colors cursor-pointer"
                >
                  Clear search & show all
                </button>
              )}
            </div>
          )}

          <div className="space-y-3">
            {sortedResults.map((item) => (
              <div
                key={item.id}
                onClick={() => setSelectedPharmacy(item)}
                role="button"
                tabIndex={0}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    setSelectedPharmacy(item);
                  }
                }}
                className={`w-full text-left bg-slate-50 p-4 rounded-2xl border flex flex-col lg:flex-row lg:items-center justify-between gap-4 transition-colors ${
                  selectedPharmacy?.id === item.id ? 'border-blue-400 ring-2 ring-blue-100' : 'border-slate-200 hover:border-blue-200'
                }`}
              >
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    {recommendation.recommendedItem?.pharmacyId === item.pharmacyId && item.availableQuantity > 0 && (
                      <span className="text-[10px] font-black uppercase tracking-wider text-amber-800 bg-amber-100 border border-amber-300 px-2 py-0.5 rounded flex items-center gap-1">
                        <Sparkles className="w-3 h-3 text-amber-600" />
                        Recommended Option
                      </span>
                    )}
                    <span className="text-[10px] font-bold uppercase tracking-wider text-blue-600 bg-blue-50 px-2 py-0.5 rounded border border-blue-200">
                      {item.medicineCategory || 'EMERGENCY'}
                    </span>
                    <span className="text-[10px] font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded">
                      {item.availableQuantity} units available
                    </span>
                    {item.requiresPrescription && (
                      <span className="text-[10px] font-bold text-amber-800 bg-amber-50 border border-amber-200 px-2 py-0.5 rounded">
                        Rx Required
                      </span>
                    )}
                    {item.pharmacyStatus === 'OPEN' && (
                      <span className="text-[10px] font-bold text-emerald-800 bg-emerald-100/90 border border-emerald-300 px-2 py-0.5 rounded flex items-center gap-1">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-600"></span>
                        Open ({item.pharmacyOperatingHours || '24x7'})
                      </span>
                    )}
                    {item.pharmacyStatus === 'CLOSED' && (
                      <span className="text-[10px] font-bold text-rose-800 bg-rose-100/90 border border-rose-300 px-2 py-0.5 rounded flex items-center gap-1">
                        <span className="w-1.5 h-1.5 rounded-full bg-rose-600"></span>
                        Closed ({item.pharmacyOperatingHours || 'Hours unavailable'})
                      </span>
                    )}
                    {item.pharmacyStatus === 'UNKNOWN' && (
                      <span className="text-[10px] font-bold text-slate-600 bg-slate-100 border border-slate-200 px-2 py-0.5 rounded flex items-center gap-1">
                        <span className="w-1.5 h-1.5 rounded-full bg-slate-400"></span>
                        Hours unavailable
                      </span>
                    )}
                    {/* Road Distance & ETA Badge when routing succeeds */}
                    {item.roadDistanceKm != null && item.estimatedMinutes != null ? (
                      <span className="text-[10px] font-bold text-blue-900 bg-blue-100/90 border border-blue-300 px-2 py-0.5 rounded flex items-center gap-1.5">
                        <span>🚗 {item.roadDistanceKm} km</span>
                        <span>•</span>
                        <span>⏱️ ~{item.estimatedMinutes} min</span>
                      </span>
                    ) : item.distanceKm != null ? (
                      <span className="text-[10px] font-bold text-slate-700 bg-slate-200/80 border border-slate-300 px-2 py-0.5 rounded">
                        ~{item.distanceKm} km (Approximate)
                      </span>
                    ) : null}
                  </div>
                  <h4 className="font-bold text-slate-900 text-base mt-2">{item.medicineName}</h4>
                  <p className="text-xs text-slate-600 mt-1">{item.pharmacyName}</p>
                  {item.pharmacyAddress && (
                    <p className="text-xs text-slate-500 mt-1 flex gap-1.5">
                      <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                      {item.pharmacyAddress}
                    </p>
                  )}
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <span
                    onClick={(event) => event.stopPropagation()}
                    className="contents"
                  >
                    <a
                      href={buildGoogleMapsDirectionsUrl(
                        {
                          name: item.pharmacyName,
                          address: item.pharmacyAddress,
                          city: item.pharmacyCity,
                          latitude: item.pharmacyLatitude,
                          longitude: item.pharmacyLongitude,
                        },
                        userLocation
                      )}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-flex items-center justify-center gap-2 px-4 py-2 bg-white hover:bg-slate-100 text-slate-700 font-bold rounded-xl text-xs border border-slate-200 transition-colors"
                    >
                      <Navigation className="w-4 h-4 text-blue-600" />
                      Start Navigation
                    </a>
                  </span>
                  <span
                    onClick={(event) => event.stopPropagation()}
                    className="contents"
                  >
                    <button
                      onClick={() => handleInitiateReservation(item)}
                      disabled={reservingId === item.medicineId || item.availableQuantity <= 0}
                      className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl text-xs shadow-xs disabled:opacity-50 transition-colors cursor-pointer"
                    >
                      {reservingId === item.medicineId ? 'Reserving...' : 'Reserve'}
                    </button>
                  </span>
                </div>
              </div>
            ))}
          </div>
        </section>

        <div className="xl:sticky xl:top-6">
          <PharmacyRouteMap target={selectedPharmacy ?? lastReservedPharmacy} />
        </div>
      </div>

      {/* Step 4 Prescription Details Modal */}
      {rxModalItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl max-w-lg w-full p-6 shadow-xl border border-slate-200 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div>
                <h3 className="text-base font-bold text-slate-900">Prescription Verification Details</h3>
                <p className="text-xs text-slate-500">
                  {rxModalItem.medicineName} is a prescription-controlled emergency medication.
                </p>
              </div>
              <button
                onClick={() => setRxModalItem(null)}
                className="text-slate-400 hover:text-slate-600 text-sm font-bold p-1 cursor-pointer"
              >
                ✕
              </button>
            </div>

            <div className="p-3 bg-amber-50 border border-amber-200 rounded-xl text-xs text-amber-900 leading-relaxed">
              <p className="font-semibold">Student-Demo Prescription Workflow Notice:</p>
              <p className="text-[11px] text-amber-800 mt-0.5">
                SentinelGrid enforces prescription reference and doctor registration metadata to hold stock.
                The dispensing pharmacist will physically inspect the physical/digital prescription before confirming pickup.
              </p>
            </div>

            {reservationError && (
              <div className="p-3 bg-rose-50 border border-rose-200 text-rose-700 rounded-xl text-xs font-semibold">
                {reservationError}
              </div>
            )}

            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (!rxForm.prescriptionNumber.trim() || !rxForm.doctorName.trim() || !rxForm.doctorRegNumber.trim()) {
                  setReservationError('Please complete all prescription fields before holding stock.');
                  return;
                }
                handleCreateReservation(rxModalItem.pharmacyId, rxModalItem.medicineId, {
                  prescriptionNumber: rxForm.prescriptionNumber.trim(),
                  doctorName: rxForm.doctorName.trim(),
                  doctorRegNumber: rxForm.doctorRegNumber.trim(),
                });
              }}
              className="space-y-3"
            >
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Prescription Reference Number *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. RX-2026-98124"
                  value={rxForm.prescriptionNumber}
                  onChange={(e) => setRxForm({ ...rxForm, prescriptionNumber: e.target.value })}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Prescribing Doctor Full Name *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Dr. Rajesh Sharma"
                  value={rxForm.doctorName}
                  onChange={(e) => setRxForm({ ...rxForm, doctorName: e.target.value })}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Doctor Medical Registration / License # *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. MCI-TN-2015-84920"
                  value={rxForm.doctorRegNumber}
                  onChange={(e) => setRxForm({ ...rxForm, doctorRegNumber: e.target.value })}
                  className="w-full bg-slate-50 border border-slate-200 rounded-xl px-3.5 py-2 text-xs text-slate-900 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                />
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setRxModalItem(null)}
                  className="px-4 py-2 border border-slate-200 text-slate-600 font-semibold rounded-xl text-xs hover:bg-slate-50 transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={reservingId === rxModalItem.medicineId}
                  className="px-5 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl text-xs shadow-xs disabled:opacity-50 transition-colors cursor-pointer"
                >
                  {reservingId === rxModalItem.medicineId ? 'Holding Stock...' : 'Confirm Prescription & Hold'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
