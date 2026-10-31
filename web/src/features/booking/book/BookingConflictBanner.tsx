import { Button, IconAlert } from '@/shared/ui';

export type ConflictKind = 'HOUR_TAKEN' | 'TUTOR_NOT_ENABLED' | 'INSUFFICIENT_CREDITS' | 'UNEXPECTED';

export interface BookingConflictBannerProps {
  kind: ConflictKind;
  tutorName?: string;
  hours?: number;
  availableBalance?: number;
  missingCredits?: number;
  onGoBackToSearch?: () => void;
  onRetry?: () => void;
  isRetrying?: boolean;
}

function IconWallet({ className = 'h-5 w-5' }: { className?: string }) {
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
      <path d="M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1" />
      <path d="M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4" />
    </svg>
  );
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
  onRetry,
  isRetrying = false,
}: BookingConflictBannerProps) {
  if (kind === 'HOUR_TAKEN') {
    return (
      <div className="flex items-start gap-3 rounded-2xl border border-rose-100 bg-rose-50/70 p-4" role="alert">
        <IconAlert className="mt-0.5 h-5 w-5 shrink-0 text-red-600" />
        <div>
          <h2 className="text-sm font-semibold text-gray-900">Alguien tomó esa hora. Elige otra</h2>
          <p className="mt-0.5 text-xs text-gray-500">No se te cobró nada.</p>
        </div>
      </div>
    );
  }

  if (kind === 'TUTOR_NOT_ENABLED') {
    return (
      <div className="flex items-start gap-3 rounded-2xl border border-amber-100 bg-amber-50 p-4" role="alert">
        <IconAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-600" />
        <div>
          <h2 className="text-sm font-semibold text-gray-900">{tutorName} ya no enseña este curso</h2>
          <p className="mt-0.5 text-xs text-gray-500">Elige otra hora u otro tutor. No se te cobró nada.</p>
        </div>
      </div>
    );
  }

  if (kind === 'INSUFFICIENT_CREDITS') {
    const missing =
      missingCredits !== undefined && missingCredits > 0 ? missingCredits : Math.max(1, hours - availableBalance);

    const costLabel = `${hours} ${hours === 1 ? 'crédito' : 'créditos'}`;
    const missingTitle = missing === 1 ? 'Te falta 1 crédito' : `Te faltan ${missing} créditos`;

    return (
      <div className="flex items-start gap-3 rounded-2xl border border-amber-200/80 bg-amber-50/80 p-4" role="alert">
        <IconWallet className="mt-0.5 h-5 w-5 shrink-0 text-amber-800" />
        <div>
          <h2 className="text-sm font-semibold text-gray-900">{missingTitle}</h2>
          <p className="mt-0.5 text-xs text-gray-600">
            La reserva cuesta {costLabel} y tu saldo es {availableBalance}. Compra créditos y vuelve a confirmar.
          </p>
        </div>
      </div>
    );
  }

  // UNEXPECTED error
  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-2xl border border-gray-100 bg-white p-6 text-center shadow-sm"
    >
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-rose-50 text-red-600">
        <IconWifiOff className="h-6 w-6" />
      </div>
      <div>
        <h2 className="text-base font-bold text-gray-900">No pudimos confirmar la reserva</h2>
        <p className="mt-1 max-w-sm text-xs text-gray-500">
          No se te cobró nada. Lo que escribiste sigue aquí: vuelve a intentarlo.
        </p>
      </div>
      {onRetry && (
        <div className="mt-1">
          <Button
            variant="secondary"
            onClick={onRetry}
            loading={isRetrying}
            className="rounded-xl px-4 py-2 text-sm shadow-sm"
          >
            Reintentar
          </Button>
        </div>
      )}
    </div>
  );
}
