import { create } from 'zustand';

export type SupportedCity = 'Puducherry' | 'Chennai' | 'Bangalore';

interface CityState {
  selectedCity: SupportedCity;
  setSelectedCity: (city: SupportedCity) => void;
}

const normalizeCity = (city: string | null): SupportedCity => {
  if (city === 'Chennai') return 'Chennai';
  if (city === 'Bangalore' || city === 'Bengaluru') return 'Bangalore';
  return 'Puducherry';
};

export const useCityStore = create<CityState>((set) => ({
  selectedCity: normalizeCity(localStorage.getItem('sentinel_city')),
  setSelectedCity: (city) => {
    localStorage.setItem('sentinel_city', city);
    set({ selectedCity: city });
  },
}));
