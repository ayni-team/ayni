import { Button, ButtonLink, IconAlert, IconCoins } from '@/shared/ui';

export type ConflictKind =
  | 'HOUR_TAKEN'
  | 'TUTOR_NOT_ENABLED'
  | 'INSUFFICIENT_CREDITS'
  | 'UNEXPECTED';

export interface BookingConflictBannerProps {
  kind: ConflictKind;
  tutorName?: string;
  hours?: number;
  availableBalance?: number;
  missingCredits?: number;
  onGoBackToSearch: () => void;
  onRetry?: () => void;
  isRetrying?: boolean;
}

function IconWifiOff({ className = 'h-6 w-6' }: { className?: string }) {
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
    >
      <line x1="2" y1="2" x2="22" y2="22" />
      <path d="M8.5 16.5a5 5 0 0 1 7 0" />
      <path d="M5 12.5a10 10 0 0 1 5.1-2.6" />
      <path d="M19 12.5a10 10 0 0 0-2.4-1.5" />
      <path d="M2 8.5a15 15 0 0 1 5.8-2" />
      <path d="M22 8.5a15 15 0 0 0-6.8-2" />
      <line x1="12" y1="20" x2="12.01" y2="20" />
    </svg>
  );
}

export function BookingConflictBanner({
  kind,
  tutorName = 'El tutor',
  hours = 1,
  availableBalance = 0,
  missingCredits,
  onGoBackToSearch,
  onRetry,
  isRetrying = false,
}: BookingConflictBannerProps) {
  if (kind === 'HOUR_TAKEN') {
    return (
      <div
        className="flex flex-col gap-3 rounded-xl border border-red-200 bg-red-50 p-4 sm:flex-row sm:items-center sm:justify-between"
        role="alert"
      >
        <div className="flex items-start gap-3">
          <IconAlert className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
          <div>
            <h2 className="text-sm font-semibold text-red-950">
              Alguien tomó esa hora. Elige otra
            </h2>
            <p className="mt-0.5 text-xs text-red-800">No se te cobró nada.</p>
          </div>
        </div>
        <Button variant="secondary" size="sm" onClick={onGoBackToSearch}>
          Volver a Buscar
        </Button>
      </div>
    );
  }

  if (kind === 'TUTOR_NOT_ENABLED') {
    return (
      <div
        className="flex flex-col gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 sm:flex-row sm:items-center sm:justify-between"
        role="alert"
      >
        <div className="flex items-start gap-3">
          <IconAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
          <div>
            <h2 className="text-sm font-semibold text-amber-950">
              {tutorName} ya no enseña este curso
            </h2>
            <p className="mt-0.5 text-xs text-amber-800">
              Elige otra hora u otro tutor. No se te cobró nada.
            </p>
          </div>
        </div>
        <Button variant="secondary" size="sm" onClick={onGoBackToSearch}>
          Volver a Buscar
        </Button>
      </div>
    );
  }

  if (kind === 'INSUFFICIENT_CREDITS') {
    const missing =
      missingCredits !== undefined && missingCredits > 0
        ? missingCredits
        : Math.max(1, hours - availableBalance);

    const costLabel = `${hours} ${hours === 1 ? 'crédito' : 'créditos'}`;
    const missingTitle =
      missing === 1 ? 'Te falta 1 crédito' : `Te faltan ${missing} créditos`;

    return (
      <div
        className="flex flex-col gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 sm:flex-row sm:items-center sm:justify-between"
        role="alert"
      >
        <div className="flex items-start gap-3">
          <IconCoins className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
          <div>
            <h2 className="text-sm font-semibold text-amber-950">{missingTitle}</h2>
            <p className="mt-0.5 text-xs text-amber-800">
              La reserva cuesta {costLabel} y tu saldo es {availableBalance}. Compra créditos y
              vuelve a confirmar.
            </p>
          </div>
        </div>
        <ButtonLink to="/creditos/comprar" variant="secondary" size="sm">
          Comprar créditos
        </ButtonLink>
      </div>
    );
  }

  // UNEXPECTED error
  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-xl border border-stone-200 bg-white px-6 py-8 text-center shadow-sm"
    >
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-stone-100 text-stone-600">
        <IconWifiOff className="h-6 w-6" />
      </div>
      <div>
        <h2 className="text-base font-semibold text-gray-900">
          No pudimos confirmar la reserva
        </h2>
        <p className="mt-1 max-w-md text-xs text-gray-600 sm:text-sm">
          No se te cobró nada. Lo que escribiste sigue aquí: vuelve a intentarlo.
        </p>
      </div>
      {onRetry && (
        <div className="mt-2">
          <Button variant="secondary" onClick={onRetry} loading={isRetrying}>
            Reintentar
          </Button>
        </div>
      )}
    </div>
  );
}
