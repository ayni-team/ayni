import { Button } from '@/shared/ui';

export interface TutoringsErrorStateProps {
  onRetry: () => void;
  retrying?: boolean;
}

/**
 * Slashed Wi-Fi icon matching the Figma desktop error state frame.
 */
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
      <line x1="2" x2="22" y1="2" y2="22" />
      <path d="M12 20h.01" />
      <path d="M8.5 16.429a5 5 0 0 1 7 0" />
      <path d="M5 12.859a10 10 0 0 1 5.17-2.69" />
      <path d="M19 12.859a10 10 0 0 0-2.007-1.523" />
      <path d="M2 8.82a15 15 0 0 1 4.177-2.643" />
      <path d="M22 8.82a15 15 0 0 0-11.288-3.764" />
    </svg>
  );
}

/**
 * Offline / network error card matching the Figma design for FE-12:
 * Soft circular red badge with slashed Wi-Fi, title "No pudimos cargar esto",
 * platform message, and "Reintentar" button.
 */
export function TutoringsErrorState({ onRetry, retrying = false }: TutoringsErrorStateProps) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center justify-center rounded-2xl border border-gray-200 bg-white py-16 px-6 text-center shadow-sm"
    >
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-red-50 text-red-500 mb-3">
        <IconWifiOff className="h-6 w-6" />
      </div>

      <h2 className="text-base font-semibold text-gray-900">
        No pudimos cargar esto
      </h2>

      <p className="mt-1 max-w-md text-sm text-gray-600">
        La plataforma no respondió. Lo que escribiste sigue aquí.
      </p>

      <div className="mt-4">
        <Button
          variant="secondary"
          size="md"
          onClick={onRetry}
          loading={retrying}
        >
          Reintentar
        </Button>
      </div>
    </div>
  );
}
