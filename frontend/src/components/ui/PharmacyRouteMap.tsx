import React from 'react';
import { Building2, MapPin, Navigation } from 'lucide-react';
import type { InventoryItem, Pharmacy } from '../../types';
import { buildGoogleMapsDirectionsUrl } from '../../utils/maps';

import { useCityStore } from '../../store/cityStore';

type MapTarget = Partial<InventoryItem> & Partial<Pharmacy>;

interface PharmacyRouteMapProps {
  target?: MapTarget | null;
  title?: string;
  subtitle?: string;
}

export const PharmacyRouteMap: React.FC<PharmacyRouteMapProps> = ({
  target,
  title = 'Pharmacy Route Map',
  subtitle = 'Select a pharmacy to preview its location and start directions.',
}) => {
  const { selectedCity } = useCityStore();
  const name = target?.pharmacyName || target?.name || `${selectedCity} emergency pharmacy network`;
  const address = target?.pharmacyAddress || target?.address || `Emergency Corridor, ${selectedCity}`;
  const city = target?.pharmacyCity || target?.city || selectedCity;
  const latitude = target?.pharmacyLatitude ?? target?.latitude;
  const longitude = target?.pharmacyLongitude ?? target?.longitude;
  const query = latitude && longitude ? `${latitude},${longitude}` : `${name}, ${city}`;

  return (
    <section className="bg-white border border-slate-200 rounded-2xl shadow-xs overflow-hidden">
      <div className="p-5 border-b border-slate-100 flex items-start justify-between gap-4">
        <div>
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
            <MapPin className="w-4 h-4 text-blue-600" />
            {title}
          </h3>
          <p className="text-xs text-slate-500 mt-1">{subtitle}</p>
        </div>
        <a
          href={buildGoogleMapsDirectionsUrl({ name, address, city, latitude, longitude })}
          target="_blank"
          rel="noreferrer"
          className="shrink-0 inline-flex items-center gap-2 px-3 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-bold"
        >
          <Navigation className="w-4 h-4" />
          Directions
        </a>
      </div>

      <div className="relative h-72 bg-slate-100">
        <iframe
          title={name}
          src={`https://maps.google.com/maps?q=${encodeURIComponent(query)}&z=15&output=embed`}
          className="absolute inset-0 h-full w-full border-0"
          loading="lazy"
          referrerPolicy="no-referrer-when-downgrade"
        />
      </div>

      <div className="p-4 bg-slate-50 border-t border-slate-100 flex items-start gap-3">
        <div className="w-9 h-9 rounded-xl bg-blue-50 border border-blue-200 flex items-center justify-center text-blue-600 shrink-0">
          <Building2 className="w-4 h-4" />
        </div>
        <div className="min-w-0">
          <p className="text-sm font-bold text-slate-900 truncate">{name}</p>
          <p className="text-xs text-slate-500">{address}, {city}</p>
        </div>
      </div>
    </section>
  );
};
