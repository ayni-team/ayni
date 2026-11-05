import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useApiQuery } from '@/shared/api';
import { useUniversity } from '@/shared/brand';
import { formatInZone, useUniversityToday, useZone } from '@/shared/time';
import {
  Badge,
  ButtonLink,
  Card,
  Credits,
  EmptyState,
  ErrorState,
  Field,
  IconClock,
  IconWallet,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  Table,
  cx,
  type TableColumn,
} from '@/shared/ui';
import {
  ORIGIN_COLOURS,
  PERIOD_OPTIONS,
  REASON_OPTIONS,
  expiryLine,
  expiryNotice,
  movementLink,
  nextExpiry,
  originLabel,
  originShort,
  originViews,
  periodFrom,
  reasonFromUrl,
  reasonText,
  shortDay,
  signedAmount,
  type Balance,
  type Movement,
  type MovementReason,
  type OriginView,
  type Period,
} from './wallet';

const PAGE_SIZE = 10;

/** The inbox of the empty state, as the frame draws it; `shared/ui` has none. */
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

/** How the available credits split by origin. The cards below say the same in words and numbers. */
function OriginBar({ origins, universityCode }: { origins: OriginView[]; universityCode?: string }) {
  const present = origins.filter((origin) => origin.available > 0);
  const label = present.map((origin) => `${originLabel(origin.type, universityCode)}: ${origin.available}`).join(', ');
  return (
    <div role="img" aria-label={label} className="mt-4 flex h-2 w-full gap-1">
      {present.map((origin) => (
        <span
          key={origin.type}
          className={cx('rounded-full', ORIGIN_COLOURS[origin.type])}
          style={{ flexGrow: origin.available }}
        />
      ))}
    </div>
  );
}

function AvailableCard({ balance, origins, universityCode }: BalanceProps) {
  const available = balance.available ?? 0;
  const earned = balance.earnedTotal ?? 0;
  return (
    <Card className="min-w-0 flex-1">
      <p className="text-[13px] font-semibold text-ink-2">Disponible</p>
      <p className="mt-2 flex flex-wrap items-baseline gap-x-3">
        <span className="font-display text-5xl font-semibold tabular-nums text-ink">{available}</span>
        <span className="text-sm text-ink-2">
          {available === 1 ? 'crédito' : 'créditos'}
          {earned > 0 && ` · ${earned} ${earned === 1 ? 'ganado' : 'ganados'} en total`}
        </span>
      </p>
      {available > 0 ? (
        <OriginBar origins={origins} universityCode={universityCode} />
      ) : (
        <div className="mt-4 flex items-start gap-3 rounded-xl bg-credit-soft p-4">
          <IconWallet className="mt-0.5 h-5 w-5 shrink-0 text-credit-text" />
          <div>
            <p className="text-sm font-semibold text-ink">Aún no tienes créditos</p>
            <p className="mt-0.5 text-[13px] text-ink-2">
              Enseña una sesión para ganar créditos que no vencen, o compra créditos.
            </p>
          </div>
        </div>
      )}
    </Card>
  );
}

function ExpiryCard({ balance, zone, universityCode }: { balance: Balance; zone: string; universityCode?: string }) {
  const expiry = nextExpiry(balance);
  if (!expiry) return null;
  return (
    <section aria-label="Por vencer" className="rounded-[20px] bg-credit-soft p-4 sm:p-6 lg:w-[380px] lg:shrink-0">
      <p className="flex items-center gap-2 text-sm font-semibold text-ink">
        <IconClock className="h-5 w-5 text-credit-text" />
        Por vencer
      </p>
      <p className="mt-2 text-sm text-ink">{expiryNotice(expiry, zone, universityCode)}</p>
      <p className="mt-2 text-xs text-ink-2">Al reservar se usan primero.</p>
    </section>
  );
}

