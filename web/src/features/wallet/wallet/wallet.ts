import type { Schema } from '@/shared/api';
import { addDays, formatInZone } from '@/shared/time';

export type Balance = Schema<'WalletBalance'>;
export type Movement = Schema<'Movement'>;
export type CreditType = NonNullable<Schema<'WalletBalanceByType'>['type']>;
export type MovementReason = NonNullable<Movement['reason']>;

/** The order of the bar and of the cards, as the frame draws them. */
export const ORIGINS: readonly CreditType[] = ['ALLOCATED', 'EARNED', 'PURCHASED', 'SEED'];

/** The colour of each origin: the same at every university, so never the brand's. */
export const ORIGIN_COLOURS: Record<CreditType, string> = {
  ALLOCATED: 'bg-credit',
  EARNED: 'bg-[#00A3DA]',
  PURCHASED: 'bg-[#E6007E]',
  SEED: 'bg-ink-3',
};

/** "Asignados por la UPC": who grants them is the university of the person. */
export function originLabel(type: CreditType, universityCode: string | undefined): string {
  switch (type) {
    case 'ALLOCATED':
      return `Asignados por ${universityCode ? `la ${universityCode}` : 'tu universidad'}`;
    case 'EARNED':
      return 'Ganados enseñando';
    case 'PURCHASED':
      return 'Comprados';
    case 'SEED':
      return 'Iniciales';
  }
}

/** The origin of a movement, in the column of the history. */
export function originShort(type: Movement['creditType']): string {
  switch (type) {
    case 'ALLOCATED':
      return 'Asignados';
    case 'EARNED':
      return 'Ganados';
    case 'PURCHASED':
      return 'Comprados';
    case 'SEED':
      return 'Iniciales';
    default:
      return '—';
  }
}

export interface OriginView {
  type: CreditType;
  available: number;
  /** Whether the credits of this origin count toward recognition: the server says, and earned ones do. */
  counts: boolean;
  /** Only false when the server says these credits have no expiry. */
  expires: boolean;
  groups: { amount: number; expiresAt: string }[];
}

/**
 * The four origins, always: the server leaves out an origin the person has no credits of, and the
 * frame draws it with "Sin créditos de este origen". Earned credits are the ones the rule counts
 * toward recognition, so that card keeps its badge at zero.
 */
export function originViews(balance: Balance | undefined): OriginView[] {
  return ORIGINS.map((type) => {
    const entry = balance?.byType?.find((candidate) => candidate.type === type);
    return {
      type,
      available: entry?.available ?? 0,
      counts: entry?.countsTowardsRecognition ?? type === 'EARNED',
      expires: entry?.expires ?? false,
      groups: (entry?.groups ?? []).flatMap((group) =>
        group.expiresAt && (group.amount ?? 0) > 0 ? [{ amount: group.amount ?? 0, expiresAt: group.expiresAt }] : [],
      ),
    };
  });
}

export interface NextExpiry {
  type: CreditType;
  amount: number;
  expiresAt: string;
}

/** What expires first, for the "Por vencer" card: nothing when no credit expires. */
export function nextExpiry(balance: Balance | undefined): NextExpiry | null {
  const groups = originViews(balance).flatMap((origin) =>
    origin.expires ? origin.groups.map((group) => ({ type: origin.type, ...group })) : [],
  );
  if (groups.length === 0) return null;
  return groups.reduce((first, group) => (group.expiresAt < first.expiresAt ? group : first));
}

/** "31 oct": the frame writes the short month without its dot. */
export function shortDay(value: string, zone: string): string {
  return formatInZone(value, zone, { day: 'numeric', month: 'short' }).replace('.', '');
}

/** "3 vencen el 31 oct", "1 vence el 15 dic". */
export function expiryLine(group: { amount: number; expiresAt: string }, zone: string): string {
  return `${group.amount} ${group.amount === 1 ? 'vence' : 'vencen'} el ${shortDay(group.expiresAt, zone)}`;
}

