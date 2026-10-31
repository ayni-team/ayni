
import { useState } from 'react';
import { useApiQuery } from '@/shared/api';
import { CreditCoin, Select, Pagination } from '@/shared/ui';

const ORIGIN_LABELS: Record<string, string> = {
  SEED: 'Créditos iniciales',
  ALLOCATED: 'Créditos asignados',
  EARNED: 'Créditos ganados',
  PURCHASED: 'Créditos comprados',
};

const REASON_OPTIONS = [
  { value: 'GRANT', label: 'Asignación de créditos' },
  { value: 'BOOKING_CHARGE', label: 'Reserva de tutoría' },
  { value: 'BOOKING_REFUND', label: 'Devolución de créditos' },
  { value: 'SESSION_EARNING', label: 'Tutoría realizada' },
  { value: 'EXPIRY', label: 'Vencimiento' },
  { value: 'PURCHASE', label: 'Compra de créditos' },
  { value: 'CAMPUS_BENEFIT_REDEMPTION', label: 'Canje de beneficio' },
  { value: 'INCOMING_STUDENT_DONATION', label: 'Donación recibida' },
  { value: 'ADJUSTMENT', label: 'Ajuste de saldo' },
] as const;

type MovementReason = (typeof REASON_OPTIONS)[number]['value'];

const MOVEMENT_LABELS: Record<MovementReason, string> = {
  GRANT: 'Créditos asignados',
  BOOKING_CHARGE: 'Reserva de tutoría',
  BOOKING_REFUND: 'Devolución de créditos',
  SESSION_EARNING: 'Tutoría realizada',
  EXPIRY: 'Créditos vencidos',
  PURCHASE: 'Compra de créditos',
  CAMPUS_BENEFIT_REDEMPTION: 'Canje de beneficio',
  INCOMING_STUDENT_DONATION: 'Donación recibida',
  ADJUSTMENT: 'Ajuste de saldo',
};

