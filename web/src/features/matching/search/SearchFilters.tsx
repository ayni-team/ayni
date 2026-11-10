import type { Schema } from '@/shared/api';
import { useUniversity } from '@/shared/brand';
import { Button, Combobox, Field, WeekStepper, type ComboboxOption } from '@/shared/ui';

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

/** "CC215 · curso UPC" for a course of the university; a global tool has no code. */
export function courseDescription(item: CatalogItem, universityCode: string | undefined): string {
  if (!item.courseCode) return 'Habilidad de todas las universidades';
  return universityCode ? `${item.courseCode} · curso ${universityCode}` : item.courseCode;
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
  const university = useUniversity();
  const options: ComboboxOption[] = catalogItems.map((item) => ({
    value: item.id ?? '',
    label: item.name ?? '',
    description: courseDescription(item, university?.code),
  }));

  return (
    <div className="rounded-[20px] border border-line bg-white p-4 sm:p-5">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end">
        <Field label="Curso o habilidad" className="min-w-0 flex-1">
          <Combobox
            options={options}
            value={selectedCatalogItemId}
            onChange={onCatalogItemChange}
            placeholder="Escribe un curso o su código"
            emptyText="Ningún curso coincide con lo que escribiste"
          />
        </Field>

        <div className="flex w-full shrink-0 flex-col gap-1.5 lg:w-64">
          <span className="text-[13px] font-semibold text-ink" aria-hidden="true">
            Semana
          </span>
          <WeekStepper label="Semana" text={weekRangeText} onPrevious={onPrevWeek} onNext={onNextWeek} />
        </div>

        <div className="hidden shrink-0 lg:block">
          <Button variant="primary" onClick={onSearchClick} disabled={!selectedCatalogItemId}>
            Buscar
          </Button>
        </div>
      </div>
    </div>
  );
}
