import { Button, IconClock, Spinner } from '@/shared/ui';

export interface HoldBannerProps {
  isHolding: boolean;
  isExpired: boolean;
  remainingMs: number;
  label: string;
  onHoldAgain: () => void;
  reholding?: boolean;
}

const HOLD_TOTAL_MS = 5 * 60 * 1000;

export function HoldBanner({
  isHolding,
  isExpired,
  remainingMs,
  label,
  onHoldAgain,
  reholding = false,
}: HoldBannerProps) {
  if (isHolding) {
    return (
      <div
        className="flex items-center gap-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3.5 text-rose-950"
        role="status"
      >
        <Spinner size="sm" className="text-rose-600" decorative />
        <span className="text-sm font-medium">Apartando la hora para ti...</span>
      </div>
    );
  }

  if (isExpired) {
    return (
      <div
        className="flex flex-col gap-3 rounded-xl border border-stone-200 bg-stone-50 p-4 text-stone-900 sm:flex-row sm:items-center sm:justify-between"
        role="alert"
      >
        <div className="flex items-start gap-3">
          <IconClock className="mt-0.5 h-5 w-5 shrink-0 text-stone-500" />
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold text-stone-900">Se acabó el tiempo</h2>
              <span className="font-mono text-xs font-bold text-stone-500">0:00</span>
            </div>
            <p className="mt-0.5 text-xs text-stone-600">
              La hora volvió a estar libre para otros estudiantes.
            </p>
          </div>
        </div>
        <Button variant="secondary" size="sm" onClick={onHoldAgain} loading={reholding}>
          Apartar de nuevo
        </Button>
      </div>
    );
  }

  const progressPercent = Math.min(100, Math.max(0, (remainingMs / HOLD_TOTAL_MS) * 100));

  return (
    <div
      className="rounded-xl border border-rose-200 bg-rose-50/70 p-4 text-rose-950"
      role="region"
      aria-label="Tiempo restante de reserva"
    >
      <div className="flex items-center justify-between gap-4">
        <div className="flex items-center gap-2">
          <IconClock className="h-5 w-5 text-rose-600" />
          <span className="text-sm font-semibold text-rose-950">Te guardamos esta hora</span>
        </div>
        <span className="font-mono text-base font-bold text-rose-700 sm:text-lg" aria-live="polite">
          {label}
        </span>
      </div>
      <div className="mt-2.5 h-1.5 w-full overflow-hidden rounded-full bg-rose-200">
        <div
          className="h-full bg-rose-600 transition-all duration-1000 ease-linear"
          style={{ width: `${progressPercent}%` }}
        />
      </div>
      <p className="mt-2 text-xs text-rose-800">
        Tienes 5 minutos para confirmar antes de que vuelva a estar libre.
      </p>
    </div>
  );
}