function OriginCard({ origin, zone, universityCode }: { origin: OriginView; zone: string; universityCode?: string }) {
  return (
    <Card as="article" className="sm:p-[18px]">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-ink">
        <span aria-hidden="true" className={cx('h-2.5 w-2.5 shrink-0 rounded-full', ORIGIN_COLOURS[origin.type])} />
        {originLabel(origin.type, universityCode)}
      </h2>
      <p className="mt-2 font-display text-[28px] font-semibold tabular-nums leading-tight text-ink">
        {origin.available}
      </p>
      <div className="mt-2 space-y-1.5 text-xs">
        {origin.available === 0 ? (
          <p className="text-ink-2">Sin créditos de este origen</p>
        ) : origin.expires && origin.groups.length > 0 ? (
          origin.groups.map((group) => (
            <p key={group.expiresAt} className="text-credit-text">
              {expiryLine(group, zone)}
            </p>
          ))
        ) : (
          <p className="text-ink-2">No vencen</p>
        )}
      </div>
      {origin.counts && (
        <Badge tone="info" className="mt-3">
          Cuenta para el reconocimiento
        </Badge>
      )}
    </Card>
  );
}

interface BalanceProps {
  balance: Balance;
  origins: OriginView[];
  universityCode?: string;
}

function whenText(movement: Movement, zone: string): string {
  if (!movement.occurredAt) return '—';
  const time = formatInZone(movement.occurredAt, zone, 'time');
  return `${shortDay(movement.occurredAt, zone)} · ${time}`;
}

function MovementAmount({ movement, short }: { movement: Movement; short?: boolean }) {
  const amount = signedAmount(movement);
  const tone = amount > 0 ? 'text-info-text' : 'text-ink';
  if (short) {
    return (
      <span className={cx('font-display text-xl font-semibold tabular-nums', tone)}>
        {amount > 0 ? `+${amount}` : `−${Math.abs(amount)}`}
      </span>
    );
  }
  return <Credits amount={amount} signed className={cx('font-semibold', tone)} />;
}

function MovementLink({ movement, className }: { movement: Movement; className?: string }) {
  const link = movementLink(movement);
  if (!link) return null;
  return (
    <Link
      to={link.to}
      className={cx(
        'text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand',
        className,
      )}
    >
      {link.label}
    </Link>
  );
}

function Movements({ today, zone }: { today: string; zone: string }) {
  const [params] = useSearchParams();
  const asked = reasonFromUrl(params.get('motivo'));
  const [period, setPeriod] = useState<Period>(asked ? 'ALL' : 'LAST_30');
  const [reason, setReason] = useState<MovementReason | ''>(asked ?? '');
  const [page, setPage] = useState(0);
  const from = periodFrom(period, today, zone);
  const movements = useApiQuery(
    ['wallet', 'movements', { from, reason, page, size: PAGE_SIZE }],
    '/api/v1/wallet/movements',
    { query: { page, size: PAGE_SIZE, ...(from ? { from } : {}), ...(reason ? { reason } : {}) } },
  );
  const items = movements.data?.items ?? [];
  const filtered = period !== 'ALL' || reason !== '';

  const columns: readonly TableColumn<Movement>[] = [
    { key: 'when', header: 'Fecha', cell: (movement) => whenText(movement, zone), className: 'whitespace-nowrap' },
    { key: 'reason', header: 'Motivo', cell: (movement) => reasonText(movement.reason) },
    { key: 'origin', header: 'Origen', cell: (movement) => originShort(movement.creditType) },
    {
      key: 'amount',
      header: 'Movimiento',
      align: 'right',
      cell: (movement) => <MovementAmount movement={movement} />,
      className: 'whitespace-nowrap',
    },
    {
      key: 'link',
      header: <span className="sr-only">Detalle</span>,
      align: 'right',
      cell: (movement) => <MovementLink movement={movement} className="font-medium" />,
      className: 'whitespace-nowrap',
    },
  ];

  return (
    <section aria-labelledby="movements-title" className="mt-8">
      <h2 id="movements-title" className="font-display text-[22px] font-semibold text-ink">
        Movimientos
      </h2>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:w-[652px]">
        <Field label="Periodo">
          <Select
            value={period}
            options={PERIOD_OPTIONS}
            onChange={(event) => {
              setPeriod(event.target.value as Period);
              setPage(0);
            }}
          />
        </Field>
        <Field label="Motivo">
          <Select
            value={reason}
            placeholder="Todos"
            options={REASON_OPTIONS}
            onChange={(event) => {
              setReason(event.target.value as MovementReason | '');
              setPage(0);
            }}
          />
        </Field>
      </div>
      <div className="mt-6">
        {movements.isPending ? (
          <Skeleton lines={4} label="Cargando tus movimientos" className="[&>div]:h-12 [&>div]:rounded-xl" />
        ) : movements.isError ? (
          <ErrorState
            title="No pudimos cargar tus movimientos"
            error={movements.error}
            onRetry={() => void movements.refetch()}
            retrying={movements.isFetching}
          />
        ) : items.length === 0 ? (
          <EmptyState
            icon={<IconInbox className="h-8 w-8" />}
            title="Todavía no tienes movimientos"
            description={
              filtered
                ? 'No hay movimientos con esos filtros. Prueba otro periodo.'
                : 'Cuando reserves, enseñes o compres, aparecerán aquí.'
            }
          />
        ) : (
          <>
            <Table
              caption="Movimientos de tus créditos"
              columns={columns}
              rows={items}
              rowKey={(movement) => movement.sequenceNumber ?? `${movement.occurredAt}-${movement.reason}`}
              className="hidden rounded-[20px] md:block"
            />
            <ul className="divide-y divide-line rounded-[20px] border border-line bg-white md:hidden">
              {items.map((movement) => (
                <li
                  key={movement.sequenceNumber ?? `${movement.occurredAt}-${movement.reason}`}
                  className="flex items-center justify-between gap-3 p-4"
                >
                  <div className="min-w-0">
                    <p className="font-semibold text-ink">{reasonText(movement.reason)}</p>
                    <p className="text-xs text-ink-2">
                      {whenText(movement, zone)} · {originShort(movement.creditType)}
                    </p>
                    <MovementLink movement={movement} className="text-xs font-semibold" />
                  </div>
                  <MovementAmount movement={movement} short />
                </li>
              ))}
            </ul>
            <Pagination
              page={movements.data?.page ?? page}
              totalPages={movements.data?.totalPages ?? 0}
              onPageChange={setPage}
              label="Páginas de movimientos"
            />
          </>
        )}
      </div>
    </section>
  );
}

