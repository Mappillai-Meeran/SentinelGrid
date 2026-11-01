import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { dashboardService } from '../api/services';
import type { TopPharmacy } from '../types';
import { Building2, Clock, Handshake, MapPin, Navigation, Phone, Pill } from 'lucide-react';
import { GlassCard } from '../components/ui/GlassCard';
import { SectionHeader } from '../components/ui/SectionHeader';
import { StatusBadge } from '../components/ui/StatusBadge';
import { PharmacyRouteMap } from '../components/ui/PharmacyRouteMap';
import { buildGoogleMapsDirectionsUrl } from '../utils/maps';
import { useCityStore, type SupportedCity } from '../store/cityStore';

export const PharmacyNetworkPage: React.FC = () => {
  const [, setTopPharmacies] = useState<TopPharmacy[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const { selectedCity, setSelectedCity } = useCityStore();

  useEffect(() => {
    const fetchPharmacies = async () => {
      try {
        const data = await dashboardService.getTopPharmacies(10);
        setTopPharmacies(data);
      } catch (err) {
        console.error('Failed to fetch pharmacy network', err);
      }
    };

    fetchPharmacies();
  }, []);

  useEffect(() => {
    setSelectedIndex(0);
  }, [selectedCity]);

  const pharmaciesByCity = {
    Puducherry: [
      {
        name: 'Apollo Pharmacy M.G. Road',
        address: 'No.94, M.G. Road, Heritage Town',
        city: 'Puducherry',
        contact: '0413-2341000',
        stockCount: '6 Medicine Lines',
        hours: '09:00 AM - 11:00 PM',
        distance: '1.2 km from IGGGH',
        zone: 'MG Road',
        latitude: 11.9356,
        longitude: 79.8337,
      },
      {
        name: 'Mission Street Emergency Pharmacy',
        address: 'Mission Street, Heritage Town',
        city: 'Puducherry',
        contact: '+91-9876543211',
        stockCount: '4 Medicine Lines',
        hours: '08:30 AM - 10:30 PM',
        distance: '1.5 km from IGGGH',
        zone: 'Mission Street',
        latitude: 11.9324,
        longitude: 79.8308,
      },
      {
        name: 'Rajiv Gandhi WCH Casualty Pharmacy',
        address: '100 Feet Road, Ellaipillaichavady',
        city: 'Puducherry',
        contact: '+91-9876543212',
        stockCount: '5 Medicine Lines',
        hours: 'Casualty support: 24x7',
        distance: '3.8 km from IGGGH',
        zone: 'Women & Children Hospital',
        latitude: 11.9391,
        longitude: 79.8056,
      },
    ],
    Chennai: [
      {
        name: 'Apollo Pharmacy Anna Nagar',
        address: 'No.140, A1 Block, Anna Nagar',
        city: 'Chennai',
        contact: '044-26221223',
        stockCount: '5 Medicine Lines',
        hours: '09:00 AM - 11:00 PM',
        distance: 'Anna Nagar emergency corridor',
        zone: 'Anna Nagar',
        latitude: 13.0843,
        longitude: 80.2106,
      },
      {
        name: 'Apollo Pharmacy Perambur 2',
        address: 'Old No.36, New No.89, Vavidelu Mudali Street, Perambur',
        city: 'Chennai',
        contact: '044-25510550',
        stockCount: '4 Medicine Lines',
        hours: '00:00 AM - 12:00 PM',
        distance: 'Perambur access zone',
        zone: 'Perambur',
        latitude: 13.1182,
        longitude: 80.2329,
      },
      {
        name: 'Apollo Pharmacy Anna Nagar 2nd Avenue',
        address: 'Shop No.1, C/32, 2nd Avenue, Anna Nagar',
        city: 'Chennai',
        contact: '044-26200272',
        stockCount: '4 Medicine Lines',
        hours: '07:00 AM - 10:00 PM',
        distance: 'Anna Nagar 2nd Avenue',
        zone: '2nd Avenue',
        latitude: 13.0850,
        longitude: 80.2181,
      },
    ],
    Bangalore: [
      {
        name: 'Apollo Pharmacy Indira Nagar',
        address: '119, Ground Floor, 10th Cross, 1st Stage, Indira Nagar',
        city: 'Bangalore',
        contact: '080-41020665',
        stockCount: '5 Medicine Lines',
        hours: '07:00 AM - 11:00 PM',
        distance: 'Indira Nagar access zone',
        zone: 'Indira Nagar',
        latitude: 12.9784,
        longitude: 77.6408,
      },
      {
        name: 'Apollo Pharmacy HSR Layout',
        address: 'No.1713, Ground Floor, HSR Layout, Sector 2',
        city: 'Bangalore',
        contact: '080-41020639',
        stockCount: '4 Medicine Lines',
        hours: '07:00 AM - 11:00 PM',
        distance: 'HSR emergency corridor',
        zone: 'HSR Layout',
        latitude: 12.9121,
        longitude: 77.6446,
      },
      {
        name: 'Apollo Pharmacy Banashankari',
        address: 'Plot No.5, 50 Feet Main Road, Nagendra Block, Banashankari 1st Stage',
        city: 'Bangalore',
        contact: '080-41020683',
        stockCount: '4 Medicine Lines',
        hours: '07:00 AM - 11:00 PM',
        distance: 'Banashankari access zone',
        zone: 'Banashankari',
        latitude: 12.9335,
        longitude: 77.5468,
      },
    ],
    Hyderabad: [
      {
        name: 'Apollo Pharmacy HITEC City',
        address: 'Ground Floor, Cyber Towers Road, HITEC City',
        city: 'Hyderabad',
        contact: '040-23110100',
        stockCount: '6 Medicine Lines',
        hours: '08:00 AM - 11:30 PM',
        distance: 'HITEC IT corridor',
        zone: 'HITEC City',
        latitude: 17.4435,
        longitude: 78.3772,
      },
      {
        name: 'Apollo Pharmacy Banjara Hills',
        address: 'Road No. 12, Banjara Hills',
        city: 'Hyderabad',
        contact: '040-23607777',
        stockCount: '5 Medicine Lines',
        hours: '24x7 Emergency Line',
        distance: 'Banjara specialty hub',
        zone: 'Banjara Hills',
        latitude: 17.4156,
        longitude: 78.4347,
      },
      {
        name: 'Apollo Pharmacy Jubilee Hills',
        address: 'Road No. 36, Jubilee Hills',
        city: 'Hyderabad',
        contact: '040-23550550',
        stockCount: '4 Medicine Lines',
        hours: '07:00 AM - 11:00 PM',
        distance: 'Jubilee health corridor',
        zone: 'Jubilee Hills',
        latitude: 17.4319,
        longitude: 78.4071,
      },
    ],
    Coimbatore: [
      {
        name: 'Apollo Pharmacy Race Course',
        address: 'No.18, Race Course Road, Gopalapuram',
        city: 'Coimbatore',
        contact: '0422-2221100',
        stockCount: '5 Medicine Lines',
        hours: '08:00 AM - 10:30 PM',
        distance: 'Race Course hub',
        zone: 'Race Course',
        latitude: 11.0014,
        longitude: 76.9698,
      },
      {
        name: 'Apollo Pharmacy R.S. Puram',
        address: 'DB Road, R.S. Puram',
        city: 'Coimbatore',
        contact: '0422-2470055',
        stockCount: '4 Medicine Lines',
        hours: '07:30 AM - 11:00 PM',
        distance: 'RS Puram corridor',
        zone: 'R.S. Puram',
        latitude: 11.0085,
        longitude: 76.9517,
      },
      {
        name: 'Apollo Pharmacy Peelamedu',
        address: 'Avinashi Road, Peelamedu',
        city: 'Coimbatore',
        contact: '0422-2570088',
        stockCount: '6 Medicine Lines',
        hours: '24x7 Emergency Line',
        distance: 'KMCH / Avinashi corridor',
        zone: 'Peelamedu',
        latitude: 11.0267,
        longitude: 76.9995,
      },
    ],
  };

  const defaultPharmacies = pharmaciesByCity[selectedCity] || pharmaciesByCity.Puducherry;

  return (
    <div className="space-y-6">
      <SectionHeader
        title={`${selectedCity} Emergency Pharmacy Network`}
        subtitle="Demo network anchored around real pharmacy corridors for supported cities"
        icon={Building2}
      />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          {(['Puducherry', 'Chennai', 'Bangalore'] as SupportedCity[]).map((city) => (
            <button
              key={city}
              onClick={() => {
                setSelectedCity(city);
              }}
              className={`px-4 py-2 rounded-xl text-xs font-bold border transition-colors ${
                selectedCity === city
                  ? 'bg-blue-600 text-white border-blue-600 shadow-sm'
                  : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'
              }`}
            >
              {city}
            </button>
          ))}
        </div>

        <Link
          to="/pharmacy-partnership"
          className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold text-blue-700 bg-blue-50 border border-blue-200/80 hover:bg-blue-100 transition-colors"
        >
          <Handshake className="w-3.5 h-3.5 text-blue-600" />
          <span>Apply to Join Network</span>
        </Link>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1fr)_420px] gap-6 items-start">
        <PharmacyRouteMap
          target={defaultPharmacies[selectedIndex]}
          title={`${selectedCity} Pharmacy Map`}
          subtitle="Select a participating emergency hub and open directions."
        />

        <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs">
          <h3 className="text-sm font-bold text-slate-900">Coverage Corridor</h3>
          <p className="text-xs text-slate-500 mt-1">{defaultPharmacies.map((pharmacy) => pharmacy.zone).join(', ')}.</p>
          <div className="grid grid-cols-2 gap-3 mt-5">
            <div className="rounded-xl bg-blue-50 border border-blue-100 p-3">
              <p className="text-xl font-black text-blue-700">3</p>
              <p className="text-[10px] font-bold uppercase text-blue-600">Verified hubs</p>
            </div>
            <div className="rounded-xl bg-emerald-50 border border-emerald-100 p-3">
              <p className="text-xl font-black text-emerald-700">15</p>
              <p className="text-[10px] font-bold uppercase text-emerald-600">Tracked lines</p>
            </div>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {defaultPharmacies.map((pharmacy, idx) => (
          <GlassCard
            key={pharmacy.name}
            className={`space-y-4 cursor-pointer ${selectedIndex === idx ? 'ring-2 ring-blue-200 border-blue-300' : ''}`}
          >
            <button
              type="button"
              onClick={() => setSelectedIndex(idx)}
              className="w-full text-left space-y-4"
            >
            <div className="flex justify-between items-start">
              <div className="p-3 bg-blue-50 text-blue-600 rounded-2xl border border-blue-200/60">
                <Building2 className="w-6 h-6" />
              </div>
              <StatusBadge status="IN_STOCK" />
            </div>

            <div>
              <h3 className="font-bold text-slate-900 text-base">{pharmacy.name}</h3>
              <p className="text-xs text-slate-500 flex items-center gap-1.5 mt-1">
                <MapPin className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                {pharmacy.address}, {pharmacy.city}
              </p>
              <p className="text-xs text-slate-500 flex items-center gap-1.5 mt-1">
                <Phone className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                {pharmacy.contact}
              </p>
              <p className="text-xs text-slate-500 flex items-center gap-1.5 mt-1">
                <Clock className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                {pharmacy.hours}
              </p>
            </div>

            <div className="pt-3 border-t border-slate-100 flex justify-between items-center text-xs">
              <span className="text-slate-500 flex items-center gap-1">
                <Pill className="w-3.5 h-3.5 text-purple-500" />
                Inventory Tracked
              </span>
              <span className="font-bold text-slate-800">{pharmacy.stockCount}</span>
            </div>
            <div className="text-[10px] font-bold text-blue-700 bg-blue-50 border border-blue-100 rounded-xl px-3 py-2">
              {pharmacy.distance}
            </div>
            </button>
            <a
              href={buildGoogleMapsDirectionsUrl(pharmacy)}
              target="_blank"
              rel="noreferrer"
              className="w-full inline-flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-xl text-xs"
            >
              <Navigation className="w-4 h-4" />
              Start Directions
            </a>
          </GlassCard>
        ))}
      </div>
    </div>
  );
};
