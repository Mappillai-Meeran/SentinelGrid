import React from 'react';
import { Activity, Baby, Bandage, HeartPulse, Pill, Syringe, Wind } from 'lucide-react';

type CatalogueItem = {
  category: string;
  query: string;
  examples: string[];
  icon: React.ElementType;
};

const catalogueItems: CatalogueItem[] = [
  { category: 'Emergency injections', query: 'Adrenaline', examples: ['Adrenaline', 'Atropine', 'Hydrocortisone'], icon: Syringe },
  { category: 'Anti-venom', query: 'Anti-Venom', examples: ['Anti-Venom (VenoCure)', 'Polyvalent'], icon: Activity },
  { category: 'Respiratory', query: 'Salbutamol', examples: ['Salbutamol', 'Ipratropium'], icon: Wind },
  { category: 'Insulin', query: 'Insulin', examples: ['Soluble Insulin', 'NPH Insulin', 'Insulin Glargine'], icon: Pill },
  { category: 'Cardiac', query: 'Aspirin', examples: ['Aspirin', 'Amiodarone', 'Amlodipine'], icon: HeartPulse },
  { category: 'Oxygen and devices', query: 'Oxygen', examples: ['Oxygen Cylinder', 'MedO2'], icon: Wind },
  { category: 'Pediatric', query: 'Paracetamol', examples: ['Paracetamol oral liquid', 'Ibuprofen oral liquid'], icon: Baby },
  { category: 'First aid', query: 'Povidone Iodine', examples: ['Povidone Iodine', 'Chlorhexidine'], icon: Bandage },
];

interface EmergencyMedicineCatalogueProps {
  onSelect: (query: string) => void;
  expanded?: boolean;
}

export const EmergencyMedicineCatalogue: React.FC<EmergencyMedicineCatalogueProps> = ({ onSelect, expanded = false }) => (
  <div className={expanded ? 'grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4' : 'flex gap-2 overflow-x-auto pb-1'}>
    {catalogueItems.map((item) => {
      const Icon = item.icon;
      return (
        <button
          key={item.category}
          type="button"
          onClick={() => onSelect(item.query)}
          className={`${expanded ? 'min-h-28 items-start' : 'min-w-44'} flex shrink-0 flex-col gap-2 rounded-lg border border-slate-200 bg-white p-3 text-left transition-colors hover:border-blue-300 hover:bg-blue-50`}
        >
          <span className="flex items-center gap-2 text-xs font-semibold text-slate-800"><Icon className="h-4 w-4 text-blue-600" />{item.category}</span>
          <span className="text-[11px] leading-relaxed text-slate-500">{item.examples.join(' · ')}</span>
          <span className="mt-auto rounded bg-slate-100 px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wide text-slate-500">Demo · not live stock</span>
        </button>
      );
    })}
  </div>
);
