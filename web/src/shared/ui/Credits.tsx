import { cx } from './cx';

const NUMBER = new Intl.NumberFormat('es-PE');

/** "1 crédito", "3 créditos"; with `signed`, "+3 créditos" and "−1 crédito". A credit is an hour. */
export function formatCredits(amount: number, { signed = false }: { signed?: boolean } = {}): string {
  const unit = Math.abs(amount) === 1 ? 'crédito' : 'créditos';
  const sign = signed && amount > 0 ? '+' : amount < 0 ? '−' : '';
  return `${sign}${NUMBER.format(Math.abs(amount))} ${unit}`;
}

export interface CreditsProps {
  amount: number;
  signed?: boolean;
  className?: string;
}

export function Credits({ amount, signed, className }: CreditsProps) {
  return <span className={cx('tabular-nums', className)}>{formatCredits(amount, { signed })}</span>;
}

/** The coin of the design: credits are always yellow, whatever the university. Decorative. */
export function CreditCoin({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 20 20" className={cx('h-5 w-5 shrink-0', className)} aria-hidden="true" focusable="false">
      <circle cx="10" cy="10" r="10" fill="#FFC800" />
      <circle cx="10" cy="10" r="5.5" fill="none" stroke="#7A5E00" strokeWidth="1.5" />
    </svg>
  );
}
