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
export function missingText(needed: number, earned: number): string {
  const missing = Math.max(0, needed - earned);
  const amount = missing === 1 ? 'Te falta 1 crédito ganado' : `Te faltan ${missing} créditos ganados`;
  return `${amount}. Los comprados o asignados no cuentan.`;
}

/**
 * What a refused confirmation means. The answer says nothing we can read, so it goes by the balance:
 * short of earned credits, or a confirmation that ran out, which lasts ten minutes.
 */
export function refusalText(needed: number, earned: number): string {
  return needed > earned ? missingText(needed, earned) : 'La confirmación venció. Vuelve a intentarlo.';
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
