import { IconClock, Spinner } from '@/shared/ui';

export interface HoldBannerProps {
  isHolding: boolean;
  isExpired: boolean;
  remainingMs: number;
  label: string;
}

const HOLD_TOTAL_MS = 5 * 60 * 1000;

export function HoldBanner({
  isHolding,
  isExpired,
  remainingMs,
  label,
}: HoldBannerProps) {
  if (isHolding) {
    return (
      <div
        className="flex items-center gap-3 rounded-2xl border border-gray-100 bg-white p-4 shadow-sm text-sm text-gray-700"
        role="status"
      >
        <Spinner size="sm" className="text-brand" decorative />
        <span className="font-medium">Apartando la hora para ti...</span>
      </div>
    );
  }

  if (isExpired) {
    return (
      <div
        className="rounded-2xl border border-rose-100 bg-rose-50/70 p-4 text-gray-900"
        role="alert"
      >
        <div className="grid grid-cols-[1fr_auto_1fr] items-center">
          <div className="flex items-center justify-start">
            <IconClock className="h-5 w-5 text-brand" />
          </div>
          <span className="text-sm font-semibold text-brand text-center">
            Se acabó el tiempo
          </span>
          <div className="flex items-center justify-end">
            <span className="font-mono text-base font-bold text-brand" aria-live="polite">
              0:00
            </span>
          </div>
        </div>
        <div className="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-rose-200">
          <div className="h-full bg-rose-200" style={{ width: '0%' }} />
        </div>
        <p className="mt-2 text-xs text-gray-500">
          La hora volvió a estar libre para otros estudiantes.
        </p>
      </div>
    );
  }

  const progressPercent = Math.min(100, Math.max(0, (remainingMs / HOLD_TOTAL_MS) * 100));

  return (
    <div
      className="rounded-2xl border border-rose-100 bg-rose-50/70 p-4 text-gray-900"
      role="region"
      aria-label="Tiempo restante de reserva"
    >
      <div className="grid grid-cols-[1fr_auto_1fr] items-center">
        <div className="flex items-center justify-start">
          <IconClock className="h-5 w-5 text-brand" />
        </div>
        <span className="text-sm font-semibold text-gray-900 text-center">
          Te guardamos esta hora
        </span>
        <div className="flex items-center justify-end">
          <span className="font-mono text-base font-bold text-gray-900" aria-live="polite">
            {label}
          </span>
        </div>
      </div>
      <div className="mt-3 h-1.5 w-full overflow-hidden rounded-full bg-rose-200">
        <div
          className="h-full bg-brand transition-all duration-1000 ease-linear"
          style={{ width: `${progressPercent}%` }}
        />
      </div>
      <p className="mt-2 text-xs text-gray-500">
        Si sales sin confirmar, la hora se libera para otros.
      </p>
    </div>
  );
}