const DATE_FORMATTER = new Intl.DateTimeFormat('es-PE', {
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

const DATE_TIME_FORMATTER = new Intl.DateTimeFormat('es-PE', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

const PAGE_SIZE = 10;

function formatDate(value?: string | null) {
  if (!value) return 'Sin fecha';

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return 'Sin fecha';

  return DATE_FORMATTER.format(date);
}

function formatDateTime(value?: string | null) {
  if (!value) return 'Fecha no disponible';

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) return 'Fecha no disponible';

  return DATE_TIME_FORMATTER.format(date);
}

function getOriginLabel(type?: string | null) {
  if (!type) return 'Origen no especificado';

  return ORIGIN_LABELS[type] ?? type;
}

function getReasonLabel(reason?: string | null) {
  if (!reason) return 'Movimiento de créditos';

  return (
    MOVEMENT_LABELS[reason as MovementReason] ?? reason
  );
}

function movementLink(
  referenceType?: string | null,
  referenceId?: string | null,
) {
  if (!referenceType || !referenceId) return null;

  switch (referenceType) {
    case 'BOOKING':
      return '/mis-tutorias';

    case 'PURCHASE':
      return `/creditos/compras/${encodeURIComponent(referenceId)}`;

    default:
      return null;
  }
}

export default function WalletPage() {
  const [page, setPage] = useState(0);
  const [reason, setReason] = useState<MovementReason | ''>('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const {
    data: balance,
    isPending,
    isError,
    refetch,
  } = useApiQuery(['wallet', 'balance'], '/api/v1/wallet');

  const {
    data: movements,
    isPending: movementsPending,
    isError: movementsError,
    refetch: refetchMovements,
  } = useApiQuery(
    ['wallet', 'movements', { page, size: PAGE_SIZE, reason, from, to }],
    '/api/v1/wallet/movements',
    {
      query: {
        page,
        size: PAGE_SIZE,
        ...(reason ? { reason } : {}),
        ...(from ? { from } : {}),
        ...(to ? { to } : {}),
      },
    },
  );

  function handleReasonChange(value: string) {
    setReason(value as MovementReason | '');
    setPage(0);
  }

  function handleFromChange(value: string) {
    setFrom(value);
    setPage(0);
  }

  function handleToChange(value: string) {
    setTo(value);
    setPage(0);
  }

  function clearFilters() {
    setReason('');
    setFrom('');
    setTo('');
    setPage(0);
  }

  if (isPending) {
    return (
      <main className="mx-auto w-full max-w-5xl space-y-6 p-6">
        <div className="h-8 w-48 animate-pulse rounded bg-gray-200" />
        <div className="h-36 animate-pulse rounded-xl bg-gray-100" />
        <div className="h-48 animate-pulse rounded-xl bg-gray-100" />
      </main>
    );
  }

  if (isError || !balance) {
    return (
      <main className="mx-auto w-full max-w-5xl p-6">
        <h1 className="text-2xl font-bold text-gray-900">
          Mis créditos
        </h1>

        <p className="mt-4 text-sm text-gray-600">
          No pudimos cargar tu saldo de créditos.
        </p>

        <button
          type="button"
          onClick={() => void refetch()}
          className="mt-4 rounded-lg bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-700"
        >
          Reintentar
        </button>
      </main>
    );
  }

  const available = balance.available ?? 0;
  const earnedTotal = balance.earnedTotal ?? 0;
  const origins = balance.byType ?? [];
  const movementItems = movements?.items ?? [];

  return (
    <main className="mx-auto w-full max-w-5xl space-y-8 p-6">
      {/* Encabezado */}
      <header>
        <h1 className="text-2xl font-bold text-gray-900">
          Mis créditos
        </h1>

        <p className="mt-2 text-sm text-gray-600">
          Consulta tu saldo, el origen de tus créditos y tus movimientos.
        </p>
      </header>

      {/* Saldo principal */}
      <section className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm font-medium text-gray-500">
          Créditos disponibles
        </p>

        <div className="mt-3 flex items-center gap-3">
          <CreditCoin />

          <span className="text-4xl font-bold tracking-tight text-gray-900">
            {available}
          </span>
        </div>

        <div className="mt-5 border-t border-gray-100 pt-4">
          <p className="text-sm text-gray-500">
            Total de créditos ganados
          </p>

          <p className="mt-1 text-lg font-semibold text-gray-900">
            {earnedTotal}
          </p>
        </div>
      </section>

      {/* Desglose de créditos */}
      {available === 0 ? (
        <section className="rounded-xl border border-dashed border-gray-300 p-6 text-center">
          <h2 className="font-semibold text-gray-900">
            Aún no tienes créditos
          </h2>

          <p className="mt-2 text-sm text-gray-500">
            Cuando obtengas créditos, podrás consultar aquí tu saldo y sus
            fechas de vencimiento.
          </p>
        </section>
      ) : (
        <section className="space-y-4">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">
              Desglose de créditos
            </h2>

            <p className="mt-1 text-sm text-gray-500">
              Consulta de dónde provienen tus créditos y cuándo vencen.
            </p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            {origins.map((origin, index) => (
              <article
                key={origin.type ?? index}
                className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h3 className="font-semibold text-gray-900">
                      {getOriginLabel(origin.type)}
                    </h3>

                    <p className="mt-1 text-2xl font-bold text-gray-900">
                      {origin.available ?? 0}
                    </p>
                  </div>

                  <CreditCoin />
                </div>

                {origin.countsTowardsRecognition && (
                  <p className="mt-3 text-xs font-medium text-green-700">
                    Cuenta para el reconocimiento universitario
                  </p>
                )}

                <div className="mt-4 space-y-3 border-t border-gray-100 pt-4">
                  {(origin.groups ?? []).map((group, groupIndex) => (
                    <div
                      key={`${origin.type ?? 'origin'}-${groupIndex}`}
                      className="flex items-center justify-between gap-3 text-sm"
                    >
                      <span className="text-gray-600">
                        {group.amount ?? 0} créditos
                      </span>

                      <span className="text-right text-gray-500">
                        {origin.expires === false
                          ? 'No vencen'
                          : group.expiresAt
                            ? `Vence: ${formatDate(group.expiresAt)}`
                            : 'Sin fecha de vencimiento'}
                      </span>
                    </div>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </section>
      )}

      {/* Historial de movimientos */}
      <section className="space-y-5">
        <div>
          <h2 className="text-lg font-semibold text-gray-900">
            Historial de movimientos
          </h2>

          <p className="mt-1 text-sm text-gray-500">
            Consulta los créditos que has recibido y utilizado.
          </p>
        </div>

        {/* Filtros */}
        <div className="grid gap-4 rounded-xl border border-gray-200 bg-white p-4 sm:grid-cols-3">
          <div>
            <label
              htmlFor="movement-reason"
              className="mb-2 block text-sm font-medium text-gray-700"
            >
              Tipo de movimiento
            </label>

            <Select
              id="movement-reason"
              className="w-full"
              value={reason}
              onChange={(event) => handleReasonChange(event.target.value)}
              options={REASON_OPTIONS}
              placeholder="Todos los movimientos"
            />
          </div>

          <div>
            <label
              htmlFor="movement-from"
              className="mb-2 block text-sm font-medium text-gray-700"
            >
              Desde
            </label>

            <input
              id="movement-from"
              type="date"
              value={from}
              onChange={(event) => handleFromChange(event.target.value)}
              className="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm text-gray-700 focus:border-gray-900 focus:outline-none focus:ring-2 focus:ring-gray-200"
            />
          </div>

          <div>
            <label
              htmlFor="movement-to"
              className="mb-2 block text-sm font-medium text-gray-700"
            >
              Hasta
            </label>

            <input
              id="movement-to"
              type="date"
              value={to}
              min={from || undefined}
              onChange={(event) => handleToChange(event.target.value)}
              className="h-11 w-full rounded-lg border border-gray-300 px-3 text-sm text-gray-700 focus:border-gray-900 focus:outline-none focus:ring-2 focus:ring-gray-200"
            />
          </div>
        </div>

        {/* Resultados */}
        {movementsPending ? (
          <div className="space-y-3">
            {[1, 2, 3].map((item) => (
              <div
                key={item}
                className="h-20 animate-pulse rounded-xl bg-gray-100"
              />
            ))}
          </div>
        ) : movementsError ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5">
            <p className="text-sm text-red-700">
              No pudimos cargar el historial de movimientos.
            </p>

            <button
              type="button"
              onClick={() => void refetchMovements()}
              className="mt-3 text-sm font-semibold text-red-800 underline"
            >
              Reintentar
            </button>
          </div>
        ) : movementItems.length === 0 ? (
          <div className="rounded-xl border border-dashed border-gray-300 p-8 text-center">
            <h3 className="font-semibold text-gray-900">
              No hay movimientos
            </h3>

            <p className="mt-2 text-sm text-gray-500">
              No encontramos movimientos para los filtros seleccionados.
            </p>

            {(reason || from || to) && (
              <button
                type="button"
                onClick={clearFilters}
                className="mt-4 text-sm font-semibold text-gray-900 underline"
              >
                Limpiar filtros
              </button>
            )}
          </div>
        ) : (
          <>
            <div className="divide-y divide-gray-100 rounded-xl border border-gray-200 bg-white">
              {movementItems.map((movement, index) => {
                const isCredit = movement.direction === 'CREDIT';

                const href = movementLink(
                  movement.referenceType,
                  movement.referenceId,
                );

                return (
                  <article
                    key={movement.sequenceNumber ?? index}
                    className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div className="flex min-w-0 items-start gap-3">
                      <div
                        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${
                          isCredit
                            ? 'bg-green-50 text-green-700'
                            : 'bg-red-50 text-red-700'
                        }`}
                      >
                        <CreditCoin />
                      </div>

                      <div className="min-w-0">
                        <h3 className="font-medium text-gray-900">
                          {getReasonLabel(movement.reason)}
                        </h3>

                        <p className="mt-1 text-sm text-gray-500">
                          {formatDateTime(movement.occurredAt)}
                        </p>

                        {movement.creditType && (
                          <p className="mt-1 text-xs text-gray-500">
                            {getOriginLabel(movement.creditType)}
                          </p>
                        )}

                        {href && (
                          <a
                            href={href}
                            className="mt-1 inline-block text-xs font-medium text-blue-700 hover:underline"
                          >
                            Ver detalle
                          </a>
                        )}
                      </div>
                    </div>

                    <p
                      className={`shrink-0 text-base font-semibold tabular-nums ${
                        isCredit ? 'text-green-700' : 'text-red-700'
                      }`}
                    >
                      {isCredit ? '+' : '-'}
                      {movement.amount ?? 0} créditos
                    </p>
                  </article>
                );
              })}
            </div>

            <Pagination
              page={movements?.page ?? page}
              totalPages={movements?.totalPages ?? 0}
              onPageChange={setPage}
              label="Páginas del historial de movimientos"
            />
          </>
        )}
      </section>
    </main>
  );
}