/**
 * FE-30 · `/creditos` · student.
 *
 * The balance, where each credit comes from and when it expires, and the history. `guidance` is
 * English and is not shown: the zero balance has a text of the web's own.
 */
export default function WalletPage() {
  const zone = useZone();
  const today = useUniversityToday();
  const university = useUniversity();
  const balance = useApiQuery(['wallet', 'balance'], '/api/v1/wallet');
  const origins = originViews(balance.data);

  return (
    <>
      <PageHeader
        title="Créditos"
        description="1 crédito = 1 hora. Al reservar se usan primero los que vencen antes."
        actions={
          <>
            <ButtonLink to="/creditos/compras" variant="ghost">
              Mis compras
            </ButtonLink>
            <ButtonLink to="/creditos/comprar">Comprar créditos</ButtonLink>
          </>
        }
      />
      {balance.isPending ? (
        <div>
          <Card>
            <Skeleton lines={3} label="Cargando tu saldo" />
          </Card>
          <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {ORIGIN_PLACEHOLDERS.map((key) => (
              <Skeleton key={key} className="h-[120px] [&>div]:rounded-[20px]" label="Cargando" />
            ))}
          </div>
        </div>
      ) : balance.isError || !balance.data ? (
        <ErrorState
          title="No pudimos cargar tus créditos"
          error={balance.error}
          onRetry={() => void balance.refetch()}
          retrying={balance.isFetching}
        />
      ) : (
        <>
          <div className="flex flex-col gap-4 lg:flex-row">
            <AvailableCard balance={balance.data} origins={origins} universityCode={university?.code} />
            <ExpiryCard balance={balance.data} zone={zone} universityCode={university?.code} />
          </div>
          <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {origins.map((origin) => (
              <OriginCard key={origin.type} origin={origin} zone={zone} universityCode={university?.code} />
            ))}
          </div>
          <Movements today={today} zone={zone} />
        </>
      )}
    </>
  );
}

const ORIGIN_PLACEHOLDERS = ['a', 'b', 'c', 'd'];
