import { useMemo, useState } from 'react';
import { downloadFile, useApiQuery } from '@/shared/api';
import { useUniversity } from '@/shared/brand';
import { useUniversityToday } from '@/shared/time';
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Field,
  PageHeader,
  Skeleton,
  Table,
  Tabs,
  TextField,
  cx,
  useToast,
  type TableColumn,
} from '@/shared/ui';
import {
  axisOf,
  dayFigures,
  defaultPeriod,
  hasActivity,
  hoursOf,
  labelEvery,
  longDayText,
  peakOf,
  periodError,
  periodErrorText,
  searchesText,
  shortDayText,
  type Indicators,
  type UsageDay,
} from './indicators';

/** The inbox of the empty states, as the frame draws it; `shared/ui` has none. */
function IconInbox({ className = 'h-6 w-6' }: { className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      <polyline points="22 12 16 12 14 15 10 15 8 12 2 12" />
      <path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
    </svg>
  );
}

const NUMBER = new Intl.NumberFormat('es-PE');

const TOTALS = [
  { key: 'sessionsCompleted', label: 'Sesiones completadas' },
  { key: 'hoursExchanged', label: 'Horas intercambiadas' },
  { key: 'activeStudents', label: 'Estudiantes activos' },
  { key: 'newStudents', label: 'Estudiantes nuevos' },
  { key: 'recognitionsGranted', label: 'Reconocimientos otorgados' },
] as const;

function Totals({ indicators }: { indicators: Indicators }) {
  return (
    <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
      {TOTALS.map((total) => (
        <div key={total.key} className="rounded-[20px] border border-line bg-white p-[18px]">
          <dt className="text-[13px] text-ink-2">{total.label}</dt>
          <dd className="mt-1 font-display text-[32px] font-semibold tabular-nums leading-tight text-ink">
            {NUMBER.format(indicators.totals?.[total.key] ?? 0)}
          </dd>
        </div>
      ))}
    </dl>
  );
}

const CHART_HEIGHT = 200;

