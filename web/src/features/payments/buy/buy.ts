import type { Schema } from '@/shared/api';
import { formatInZone } from '@/shared/time';
import { statusBadge, type Purchase } from '../purchases/purchases';

export type PurchaseAnswer = Schema<'PurchaseResponse'>;

/** What is wrong with the amount typed, or nothing: a whole number of at least one. */
export function creditsError(value: string): string | undefined {
  const text = value.trim();
  if (!text) return 'Escribe cuántos créditos quieres';
  if (!/^\d+$/.test(text)) return 'Escribe un número entero de créditos';
  if (Number(text) < 1) return 'Compra al menos 1 crédito';
  return undefined;
}

export type Outcome = {
  tone: 'info' | 'warning' | 'danger';
  title: string;
  detail?: string;
  /** A new attempt, which takes a new idempotency key. */
  canRetry: boolean;
};

function credits(count: number): string {
  return count === 1 ? '1 crédito' : `${count} créditos`;
}

/** What an answer means, in the web's own Spanish from its status: `guidance` is not shown. */
export function outcomeOf(purchase: PurchaseAnswer): Outcome {
  switch (purchase.status) {
    case 'CONFIRMED': {
      const count = purchase.credits ?? 0;
      return {
        tone: 'info',
        title: count === 1 ? 'Listo: se acreditó 1 crédito' : `Listo: se acreditaron ${credits(count)}`,
        canRetry: false,
      };
    }
    case 'FAILED':
      return {
        tone: 'danger',
        title: 'El pago fue rechazado',
        detail: 'No se te cobró. Puedes intentarlo de nuevo.',
        canRetry: true,
      };
    case 'EXPIRED':
      return {
        tone: 'danger',
        title: 'El pago venció',
        detail: 'No se te cobró. Puedes intentarlo de nuevo.',
        canRetry: true,
      };
    default:
      return {
        tone: 'warning',
        title: 'Tu pago está en proceso. No vuelvas a intentarlo',
        detail: 'Te avisaremos cuando se acredite. Revisa Mis compras en unos minutos.',
        canRetry: false,
      };
  }
}

/** "Referencia SIM-7F3A2C · 23 oct, 16:42". */
export function receiptLine(purchase: Purchase, zone: string): string {
  const when = purchase.createdAt
    ? formatInZone(purchase.createdAt, zone, {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
      }).replace('.', '')
    : null;
  return [purchase.providerReference && `Referencia ${purchase.providerReference}`, when].filter(Boolean).join(' · ');
}

export { statusBadge };

/** The refusal of the monthly limit, in the web's own words: the server's message is English. */
export const MONTHLY_LIMIT_TEXT = 'Llegaste al límite de créditos que puedes comprar este mes. Prueba con menos.';
