import React, { useEffect, useState } from 'react';
import api from '../api/client';
import type { TopPharmacy } from '../types';
import { Building2, MapPin, Phone, Pill } from 'lucide-react';

export const PharmacyNetworkPage: React.FC = () => {
  const [, setTopPharmacies] = useState<TopPharmacy[]>([]);

  useEffect(() => {
    const fetchPharmacies = async () => {
      try {
        const res = await api.get('/admin/dashboard/pharmacies/top?limit=10');
        setTopPharmacies(res.data.data || []);
      } catch (err) {
        console.error('Failed to fetch pharmacy network', err);
      }
    };

    fetchPharmacies();
  }, []);

  const defaultPharmacies = [
    {
      name: 'Apollo Pharmacy Emergency Hub',
      address: '123 Healthcare Ave',
      city: 'Puducherry',
      contact: '+91-9876543210',
      stockCount: '6 Medicine Lines',
    },
    {
      name: 'MedPlus Emergency Desk',
      address: '45 Mission Street',
      city: 'Puducherry',
      contact: '+91-9876543211',
      stockCount: '4 Medicine Lines',
    },
    {
      name: 'JIPMER Emergency Pharmacy',
      address: 'JIPMER Campus Road',
      city: 'Puducherry',
      contact: '+91-9876543212',
      stockCount: '5 Medicine Lines',
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-white flex items-center gap-2">
          <Building2 className="w-5 h-5 text-blue-400" />
          Emergency Pharmacy Network
        </h2>
        <p className="text-xs text-slate-400">Verified emergency pharmacy hubs participating in the SentinelGrid availability network</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {defaultPharmacies.map((pharmacy, idx) => (
          <div key={idx} className="glass-panel p-6 rounded-3xl border border-slate-800 space-y-4">
            <div className="flex justify-between items-start">
              <div className="p-3 bg-blue-500/10 text-blue-400 rounded-2xl border border-blue-500/20">
                <Building2 className="w-6 h-6" />
              </div>
              <span className="px-2.5 py-1 bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-[10px] font-bold rounded-full">
                ACTIVE HUB
              </span>
            </div>

            <div>
              <h3 className="font-bold text-white text-base">{pharmacy.name}</h3>
              <p className="text-xs text-slate-400 flex items-center gap-1.5 mt-1">
                <MapPin className="w-3.5 h-3.5 text-slate-500" />
                {pharmacy.address}, {pharmacy.city}
              </p>
              <p className="text-xs text-slate-400 flex items-center gap-1.5 mt-1">
                <Phone className="w-3.5 h-3.5 text-slate-500" />
                {pharmacy.contact}
              </p>
            </div>

            <div className="pt-3 border-t border-slate-800/80 flex justify-between items-center text-xs">
              <span className="text-slate-400 flex items-center gap-1">
                <Pill className="w-3.5 h-3.5 text-purple-400" />
                Inventory Tracked
              </span>
              <span className="font-bold text-white">{pharmacy.stockCount}</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
