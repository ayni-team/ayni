import type { Schema } from '@/shared/api';

export type Benefit = Schema<'CampusBenefitResponse'>;
export type Balance = Schema<'WalletBalance'>;

function credits(count: number): string {
  return count === 1 ? '1 crédito' : `${count} créditos`;
}

/** Only earned credits are spent here: what was bought or assigned does not count. */
export function earnedAvailable(balance: Balance | undefined): number {
  return balance?.byType?.find((origin) => origin.type === 'EARNED')?.available ?? 0;
}

/** What is wrong with the amount to donate, or nothing: a whole number of at least one. */
export function donationError(value: string): string | undefined {
  const text = value.trim();
  if (!text) return 'Escribe cuántos créditos quieres donar';
  if (!/^\d+$/.test(text)) return 'Escribe un número entero de créditos';
  if (Number(text) < 1) return 'Dona al menos 1 crédito';
  return undefined;
}

/** "Te faltan 2 créditos ganados. Los comprados o asignados no cuentan." */
export function missingText(missing: number): string {
  const amount = missing === 1 ? 'Te falta 1 crédito ganado' : `Te faltan ${missing} créditos ganados`;
  return `${amount}. Los comprados o asignados no cuentan.`;
}

/** The body of a refused confirmation: the common shape plus what tells one refusal from another. */
export type Refusal = Pick<Schema<'WalletApiError'>, 'code' | 'missingCredits'>;

/**
 * What a refused confirmation means, from what the answer says: short of earned credits (`code`
 * and `missingCredits`), or a confirmation that ran out or cannot be used again. Never from the
 * balance the screen read before, which may be older than the refusal.
 */
export function refusalText(refusal: Refusal | undefined): string {
  const code = refusal?.code;
  if ((code === 'INSUFFICIENT_EARNED_CREDITS' || code === 'INSUFFICIENT_CREDITS') && refusal?.missingCredits) {
    return missingText(refusal.missingCredits);
  }
  if (code === 'CONFIRMATION_EXPIRED' || code === 'CONFIRMATION_INVALID') {
    return 'La confirmación venció. Vuelve a intentarlo.';
  }
  if (code === 'CONFIRMATION_USED') return 'Esta confirmación ya se usó. Revisa tus movimientos antes de repetirlo.';
  return 'No pudimos completarlo. Vuelve a intentarlo.';
}

export type CreditUse =
  { kind: 'REDEMPTION'; benefitId: string; name: string; credits: number } | { kind: 'DONATION'; credits: number };

/** What the dialog asks, in the web's own words: `warning` is English and is not shown. */
export function confirmationText(use: CreditUse): string {
  const spent = use.credits === 1 ? '1 crédito ganado' : `${use.credits} créditos ganados`;
  return use.kind === 'REDEMPTION'
    ? `Vas a canjear ${spent} por "${use.name}". Esto no se puede deshacer.`
    : `Vas a donar ${spent} a los estudiantes nuevos. Esto no se puede deshacer.`;
}

export function doneText(use: CreditUse): string {
  return use.kind === 'REDEMPTION'
    ? `Canjeaste "${use.name}"`
    : `Gracias: donaste ${credits(use.credits)} a los estudiantes nuevos`;
}

/** "12 créditos de 5 aportes". */
export function poolText(pool: Schema<'DonationPoolResponse'> | undefined): string {
  const contributions = pool?.contributions ?? 0;
  return `${credits(pool?.credits ?? 0)} de ${contributions === 1 ? '1 aporte' : `${contributions} aportes`}`;
}
