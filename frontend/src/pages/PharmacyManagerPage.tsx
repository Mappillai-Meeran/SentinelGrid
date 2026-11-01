import React, { useEffect, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import {
  AlertTriangle,
  Building2,
  CheckCircle2,
  Clock,
  MapPin,
  Phone,
  RefreshCw,
  ShieldCheck,
  SlidersHorizontal,
  Users,
} from 'lucide-react';
import { managerService } from '../api/services';
import type { ManagerProfile, ManagerRules, ManagerStaff, ManagerWorkspace } from '../types';
import { useToast } from '../components/ui/Toast';
import { ReservationLookupPanel } from '../components/reservations/ReservationLookupPanel';

type ManagerTab = 'profile' | 'staff' | 'rules' | 'reports';
type SaveTarget = 'profile' | 'staff' | 'rules' | null;

const newStaffDraft = (): ManagerStaff => ({
  username: '',
  role: 'Dispensing Pharmacist',
  shift: 'Morning',
  status: 'Active',
  reservationsHandled: 0,
});

const inputClass = 'w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-800 outline-none placeholder:text-slate-400 focus:border-blue-500 focus:bg-white';

export const PharmacyManagerPage: React.FC = () => {
  const { showError, showToast } = useToast();
  const [tab, setTab] = useState<ManagerTab>('profile');
  const [workspace, setWorkspace] = useState<ManagerWorkspace | null>(null);
  const [profileDraft, setProfileDraft] = useState<ManagerProfile | null>(null);
  const [rulesDraft, setRulesDraft] = useState<ManagerRules | null>(null);
  const [staffDraft, setStaffDraft] = useState<ManagerStaff>(newStaffDraft);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [saving, setSaving] = useState<SaveTarget>(null);

  const loadWorkspace = async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const data = await managerService.getWorkspace();
      setWorkspace(data);
      setProfileDraft(data.profile);
      setRulesDraft(data.rules);
    } catch (error) {
      console.error('Failed to load manager workspace', error);
      setLoadError(true);
      showError(error, 'Unable to load branch management data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadWorkspace();
  }, []);

  const refreshWorkspace = async () => {
    const data = await managerService.getWorkspace();
    setWorkspace(data);
    setProfileDraft(data.profile);
    setRulesDraft(data.rules);
  };

  const saveProfile = async () => {
    if (!profileDraft) return;
    setSaving('profile');
    try {
      const saved = await managerService.updateProfile(profileDraft);
      setWorkspace((current) => current ? { ...current, profile: saved } : current);
      setProfileDraft(saved);
      showToast('Branch profile saved', 'success');
    } catch (error) {
      showError(error, 'Unable to save branch profile');
    } finally {
      setSaving(null);
    }
  };

  const saveRules = async () => {
    if (!rulesDraft) return;
    setSaving('rules');
    try {
      const saved = await managerService.updateRules(rulesDraft);
      setRulesDraft(saved);
      await refreshWorkspace();
      showToast('Stock rules saved', 'success');
    } catch (error) {
      showError(error, 'Unable to save stock rules');
    } finally {
      setSaving(null);
    }
  };

  const saveStaff = async () => {
    if (!staffDraft.username.trim()) {
      showToast('Enter a staff username before adding coverage.', 'warning');
      return;
    }
    setSaving('staff');
    try {
      await managerService.saveStaff(staffDraft);
      setStaffDraft(newStaffDraft());
      await refreshWorkspace();
      showToast('Staff coverage added', 'success');
    } catch (error) {
      showError(error, 'Unable to add staff coverage');
    } finally {
      setSaving(null);
    }
  };

  const updateStaffStatus = async (member: ManagerStaff) => {
    if (!member.id) return;
    try {
      await managerService.updateStaffStatus(member.id, member.status === 'Active' ? 'Inactive' : 'Active');
      await refreshWorkspace();
      showToast(`${member.username} coverage updated`, 'success');
    } catch (error) {
      showError(error, 'Unable to update staff coverage');
    }
  };

  const deleteStaff = async (member: ManagerStaff) => {
    if (!member.id) return;
    try {
      await managerService.deleteStaff(member.id);
      await refreshWorkspace();
      showToast(`${member.username} removed from coverage`, 'success');
    } catch (error) {
      showError(error, 'Unable to remove staff coverage');
    }
  };

  const tabs: Array<{ id: ManagerTab; label: string; icon: React.ElementType }> = [
    { id: 'profile', label: 'Branch profile', icon: Building2 },
    { id: 'staff', label: 'Staff coverage', icon: Users },
    { id: 'rules', label: 'Stock rules', icon: SlidersHorizontal },
    { id: 'reports', label: 'Reports', icon: AlertTriangle },
  ];

  const lowStock = workspace?.reports.stockRisks ?? [];
  const statusData = workspace?.reports.reservationOutcomes ?? [];

  if (loading) {
    return <div className="mx-auto max-w-[1440px] space-y-6"><div className="h-24 animate-pulse rounded-lg bg-slate-200/70" /><div className="h-12 animate-pulse rounded-lg bg-slate-200/70" /><div className="h-80 animate-pulse rounded-lg bg-slate-200/70" /></div>;
  }

  if (loadError || !workspace) {
    return <div className="mx-auto max-w-xl rounded-lg border border-red-200 bg-white p-6 shadow-xs"><AlertTriangle className="h-6 w-6 text-red-600" /><h1 className="mt-3 text-lg font-bold text-slate-950">Branch management is unavailable</h1><p className="mt-1 text-sm text-slate-500">The manager workspace could not be loaded. Your saved branch data has not been changed.</p><button onClick={loadWorkspace} className="mt-5 inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-xs font-semibold text-white hover:bg-blue-700"><RefreshCw className="h-4 w-4" />Try again</button></div>;
  }

  return (
    <div className="mx-auto max-w-[1440px] space-y-6 pb-10">
      <header className="flex flex-col gap-4 border-b border-slate-200 pb-6 lg:flex-row lg:items-end lg:justify-between">
        <div><p className="text-xs font-bold uppercase tracking-[0.14em] text-blue-700">Pharmacy manager</p><h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950 sm:text-3xl">Branch management</h1><p className="mt-2 max-w-2xl text-sm text-slate-500">Manage branch information, staff coverage, stock rules, and operational reports.</p></div>
        <div className="flex items-center gap-3"><span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-semibold text-emerald-700"><ShieldCheck className="h-4 w-4" />{workspace.profile.verificationStatus}</span><button onClick={loadWorkspace} className="rounded-lg border border-slate-200 bg-white p-2.5 text-slate-600 shadow-xs hover:bg-slate-50" title="Refresh workspace"><RefreshCw className="h-4 w-4" /></button></div>
      </header>

      <ReservationLookupPanel scopeLabel="View reservations assigned to this pharmacy branch. Verification and pickup remain in the Pharmacist workspace." />

      <nav className="flex gap-1 overflow-x-auto rounded-lg border border-slate-200 bg-white p-1.5 shadow-xs" aria-label="Branch management sections">
        {tabs.map((item) => { const Icon = item.icon; return <button key={item.id} onClick={() => setTab(item.id)} className={`inline-flex shrink-0 items-center gap-2 rounded-md px-3 py-2 text-xs font-semibold transition-colors ${tab === item.id ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'}`}><Icon className="h-4 w-4" />{item.label}</button>; })}
      </nav>

      {tab === 'profile' && profileDraft && <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xs"><div className="flex flex-col gap-3 border-b border-slate-200 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"><div><h2 className="text-sm font-bold text-slate-950">Branch profile</h2><p className="mt-1 text-xs text-slate-500">Contact, address, service coverage, and operating information.</p></div><span className="text-sm font-semibold text-slate-800">{workspace.profile.name}</span></div><div className="grid grid-cols-1 gap-5 p-5 md:grid-cols-2">{[
        ['name', 'Pharmacy name', Building2], ['address', 'Address', MapPin], ['phone', 'Contact number', Phone], ['hours', 'Operating hours', Clock], ['coverage', 'Service coverage', ShieldCheck], ['city', 'City / PIN', MapPin],
      ].map(([field, label, Icon]) => <label key={field as string} className="block"><span className="mb-2 flex items-center gap-2 text-xs font-semibold text-slate-600"><Icon className="h-4 w-4 text-blue-600" />{label as string}</span><input value={String(profileDraft[field as keyof ManagerProfile] ?? '')} onChange={(event) => setProfileDraft({ ...profileDraft, [field as keyof ManagerProfile]: event.target.value })} className={inputClass} /></label>)}<div className="md:col-span-2 flex justify-end border-t border-slate-100 pt-5"><button onClick={saveProfile} disabled={saving === 'profile'} className="rounded-lg bg-blue-600 px-4 py-2.5 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-60">{saving === 'profile' ? 'Saving profile...' : 'Save branch profile'}</button></div></div></section>}

      {tab === 'staff' && <section className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-xs"><div className="border-b border-slate-200 px-5 py-4"><h2 className="text-sm font-bold text-slate-950">Staff coverage</h2><p className="mt-1 text-xs text-slate-500">Maintain the branch coverage roster and current shift allocation.</p></div><div className="grid grid-cols-1 gap-3 border-b border-slate-200 bg-slate-50 p-5 lg:grid-cols-[minmax(0,1fr)_180px_150px_auto]"><input value={staffDraft.username} onChange={(event) => setStaffDraft({ ...staffDraft, username: event.target.value })} placeholder="Staff username" className={inputClass} /><select value={staffDraft.role} onChange={(event) => setStaffDraft({ ...staffDraft, role: event.target.value })} className={inputClass}><option>Lead Pharmacist</option><option>Dispensing Pharmacist</option><option>Inventory Support</option></select><select value={staffDraft.shift} onChange={(event) => setStaffDraft({ ...staffDraft, shift: event.target.value })} className={inputClass}><option>Morning</option><option>Evening</option><option>Backup</option><option>Night</option></select><button onClick={saveStaff} disabled={saving === 'staff'} className="rounded-lg bg-blue-600 px-4 py-2.5 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-60">{saving === 'staff' ? 'Adding...' : 'Add coverage'}</button></div>{workspace.staff.length === 0 ? <div className="px-5 py-14 text-center"><Users className="mx-auto h-7 w-7 text-slate-300" /><p className="mt-3 text-sm font-semibold text-slate-800">No staff coverage recorded</p><p className="mt-1 text-xs text-slate-500">Add a pharmacist or inventory support member to begin tracking branch coverage.</p></div> : <div className="overflow-x-auto"><table className="w-full min-w-[720px] text-left text-sm"><thead className="bg-slate-50 text-[11px] font-bold uppercase tracking-wide text-slate-500"><tr><th className="px-5 py-3">Staff member</th><th className="px-5 py-3">Role</th><th className="px-5 py-3">Shift</th><th className="px-5 py-3 text-right">Reservations handled</th><th className="px-5 py-3">Status</th><th className="px-5 py-3 text-right">Actions</th></tr></thead><tbody className="divide-y divide-slate-100">{workspace.staff.map((member) => <tr key={member.id ?? member.username} className="hover:bg-slate-50/70"><td className="px-5 py-3.5 font-semibold text-slate-900">{member.username}</td><td className="px-5 py-3.5 text-xs text-slate-600">{member.role}</td><td className="px-5 py-3.5 text-xs text-slate-600">{member.shift}</td><td className="px-5 py-3.5 text-right font-semibold text-slate-800">{member.reservationsHandled}</td><td className="px-5 py-3.5"><span className={`rounded-full px-2.5 py-1 text-[10px] font-bold ${member.status === 'Active' ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}`}>{member.status}</span></td><td className="px-5 py-3.5 text-right"><button onClick={() => updateStaffStatus(member)} className="mr-2 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-[11px] font-semibold text-slate-700 hover:bg-slate-50">{member.status === 'Active' ? 'Deactivate' : 'Activate'}</button><button onClick={() => deleteStaff(member)} className="rounded-lg px-2.5 py-1.5 text-[11px] font-semibold text-red-700 hover:bg-red-50">Remove</button></td></tr>)}</tbody></table></div>}</section>}

      {tab === 'rules' && rulesDraft && <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs"><div className="border-b border-slate-200 pb-4"><h2 className="text-sm font-bold text-slate-950">Stock rules</h2><p className="mt-1 text-xs text-slate-500">Branch-specific limits used for holds and stock risk monitoring.</p></div><div className="mt-5 grid grid-cols-1 gap-5 md:grid-cols-3">{[
        ['holdExpiryMinutes', 'Hold expiry', 'minutes'], ['lowStockThreshold', 'Low-stock alert', 'available units'], ['maxEmergencyHoldQuantity', 'Maximum emergency hold', 'units per patient'],
      ].map(([field, label, unit]) => <label key={field} className="block"><span className="text-xs font-semibold text-slate-700">{label}</span><p className="mt-1 text-[11px] text-slate-500">{unit}</p><input type="number" min={1} value={Number(rulesDraft[field as keyof ManagerRules])} onChange={(event) => setRulesDraft({ ...rulesDraft, [field as keyof ManagerRules]: Number(event.target.value) })} className={`${inputClass} mt-3`} /></label>)}</div><div className="mt-6 flex justify-end border-t border-slate-100 pt-5"><button onClick={saveRules} disabled={saving === 'rules'} className="rounded-lg bg-blue-600 px-4 py-2.5 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-60">{saving === 'rules' ? 'Saving rules...' : 'Save stock rules'}</button></div></section>}

      {tab === 'reports' && <div className="grid grid-cols-1 gap-6 xl:grid-cols-2"><section className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs"><div><h2 className="flex items-center gap-2 text-sm font-bold text-slate-950"><AlertTriangle className="h-4 w-4 text-amber-600" />Low-stock risk</h2><p className="mt-1 text-xs text-slate-500">Medicine lines below the branch stock threshold.</p></div><div className="mt-5 h-72">{lowStock.length === 0 ? <div className="grid h-full place-items-center text-xs text-slate-400">No low-stock risks recorded.</div> : <ResponsiveContainer width="100%" height="100%"><BarChart data={lowStock}><CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" /><XAxis dataKey="medicine" tick={{ fontSize: 10, fill: '#64748b' }} interval={0} /><YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#64748b' }} /><Tooltip contentStyle={{ backgroundColor: '#ffffff', borderColor: '#e2e8f0', borderRadius: 8, fontSize: 12 }} /><Bar dataKey="available" fill="#f59e0b" radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer>}</div></section><section className="rounded-lg border border-slate-200 bg-white p-5 shadow-xs"><div><h2 className="flex items-center gap-2 text-sm font-bold text-slate-950"><CheckCircle2 className="h-4 w-4 text-emerald-600" />Reservation outcomes</h2><p className="mt-1 text-xs text-slate-500">Recorded outcomes for this branch.</p></div><div className="mt-5 h-56">{statusData.length === 0 ? <div className="grid h-full place-items-center text-xs text-slate-400">No reservation outcome data recorded.</div> : <ResponsiveContainer width="100%" height="100%"><PieChart><Pie data={statusData} cx="50%" cy="50%" innerRadius={58} outerRadius={82} paddingAngle={4} dataKey="value">{statusData.map((entry) => <Cell key={entry.name} fill={entry.color} />)}</Pie><Tooltip contentStyle={{ backgroundColor: '#ffffff', borderColor: '#e2e8f0', borderRadius: 8, fontSize: 12 }} /></PieChart></ResponsiveContainer>}</div><div className="grid grid-cols-2 gap-2 sm:grid-cols-3">{statusData.map((item) => <div key={item.name} className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-center"><p className="text-lg font-bold text-slate-900">{item.value}</p><p className="mt-1 text-[10px] font-bold uppercase text-slate-500">{item.name}</p></div>)}</div></section></div>}
    </div>
  );
};
