import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Search,
  Clock,
  Building2,
  Navigation,
  ShieldCheck,
  RefreshCw,
  HeartPulse,
  Pill,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Activity,
} from 'lucide-react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { reservationService } from '../../api/services';
import type { Reservation } from '../../types';
import { useAuthStore } from '../../store/authStore';
import { useCityStore } from '../../store/cityStore';
import { buildGoogleMapsDirectionsUrl } from '../../utils/maps';
import { useToast } from '../ui/Toast';

export const PatientDashboardView: React.FC = () => {
  const { user } = useAuthStore();
  const { selectedCity } = useCityStore();
  const { showToast, showError } = useToast();

  const [activeHolds, setActiveHolds] = useState<Reservation[]>([]);
  const [allReservations, setAllReservations] = useState<Reservation[]>([]);
  const [, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  // Mini Calendar State
  const [calendarDate, setCalendarDate] = useState(new Date());

  const loadPatientData = async (silent = false) => {
    if (!silent) setLoading(true);
    else setRefreshing(true);

    try {
      const [pendingRes, historyRes] = await Promise.all([
        reservationService.getMyReservations('PENDING'),
        reservationService.getMyReservations('ALL'),
      ]);

      setActiveHolds(pendingRes);
      setAllReservations(historyRes);
    } catch (err) {
      console.error('Failed to load patient CareLink dashboard data', err);
      showError(err, 'Unable to sync health records');
    } finally {
      if (!silent) setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    loadPatientData();
  }, [selectedCity]);

  // Live countdown clock for pending holds
  useEffect(() => {
    if (activeHolds.length === 0) return;

    const timer = setInterval(() => {
      setActiveHolds((prevHolds) =>
        prevHolds.map((hold) => {
          const nextSecs = (hold.remainingSeconds ?? 1800) - 1;
          if (nextSecs <= 0) {
            return {
              ...hold,
              status: 'EXPIRED',
              remainingSeconds: 0,
              remainingMinutes: 0,
            };
          }
          return {
            ...hold,
            remainingSeconds: nextSecs,
            remainingMinutes: Math.floor(nextSecs / 60),
          };
        })
      );
    }, 1000);

    return () => clearInterval(timer);
  }, [activeHolds.length]);

  const handleCancelHold = async (reservationId: number) => {
    if (!window.confirm('Are you sure you want to release this emergency reservation hold?')) {
      return;
    }
    try {
      await reservationService.cancelReservation(reservationId);
      showToast('Reservation hold cancelled and stock released', 'info');
      loadPatientData(true);
    } catch (err) {
      showError(err, 'Failed to cancel reservation');
    }
  };

  // Chart data computed from actual patient history over the last 7 calendar days
  const confirmedCount = allReservations.filter((r) => r.status === 'CONFIRMED').length;

  // Genuine chronological 7-day trend based on actual reservation timestamps
  const last7Days = Array.from({ length: 7 }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - (6 - i));
    d.setHours(0, 0, 0, 0);
    return d;
  });

  const chartData = last7Days.map((dateObj) => {
    const dateStr = dateObj.toISOString().slice(0, 10);
    const dayLabel = dateObj.toLocaleDateString('default', { weekday: 'short' });
    const count = allReservations.filter((r) => {
      if (!r.createdAt) return false;
      const resDate = new Date(r.createdAt).toISOString().slice(0, 10);
      return resDate === dateStr;
    }).length;
    return {
      day: dayLabel,
      date: dateStr,
      holds: count,
    };
  });

  const totalInPeriod = chartData.reduce((acc, c) => acc + c.holds, 0);
  const featuredHold = activeHolds[0] ?? null;

  // Real Dynamic Calendar Engine
  const calYear = calendarDate.getFullYear();
  const calMonth = calendarDate.getMonth();
  const currentMonthName = calendarDate.toLocaleString('default', { month: 'long' });
  const daysInMonth = new Date(calYear, calMonth + 1, 0).getDate();
  const firstDayWeekday = (new Date(calYear, calMonth, 1).getDay() + 6) % 7; // Monday = 0, Sunday = 6

  const today = new Date();
  const isViewingCurrentMonth = today.getFullYear() === calYear && today.getMonth() === calMonth;
  const currentDay = isViewingCurrentMonth ? today.getDate() : -1;

  const handlePrevMonth = () => {
    setCalendarDate(new Date(calYear, calMonth - 1, 1));
  };
  const handleNextMonth = () => {
    setCalendarDate(new Date(calYear, calMonth + 1, 1));
  };

  const getDayActivity = (dayNum: number) => {
    const hasHold = activeHolds.some((r) => {
      if (!r.expiresAt) return false;
      const d = new Date(r.expiresAt);
      return d.getFullYear() === calYear && d.getMonth() === calMonth && d.getDate() === dayNum;
    });
    const hasPickup = allReservations.some((r) => {
      if (r.status !== 'CONFIRMED' || !r.confirmedAt) return false;
      const d = new Date(r.confirmedAt);
      return d.getFullYear() === calYear && d.getMonth() === calMonth && d.getDate() === dayNum;
    });
    return { hasHold, hasPickup };
  };

  return (
    <div className="space-y-6 max-w-[1400px] mx-auto pb-12">
      {/* Top Bar with Search & Profile Shortcut */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-400 uppercase tracking-wider">
            <HeartPulse className="w-4 h-4 text-blue-500" />
            <span>SentinelGrid Patient Care</span>
            <span>•</span>
            <span className="text-slate-600 font-bold">{selectedCity} Hub</span>
          </div>
          <h1 className="text-2xl font-black text-slate-900 tracking-tight mt-0.5">
            Dashboard — Hello, {user?.username ?? 'Patient'}
          </h1>
        </div>

        <div className="flex items-center gap-3">
          <Link
            to="/search"
            className="px-4 py-2 rounded-2xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold flex items-center gap-2 shadow-md shadow-blue-500/15 transition-all"
          >
            <Search className="w-3.5 h-3.5" />
            + New Emergency Hold
          </Link>
          <button
            onClick={() => loadPatientData(true)}
            disabled={refreshing}
            className="p-2 rounded-2xl bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 transition-colors shadow-2xs"
            title="Refresh Health Records"
          >
            <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Main Grid: 2 Columns (Center Main Dashboard + Right Panel) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left & Center Main Column (2 Spans) */}
        <div className="lg:col-span-2 space-y-6">
          {/* Main Area Chart Card (iMed Style) */}
          <div className="bg-white rounded-[28px] border border-slate-200/90 p-6 shadow-sm space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
              <div>
                <h3 className="font-extrabold text-base text-slate-900">Personal Hold Activity</h3>
                <p className="text-xs text-slate-500">
                  Last 7 days ({chartData[0]?.date ?? ''} to {chartData[6]?.date ?? ''})
                </p>
              </div>

              <div className="flex items-center gap-4 text-xs font-semibold">
                <span className="flex items-center gap-1.5 text-blue-600 font-bold">
                  <span className="w-2.5 h-2.5 rounded-full bg-blue-600"></span>
                  Holds in Period ({totalInPeriod})
                </span>
                <span className="flex items-center gap-1.5 text-slate-600 font-bold">
                  <span className="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
                  Total Pickups ({confirmedCount})
                </span>
              </div>
            </div>

            {/* Smooth Spline Chart or Honest Empty State */}
            {totalInPeriod === 0 ? (
              <div className="h-48 flex flex-col items-center justify-center text-slate-400 text-xs space-y-1.5 border border-dashed border-slate-200 rounded-2xl my-2">
                <Clock className="w-6 h-6 text-slate-300 stroke-[1.5]" />
                <span className="font-bold text-slate-600">No reservations recorded in this period</span>
                <span className="text-[11px] text-slate-400">Your reservation activity over the last 7 days will be visualized here.</span>
              </div>
            ) : (
              <div className="h-56 w-full pt-2">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                    <defs>
                      <linearGradient id="colorHolds" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#2563EB" stopOpacity={0.25} />
                        <stop offset="95%" stopColor="#2563EB" stopOpacity={0.0} />
                      </linearGradient>
                    </defs>
                    <XAxis
                      dataKey="day"
                      axisLine={false}
                      tickLine={false}
                      tick={{ fill: '#94A3B8', fontSize: 11, fontWeight: 600 }}
                    />
                    <YAxis
                      axisLine={false}
                      tickLine={false}
                      tick={{ fill: '#94A3B8', fontSize: 11 }}
                      allowDecimals={false}
                    />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: '#0F172A',
                        borderRadius: '16px',
                        border: 'none',
                        color: '#FFFFFF',
                        fontSize: '12px',
                      }}
                      formatter={(val: any) => [`${val} hold(s)`, 'Reservations']}
                    />
                    <Area
                      type="monotone"
                      dataKey="holds"
                      stroke="#2563EB"
                      strokeWidth={3}
                      fillOpacity={1}
                      fill="url(#colorHolds)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>

          {/* iMed Metric Tiles Grid */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            {/* Tile 1: Primary Blue (Active Holds) */}
            <div className="bg-blue-600 text-white rounded-[24px] p-5 shadow-sm space-y-2 relative overflow-hidden">
              <div className="w-8 h-8 rounded-xl bg-white/20 flex items-center justify-center">
                <Clock className="w-4 h-4 text-white" />
              </div>
              <div>
                <p className="text-[11px] font-bold text-blue-100 uppercase tracking-wider">Active Holds</p>
                <p className="text-2xl font-black text-white">{activeHolds.length}</p>
              </div>
              <p className="text-[10px] text-blue-200">Guaranteed at counter</p>
            </div>

            {/* Tile 2: Soft Light Blue (Completed Pickups) */}
            <div className="bg-blue-50 text-slate-800 rounded-[24px] p-5 border border-blue-100 shadow-2xs space-y-2">
              <div className="w-8 h-8 rounded-xl bg-blue-100 flex items-center justify-center">
                <CheckCircle2 className="w-4 h-4 text-blue-600" />
              </div>
              <div>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Completed</p>
                <p className="text-2xl font-black text-slate-900">{confirmedCount}</p>
              </div>
              <p className="text-[10px] text-slate-500">Pickups dispensed</p>
            </div>

            {/* Tile 3: Soft Green (Verified Prescriptions) */}
            <div className="bg-emerald-50 text-slate-800 rounded-[24px] p-5 border border-emerald-100 shadow-2xs space-y-2">
              <div className="w-8 h-8 rounded-xl bg-emerald-100 flex items-center justify-center">
                <ShieldCheck className="w-4 h-4 text-emerald-600" />
              </div>
              <div>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Rx Verified</p>
                <p className="text-2xl font-black text-slate-900">
                  {allReservations.filter((r) => r.prescriptionVerified).length}
                </p>
              </div>
              <p className="text-[10px] text-emerald-700">Clinical approved</p>
            </div>

            {/* Tile 4: Soft Slate (Expired / Released) */}
            <div className="bg-slate-50 text-slate-800 rounded-[24px] p-5 border border-slate-200 shadow-2xs space-y-2">
              <div className="w-8 h-8 rounded-xl bg-slate-200 flex items-center justify-center">
                <Activity className="w-4 h-4 text-slate-600" />
              </div>
              <div>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Total Handled</p>
                <p className="text-2xl font-black text-slate-900">{allReservations.length}</p>
              </div>
              <p className="text-[10px] text-slate-500">Lifetime records</p>
            </div>
          </div>

          {/* Pickup Preparation Checklist (Notes Card in iMed Style) */}
          <div className="bg-white rounded-[28px] border border-slate-200/90 p-6 shadow-sm space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-sm text-slate-900">Counter Pickup Preparation Checklist</h3>
              <span className="text-[11px] font-bold text-blue-600">Patient Guidelines</span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100 flex items-start gap-3">
                <div className="w-5 h-5 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center shrink-0 mt-0.5">
                  ✓
                </div>
                <div>
                  <p className="font-bold text-slate-900">Present Ticket ID</p>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    Show your Ticket #RES code to the pharmacist upon arrival.
                  </p>
                </div>
              </div>

              <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100 flex items-start gap-3">
                <div className="w-5 h-5 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center shrink-0 mt-0.5">
                  ✓
                </div>
                <div>
                  <p className="font-bold text-slate-900">Prescription Requirement</p>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    For Schedule H/X medicines, present the physical prescription slip.
                  </p>
                </div>
              </div>

              <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100 flex items-start gap-3">
                <div className="w-5 h-5 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center shrink-0 mt-0.5">
                  ✓
                </div>
                <div>
                  <p className="font-bold text-slate-900">Guaranteed Stock Hold</p>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    Your stock is locked exclusively for 30 minutes from booking.
                  </p>
                </div>
              </div>

              <div className="p-3.5 rounded-2xl bg-slate-50 border border-slate-100 flex items-start gap-3">
                <div className="w-5 h-5 rounded-full bg-amber-100 text-amber-700 flex items-center justify-center shrink-0 mt-0.5">
                  !
                </div>
                <div>
                  <p className="font-bold text-slate-900">Automatic Stock Release</p>
                  <p className="text-[11px] text-slate-500 mt-0.5">
                    Unclaimed holds return to public inventory when the timer hits zero.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Right Rail: Mini Calendar & Active Treatment Card (iMed Style) */}
        <div className="space-y-6">
          {/* Mini Calendar Card with Dynamic Engine & Real Activity Markers */}
          <div className="bg-white rounded-[28px] border border-slate-200/90 p-6 shadow-sm space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider">Calendar</p>
                <h3 className="text-base font-black text-slate-900">
                  {currentMonthName} {calYear}
                </h3>
              </div>
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  onClick={handlePrevMonth}
                  aria-label="Previous month"
                  className="p-1 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-700 transition-colors"
                >
                  <ChevronLeft className="w-4 h-4" />
                </button>
                <button
                  type="button"
                  onClick={handleNextMonth}
                  aria-label="Next month"
                  className="p-1 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-slate-700 transition-colors"
                >
                  <ChevronRight className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Days Grid */}
            <div className="grid grid-cols-7 gap-1 text-center text-xs">
              {['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d, i) => (
                <span key={i} className="text-[10px] font-bold text-slate-400 py-1">{d}</span>
              ))}
              {/* Monday-based blank offset cells */}
              {Array.from({ length: firstDayWeekday }, (_, i) => (
                <div key={`blank-${i}`} className="py-1.5" />
              ))}
              {/* Actual days in this month */}
              {Array.from({ length: daysInMonth }, (_, i) => i + 1).map((day) => {
                const isToday = day === currentDay;
                const { hasHold, hasPickup } = getDayActivity(day);
                return (
                  <div
                    key={day}
                    title={
                      isToday
                        ? 'Today'
                        : hasHold
                        ? 'Active hold expires on this date'
                        : hasPickup
                        ? 'Completed pickup on this date'
                        : undefined
                    }
                    className={`py-1.5 rounded-xl font-bold text-xs transition-colors relative ${
                      isToday
                        ? 'bg-blue-600 text-white shadow-sm shadow-blue-500/20'
                        : hasHold
                        ? 'bg-cyan-50 text-cyan-900 border border-cyan-300 font-extrabold'
                        : hasPickup
                        ? 'bg-emerald-50 text-emerald-900 border border-emerald-200'
                        : 'text-slate-700 hover:bg-slate-100'
                    }`}
                  >
                    <span>{day}</span>
                    {hasHold && !isToday && (
                      <span className="absolute bottom-0.5 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-cyan-600" />
                    )}
                    {hasPickup && !isToday && !hasHold && (
                      <span className="absolute bottom-0.5 left-1/2 -translate-x-1/2 w-1 h-1 rounded-full bg-emerald-600" />
                    )}
                  </div>
                );
              })}
            </div>

            {/* Truthful Legend */}
            <div className="flex items-center justify-between text-[10px] text-slate-500 pt-2 border-t border-slate-100 font-semibold">
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-blue-600 inline-block" /> Today
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-cyan-500 inline-block" /> Active Hold
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-emerald-500 inline-block" /> Pickup
              </span>
            </div>
          </div>

          {/* "Your Medication Hold" Card (iMed "Your Treatment" Card) */}
          <div className="bg-white rounded-[28px] border border-slate-200/90 p-6 shadow-sm space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-extrabold text-sm text-slate-900">Your Active Treatment</h3>
              <Link to="/reservations" className="text-xs font-bold text-blue-600 hover:underline">
                See all
              </Link>
            </div>

            {featuredHold ? (
              <div className="p-4 rounded-2xl bg-blue-50/60 border border-blue-100 space-y-3">
                <div className="flex items-start gap-3">
                  <div className="w-10 h-10 rounded-xl bg-blue-600 text-white flex items-center justify-center shrink-0">
                    <Pill className="w-5 h-5" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <h4 className="font-extrabold text-sm text-slate-900 truncate">
                      {featuredHold.medicine?.name}
                    </h4>
                    <p className="text-xs text-slate-500">
                      Quantity: <span className="font-bold text-slate-800">{featuredHold.quantity} unit(s)</span>
                    </p>
                  </div>
                </div>

                <div className="p-2.5 rounded-xl bg-white border border-blue-100 flex items-center justify-between text-xs">
                  <span className="font-bold text-slate-600">Hold Timer:</span>
                  <span className="font-mono font-black text-blue-600">
                    {Math.floor((featuredHold.remainingSeconds ?? 0) / 60)}:
                    {String((featuredHold.remainingSeconds ?? 0) % 60).padStart(2, '0')}
                  </span>
                </div>

                <div className="space-y-1 text-xs text-slate-600">
                  <div className="flex items-center gap-1.5 font-bold text-slate-900">
                    <Building2 className="w-3.5 h-3.5 text-blue-600" />
                    <span>{featuredHold.pharmacy?.name}</span>
                  </div>
                  <p className="text-[11px] text-slate-500 pl-5">
                    {featuredHold.pharmacy?.address}
                  </p>
                </div>

                <div className="pt-2 flex items-center gap-2">
                  <a
                    href={buildGoogleMapsDirectionsUrl(featuredHold.pharmacy)}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex-1 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold flex items-center justify-center gap-1.5 transition-colors shadow-xs"
                  >
                    <Navigation className="w-3 h-3" /> Directions
                  </a>
                  <button
                    onClick={() => handleCancelHold(featuredHold.id)}
                    className="px-3 py-2 rounded-xl text-red-600 hover:bg-red-50 text-xs font-bold transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <div className="p-6 rounded-2xl bg-slate-50 border border-dashed border-slate-200 text-center space-y-2">
                <Pill className="w-8 h-8 text-slate-300 mx-auto" />
                <p className="text-xs font-bold text-slate-700">No Active Treatment Hold</p>
                <p className="text-[11px] text-slate-400">
                  Search nearby pharmacy counters to lock stock for your required medication.
                </p>
                <Link
                  to="/search"
                  className="inline-block px-3 py-1.5 rounded-xl bg-blue-600 text-white text-xs font-bold shadow-xs"
                >
                  Find Medicine
                </Link>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default PatientDashboardView;
