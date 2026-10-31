
import { useMemo, useState } from 'react';
import { downloadFile, useApiQuery } from '@/shared/api';
import { addDays, useUniversityToday } from '@/shared/time';
import {
  Button,
  Card,
  ErrorState,
  TextField,
} from '@/shared/ui';

const INDICATORS_KEY = ['analytics', 'indicators'] as const;
const CATALOG_KEY = ['analytics', 'catalog'] as const;

function inclusiveDays(from: string, to: string): number {
  const start = Date.parse(`${from}T00:00:00Z`);
  const end = Date.parse(`${to}T00:00:00Z`);

  if (!Number.isFinite(start) || !Number.isFinite(end)) {
    return 0;
  }

  return Math.floor((end - start) / 86_400_000) + 1;
}

function formatNumber(value?: number): string {
  return (value ?? 0).toLocaleString('es-PE');
}

function MetricCard({
                      title,
                      value,
                      description,
                    }: {
  title: string;
  value?: number;
  description: string;
}) {
  return (
    <Card>
      <p className="text-sm text-ink-2">{title}</p>
      <p className="mt-2 font-display text-3xl font-semibold text-ink">
        {formatNumber(value)}
      </p>
      <p className="mt-1 text-xs text-ink-2">{description}</p>
    </Card>
  );
}

