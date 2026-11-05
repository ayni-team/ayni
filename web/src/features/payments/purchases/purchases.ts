import type { Schema } from '@/shared/api';
import type { BadgeTone } from '@/shared/ui';

export type Purchase = Schema<'PurchaseResponse'>;
export type PurchaseDetail = Schema<'PurchaseDetailResponse'>;
export type PurchaseStatus = NonNullable<Purchase['status']>;

const STATUS_BADGES: Record<PurchaseStatus, { label: string; tone: BadgeTone }> = {
  PENDING: { label: 'En proceso', tone: 'warning' },
  CONFIRMED: { label: 'Confirmada', tone: 'success' },
  FAILED: { label: 'Rechazada', tone: 'danger' },
  EXPIRED: { label: 'Vencida', tone: 'neutral' },
};

/** The badge of a status, in words; a purchase without one says so. */
export function statusBadge(status: Purchase['status']): { label: string; tone: BadgeTone } {
  return (status && STATUS_BADGES[status]) || { label: 'Sin estado', tone: 'neutral' };
}

/** What a status means for the person, on the receipt. A confirmed purchase needs no words. */
export function statusNote(status: Purchase['status']): string | null {
  switch (status) {
    case 'PENDING':
      return 'Tu pago está en proceso. No vuelvas a intentarlo.';
    case 'FAILED':
      return 'El pago fue rechazado. No se acreditaron créditos.';
    case 'EXPIRED':
      return 'El pago venció sin confirmarse. No se acreditaron créditos.';
    default:
      return null;
  }
}

/** "S/ 5.00". An absent amount is a dash; a currency the browser does not know goes as it comes. */
export function amountText(amount: number | undefined, currency: string | undefined): string {
  if (amount === undefined) return '—';
  try {
    return new Intl.NumberFormat('es-PE', { style: 'currency', currency: currency || 'PEN' }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency ?? ''}`.trim();
  }
}

/** The newest first. A purchase without a date goes last. */
export function newestFirst(purchases: readonly Purchase[]): Purchase[] {
  return [...purchases].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
}

/**
 * Where the wallet shows the movement a purchase caused: only a confirmed purchase caused one. The
 * wallet has no page per movement, so it opens on its purchases.
 */
export function movementLink(purchase: PurchaseDetail): string | null {
  return purchase.status === 'CONFIRMED' ? '/creditos?motivo=PURCHASE' : null;
}
