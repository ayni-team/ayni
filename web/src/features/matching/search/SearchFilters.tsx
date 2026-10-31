import type { Schema } from '@/shared/api';
import { Button, IconChevronLeft, IconChevronRight, Select } from '@/shared/ui';

type CatalogItem = Schema<'CatalogItem'>;

export interface SearchFiltersProps {
  catalogItems: readonly CatalogItem[];
  selectedCatalogItemId: string;
  onCatalogItemChange: (id: string) => void;
  weekRangeText: string;
  onPrevWeek: () => void;
  onNextWeek: () => void;
  onSearchClick?: () => void;
}

export function SearchFilters({
  catalogItems,
  selectedCatalogItemId,
  onCatalogItemChange,
  weekRangeText,
  onPrevWeek,
  onNextWeek,
  onSearchClick,
}: SearchFiltersProps) {
  const options = catalogItems.map((item) => {
    const codePart = item.courseCode ? ` · ${item.courseCode}` : '';
    const label = `${item.name ?? ''}${codePart}`;
    return {
      value: item.id ?? '',
      label,
    };
  });

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm sm:p-5">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end">
        {/* Course selection */}
        <div className="flex-1 min-w-0">
          <label htmlFor="catalog-item-select" className="block text-xs font-medium text-gray-700 mb-1.5">
            Curso o habilidad
          </label>
          <Select
            id="catalog-item-select"
            value={selectedCatalogItemId}
            onChange={(e) => onCatalogItemChange(e.target.value)}
            placeholder="Escribe para buscar un curso"
            options={options}
            className="w-full text-sm"
          />
        </div>

        {/* Week selection */}
        <div className="w-full lg:w-64 shrink-0">
          <span id="semana-label" className="block text-xs font-medium text-gray-700 mb-1.5">
            Semana
          </span>
          <div
            role="group"
            aria-labelledby="semana-label"
            className="flex items-center justify-between rounded-lg border border-gray-300 bg-white px-2 py-1.5 text-sm shadow-sm focus-within:border-brand-primary focus-within:ring-1 focus-within:ring-brand-primary"
          >
            <button
              type="button"
              onClick={onPrevWeek}
              aria-label="Semana anterior"
              className="rounded p-1 text-gray-500 hover:bg-gray-100 hover:text-gray-700 focus:outline-none focus:ring-1 focus:ring-brand-primary"
            >
              <IconChevronLeft className="h-4 w-4" />
            </button>
            <span className="font-medium text-gray-900 select-none">{weekRangeText}</span>
            <button
              type="button"
              onClick={onNextWeek}
              aria-label="Semana siguiente"
              className="rounded p-1 text-gray-500 hover:bg-gray-100 hover:text-gray-700 focus:outline-none focus:ring-1 focus:ring-brand-primary"
            >
              <IconChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>

        {/* Search button on Desktop */}
        <div className="hidden lg:block shrink-0">
          <Button variant="primary" onClick={onSearchClick} disabled={!selectedCatalogItemId}>
            Buscar
          </Button>
        </div>
      </div>
    </div>
  );
}