export default function IndicatorsPage() {
  const today = useUniversityToday();

  const [from, setFrom] = useState(() => addDays(today, -29));
  const [to, setTo] = useState(today);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState(false);

  const periodDays = inclusiveDays(from, to);
  const validPeriod =
    Boolean(from) &&
    Boolean(to) &&
    from <= to &&
    periodDays <= 366;

  const indicators = useApiQuery(
    [...INDICATORS_KEY, { from, to }],
    '/api/v1/coordinator/indicators',
    {
      query: { from, to },
      enabled: validPeriod,
    },
  );

  const uncoveredDemand = indicators.data?.uncoveredDemand ?? [];

  const catalog = useApiQuery(
    CATALOG_KEY,
    '/api/v1/catalog',
    {
      query: { size: 100 },
      enabled: uncoveredDemand.length > 0,
    },
  );

  const courseNames = useMemo(() => {
    const items = catalog.data?.items ?? [];

    return new Map(
      items
        .filter((item) => item.id && item.name)
        .map((item) => [item.id!, item.name!]),
    );
  }, [catalog.data?.items]);

  const totals = indicators.data?.totals;
  const days = indicators.data?.days ?? [];

  const maxHours = Math.max(
    1,
    ...days.map((day) => day.figures?.hoursExchanged ?? 0),
  );

  async function exportCsv() {
    setExporting(true);
    setExportError(false);

    try {
      await downloadFile(
        '/api/v1/coordinator/indicators/export',
        'indicadores.csv',
        {
          query: { from, to },
        },
      );
    } catch {
      setExportError(true);
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="font-display text-2xl font-semibold text-ink">
          Indicadores
        </h1>
        <p className="mt-1 text-sm text-ink-2">
          Consulta el uso de Ayni y la participación de estudiantes de tu universidad.
        </p>
      </header>

      <Card title="Periodo de consulta">
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label
              htmlFor="indicators-from"
              className="mb-1 block text-sm font-medium text-ink"
            >
              Desde
            </label>
            <TextField
              id="indicators-from"
              type="date"
              value={from}
              max={to || today}
              onChange={(event) => setFrom(event.target.value)}
            />
          </div>

          <div>
            <label
              htmlFor="indicators-to"
              className="mb-1 block text-sm font-medium text-ink"
            >
              Hasta
            </label>
            <TextField
              id="indicators-to"
              type="date"
              value={to}
              min={from}
              max={today}
              onChange={(event) => setTo(event.target.value)}
            />
          </div>
        </div>

        {!validPeriod && (
          <p className="mt-3 text-sm text-red-700" role="alert">
            {from > to
              ? 'La fecha inicial no puede ser posterior a la fecha final.'
              : periodDays > 366
                ? 'El periodo no puede superar los 366 días.'
                : 'Selecciona un periodo válido.'}
          </p>
        )}

        <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-ink-2">
            {validPeriod ? `${periodDays} días seleccionados` : 'Periodo inválido'}
          </p>

          <Button
            onClick={exportCsv}
            disabled={!validPeriod || indicators.isLoading || exporting}
            loading={exporting}
            variant="secondary"
          >
            Descargar CSV
          </Button>
        </div>

        {exportError && (
          <p className="mt-3 text-sm text-red-700" role="alert">
            No pudimos descargar el archivo. Inténtalo nuevamente.
          </p>
        )}
      </Card>

      {!validPeriod ? null : indicators.isLoading ? (
        <p className="py-8 text-center text-sm text-ink-2">
          Cargando indicadores...
        </p>
      ) : indicators.error ? (
        <ErrorState
          error={indicators.error}
          onRetry={() => void indicators.refetch()}
          retrying={indicators.isFetching}
        />
      ) : (
        <>
          <section className="space-y-3">
            <h2 className="font-display text-lg font-semibold text-ink">
              Resumen del periodo
            </h2>

            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <MetricCard
                title="Sesiones completadas"
                value={totals?.sessionsCompleted}
                description="Tutorías finalizadas"
              />

              <MetricCard
                title="Horas intercambiadas"
                value={totals?.hoursExchanged}
                description="Total de horas compartidas"
              />

              <MetricCard
                title="Estudiantes participantes"
                value={totals?.activeStudents}
                description="Estudiantes únicos durante el periodo"
              />

              <MetricCard
                title="Nuevos estudiantes"
                value={totals?.newStudents}
                description="Estudiantes incorporados"
              />

              <MetricCard
                title="Reconocimientos otorgados"
                value={totals?.recognitionsGranted}
                description="Reconocimientos concedidos"
              />
            </div>
          </section>

          <Card
            title="Actividad diaria"
            description="Horas intercambiadas por día y sesiones completadas."
          >
            {days.length === 0 ? (
              <p className="py-6 text-center text-sm text-ink-2">
                No hay actividad registrada en este periodo.
              </p>
            ) : (
              <div className="space-y-4">
                {days.map((day) => {
                  const hours = day.figures?.hoursExchanged ?? 0;
                  const sessions = day.figures?.sessionsCompleted ?? 0;
                  const width = (hours / maxHours) * 100;

                  return (
                    <div key={day.day} className="space-y-1">
                      <div className="flex justify-between gap-3 text-sm">
                        <span className="font-medium text-ink">
                          {day.day}
                        </span>
                        <span className="text-ink-2">
                          {formatNumber(hours)} h · {formatNumber(sessions)} sesiones
                        </span>
                      </div>

                      <div className="h-3 overflow-hidden rounded-full bg-gray-100">
                        <div
                          className="h-full rounded-full bg-brand"
                          style={{ width: `${width}%` }}
                          role="img"
                          aria-label={`${hours} horas intercambiadas`}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </Card>

          <Card
            title="Demanda de cursos no cubierta"
            description="Cursos más buscados para los que no se encontró una hora disponible."
          >
            {uncoveredDemand.length === 0 ? (
              <p className="py-6 text-center text-sm text-ink-2">
                No hay demanda de cursos sin cubrir en este periodo.
              </p>
            ) : (
              <div className="divide-y divide-line">
                {uncoveredDemand.map((course) => (
                  <div
                    key={course.catalogItemId}
                    className="flex items-center justify-between gap-4 py-3"
                  >
                    <span className="text-sm font-medium text-ink">
                      {courseNames.get(course.catalogItemId ?? '') ??
                        'Curso no identificado'}
                    </span>

                    <span className="shrink-0 text-sm text-ink-2">
                      {formatNumber(course.searches)} búsquedas
                    </span>
                  </div>
                ))}
              </div>
            )}

            {catalog.error && (
              <p className="mt-3 text-sm text-ink-2">
                No pudimos obtener los nombres de algunos cursos.
              </p>
            )}
          </Card>
        </>
      )}
    </div>
  );
}