/** One bar a day, drawn with CSS: the guide asks for simple bars and allows no new dependency. */
function Bars({ days }: { days: readonly UsageDay[] }) {
  const peak = peakOf(days);
  const axis = axisOf(peak ? hoursOf(peak) : 1);
  const every = labelEvery(days.length);
  return (
    <div className="overflow-x-auto pt-3" role="group" aria-label="Horas intercambiadas por día">
      <div className="flex" style={{ minWidth: `${Math.max(320, days.length * 18)}px` }}>
        <div
          aria-hidden="true"
          className="relative mr-2 w-6 shrink-0 text-xs text-ink-2"
          style={{ height: CHART_HEIGHT }}
        >
          <span className="absolute right-0 top-0 -translate-y-1/2">{axis.top}</span>
          <span className="absolute right-0 top-1/2 -translate-y-1/2">{axis.middle}</span>
          <span className="absolute bottom-0 right-0 translate-y-1/2">0</span>
        </div>
        <div className="min-w-0 flex-1">
          <div className="relative" style={{ height: CHART_HEIGHT }}>
            <div aria-hidden="true" className="absolute inset-x-0 top-0 border-t border-line" />
            <div aria-hidden="true" className="absolute inset-x-0 top-1/2 border-t border-line" />
            <div aria-hidden="true" className="absolute inset-x-0 bottom-0 border-t border-line" />
            <ul className="absolute inset-0 flex items-end gap-[3px] sm:gap-2">
              {days.map((day, index) => {
                const hours = hoursOf(day);
                const text = `${longDayText(day.day ?? '')}: ${dayFigures(day)}`;
                return (
                  <li key={day.day} className="group relative flex h-full flex-1 items-end justify-center">
                    <div
                      title={text}
                      className="relative w-full max-w-6 rounded-t bg-info-text"
                      style={{ height: `${(hours / axis.top) * 100}%` }}
                    >
                      {day === peak && (
                        <span
                          aria-hidden="true"
                          className="absolute inset-x-0 bottom-full mb-0.5 text-center text-xs font-semibold text-ink"
                        >
                          {hours}
                        </span>
                      )}
                    </div>
                    <span className="sr-only">{text}</span>
                    <span
                      aria-hidden="true"
                      className={cx(
                        'pointer-events-none absolute top-0 z-10 hidden whitespace-nowrap rounded-lg bg-ink px-2.5 py-1.5 text-xs text-white group-hover:block',
                        index < days.length / 2 ? 'left-full ml-1' : 'right-full mr-1',
                      )}
                    >
                      <span className="block font-semibold">{longDayText(day.day ?? '')}</span>
                      {dayFigures(day)}
                    </span>
                  </li>
                );
              })}
            </ul>
          </div>
          <div aria-hidden="true" className="mt-2 flex gap-[3px] text-xs text-ink-2 sm:gap-2">
            {days.map((day, index) => (
              <span key={day.day} className="flex-1 text-center">
                {index % every === 0 ? Number((day.day ?? '').slice(8)) : ''}
              </span>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

const DAY_COLUMNS: readonly TableColumn<UsageDay>[] = [
  { key: 'day', header: 'Día', cell: (day) => longDayText(day.day ?? '') },
  { key: 'hours', header: 'Horas', align: 'right', cell: (day) => hoursOf(day) },
  { key: 'sessions', header: 'Sesiones', align: 'right', cell: (day) => day.figures?.sessionsCompleted ?? 0 },
];

type DaysView = 'BARS' | 'TABLE';

function Daily({ days }: { days: readonly UsageDay[] }) {
  const [view, setView] = useState<DaysView>('BARS');
  const peak = peakOf(days);
  return (
    <Card
      title="Horas intercambiadas por día"
      description={`Días de la universidad${
        peak ? ` · máximo ${hoursOf(peak)} el ${shortDayText(peak.day ?? '')}` : ''
      }`}
    >
      <Tabs
        label="Cómo ver los días"
        items={[
          { value: 'BARS', label: 'Barras' },
          { value: 'TABLE', label: 'Tabla' },
        ]}
        value={view}
        onChange={setView}
      >
        {view === 'BARS' ? (
          <Bars days={days} />
        ) : (
          <Table
            caption="Horas y sesiones por día"
            columns={DAY_COLUMNS}
            rows={days}
            rowKey={(day) => day.day ?? ''}
            className="max-h-80 overflow-y-auto"
          />
        )}
      </Tabs>
    </Card>
  );
}

function UncoveredDemand({ indicators }: { indicators: Indicators }) {
  const demand = indicators.uncoveredDemand ?? [];
  // Not GET /api/v1/catalog: that route answers 403 to a coordinator.
  const catalog = useApiQuery(['analytics', 'courses'], '/api/v1/coordinator/academic-catalog/courses', {
    enabled: demand.length > 0,
  });
  const courses = useMemo(() => new Map((catalog.data ?? []).map((item) => [item.id, item])), [catalog.data]);
  const highest = Math.max(1, ...demand.map((course) => course.searches ?? 0));

  return (
    <Card
      title="Cursos que se buscan y nadie enseña"
      description="Búsquedas sin ninguna hora disponible en el periodo. Para invitar tutores de estos cursos."
    >
      {demand.length === 0 ? (
        <p className="text-sm text-ink-2">En este periodo no hubo búsquedas sin cubrir.</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {demand.map((item) => {
            const course = courses.get(item.catalogItemId);
            return (
              <li key={item.catalogItemId} className="flex flex-col gap-1 md:flex-row md:items-center md:gap-4">
                <div className="min-w-0 md:w-72 md:shrink-0">
                  <p className="truncate text-sm font-semibold text-ink">
                    {course?.name ?? (catalog.isPending ? 'Cargando…' : 'Curso sin nombre')}
                  </p>
                  {course?.code && <p className="text-xs text-ink-2">{course.code}</p>}
                </div>
                <div className="flex min-w-0 flex-1 items-center gap-2">
                  <div className="min-w-0 flex-1">
                    <div
                      aria-hidden="true"
                      className="h-2.5 rounded-full bg-info-text"
                      style={{ width: `${((item.searches ?? 0) / highest) * 100}%` }}
                    />
                  </div>
                  <span className="w-24 shrink-0 text-sm text-ink">{searchesText(item.searches)}</span>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}

/**
 * FE-53 · `/coordinador/indicadores` · coordinator.
 *
 * The use of Ayni in a period (the last 30 days by default): totals, hours by day, the courses
 * searched that nobody teaches and the CSV. No names or identifiers of people ever appear. A period
 * over 366 days is refused here, before the server does.
 */
export default function IndicatorsPage() {
  const today = useUniversityToday();
  const university = useUniversity();
  const toast = useToast();
  const [period, setPeriod] = useState(() => defaultPeriod(today));
  const [exporting, setExporting] = useState(false);
  const { from, to } = period;
  const invalid = periodError(from, to);

  const indicators = useApiQuery(['analytics', 'indicators', { from, to }], '/api/v1/coordinator/indicators', {
    query: { from, to },
    enabled: invalid === null,
  });
  const active = hasActivity(indicators.data);
  const canDownload = invalid === null && active && !exporting;

  async function exportCsv() {
    setExporting(true);
    try {
      await downloadFile('/api/v1/coordinator/indicators/export', `indicadores-${from}-${to}.csv`, {
        query: { from, to },
      });
    } catch {
      toast.error('No pudimos descargar el archivo. Inténtalo de nuevo.');
    } finally {
      setExporting(false);
    }
  }

  const download = (className: string) => (
    <Button
      variant="secondary"
      onClick={() => void exportCsv()}
      disabled={!canDownload}
      loading={exporting}
      className={className}
    >
      Descargar CSV
    </Button>
  );

  return (
    <>
      <PageHeader
        title="Indicadores"
        description={`El uso de Ayni en ${university?.code ? `la ${university.code}` : 'tu universidad'}. Sin nombres ni datos de personas.`}
        actions={
          <div className="grid w-full grid-cols-2 items-start gap-3 sm:flex sm:w-auto">
            <Field label="Desde" className="sm:w-40">
              <TextField
                type="date"
                value={from}
                max={to || today}
                onChange={(event) => setPeriod({ from: event.target.value, to })}
              />
            </Field>
            <Field label="Hasta" className="sm:w-40" error={invalid ? periodErrorText(invalid) : undefined}>
              <TextField
                type="date"
                value={to}
                max={today}
                onChange={(event) => setPeriod({ from, to: event.target.value })}
              />
            </Field>
            {download(cx('hidden sm:mt-[25px] sm:inline-flex'))}
          </div>
        }
      />
      {invalid ? (
        <EmptyState
          icon={<IconInbox className="h-8 w-8" />}
          title="Elige un periodo de hasta 366 días"
          description="El servidor no calcula periodos más largos."
        />
      ) : indicators.isPending ? (
        <div className="flex flex-col gap-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            {TOTALS.map((total) => (
              <Skeleton key={total.key} className="h-[92px] [&>div]:rounded-[20px]" label="Cargando" />
            ))}
          </div>
          <Skeleton className="h-72 [&>div]:rounded-[20px]" label="Cargando los indicadores" />
        </div>
      ) : indicators.isError || !indicators.data ? (
        <ErrorState
          title="No pudimos cargar los indicadores"
          error={indicators.error}
          onRetry={() => void indicators.refetch()}
          retrying={indicators.isFetching}
        />
      ) : !active ? (
        <EmptyState
          icon={<IconInbox className="h-8 w-8" />}
          title="Sin actividad en este periodo"
          description="Prueba con un periodo más amplio."
        />
      ) : (
        <div className="flex flex-col gap-4">
          <Totals indicators={indicators.data} />
          <Daily days={indicators.data.days ?? []} />
          <UncoveredDemand indicators={indicators.data} />
          {download('sm:hidden')}
        </div>
      )}
    </>
  );
}