function originAdjective(type: CreditType, one: boolean, universityCode: string | undefined): string {
  const ending = one ? 'o' : 'os';
  switch (type) {
    case 'ALLOCATED':
      return `asignad${ending} por ${universityCode ? `la ${universityCode}` : 'tu universidad'}`;
    case 'EARNED':
      return `ganad${ending}`;
    case 'PURCHASED':
      return `comprad${ending}`;
    case 'SEED':
      return one ? 'inicial' : 'iniciales';
  }
}

/** "3 créditos asignados por la UPC vencen el 31 de octubre." */
export function expiryNotice(expiry: NextExpiry, zone: string, universityCode: string | undefined): string {
  const one = expiry.amount === 1;
  const day = formatInZone(expiry.expiresAt, zone, { day: 'numeric', month: 'long' });
  const origin = originAdjective(expiry.type, one, universityCode);
  return `${expiry.amount} ${one ? 'crédito' : 'créditos'} ${origin} ${one ? 'vence' : 'vencen'} el ${day}.`;
}

/** The reason of a movement, in Spanish; a reason the web does not know yet says it is a movement. */
const REASONS: Record<MovementReason, string> = {
  GRANT: 'Asignación de la universidad',
  BOOKING_CHARGE: 'Reserva',
  BOOKING_REFUND: 'Devolución',
  SESSION_EARNING: 'Ganaste enseñando',
  EXPIRY: 'Vencimiento',
  PURCHASE: 'Compra',
  CAMPUS_BENEFIT_REDEMPTION: 'Canje de beneficio',
  INCOMING_STUDENT_DONATION: 'Donación',
  ADJUSTMENT: 'Ajuste',
};

export function reasonText(reason: Movement['reason']): string {
  return (reason && REASONS[reason]) || 'Movimiento';
}

export const REASON_OPTIONS = (Object.keys(REASONS) as MovementReason[]).map((value) => ({
  value,
  label: REASONS[value],
}));

/** Where a movement leads, if anywhere: the guide links a booking and a purchase. */
export function movementLink(movement: Movement): { to: string; label: string } | null {
  if (!movement.referenceId) return null;
  if (movement.referenceType === 'BOOKING') return { to: '/mis-tutorias', label: 'Ver tutoría' };
  if (movement.referenceType === 'PURCHASE') {
    return { to: `/creditos/compras/${encodeURIComponent(movement.referenceId)}`, label: 'Ver compra' };
  }
  return null;
}

/** The signed amount: a debit subtracts. */
export function signedAmount(movement: Movement): number {
  const amount = movement.amount ?? 0;
  return movement.direction === 'DEBIT' ? -amount : amount;
}

export type Period = 'LAST_30' | 'LAST_90' | 'LAST_365' | 'ALL';

export const PERIOD_OPTIONS: readonly { value: Period; label: string }[] = [
  { value: 'LAST_30', label: 'Últimos 30 días' },
  { value: 'LAST_90', label: 'Últimos 90 días' },
  { value: 'LAST_365', label: 'Último año' },
  { value: 'ALL', label: 'Todo el historial' },
];

const PERIOD_DAYS: Record<Period, number | null> = { LAST_30: 30, LAST_90: 90, LAST_365: 365, ALL: null };

/**
 * The first instant of a day of the university, in UTC. The movements are filtered by instants
 * (`from` is ISO 8601 UTC, a bare day answers 400), and the day belongs to the university's zone.
 */
export function startOfDay(day: string, zone: string): string {
  const midnightUtc = new Date(`${day}T00:00:00Z`);
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: zone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(midnightUtc);
  const part = (type: string) => Number(parts.find((candidate) => candidate.type === type)?.value);
  const shownAsUtc = Date.UTC(
    part('year'),
    part('month') - 1,
    part('day'),
    part('hour'),
    part('minute'),
    part('second'),
  );
  const offset = shownAsUtc - midnightUtc.getTime();
  return new Date(midnightUtc.getTime() - offset).toISOString();
}

/** The `from` of a period, counted from the university's today; nothing when it is the whole history. */
export function periodFrom(period: Period, today: string, zone: string): string | undefined {
  const days = PERIOD_DAYS[period];
  return days === null ? undefined : startOfDay(addDays(today, -days), zone);
}
