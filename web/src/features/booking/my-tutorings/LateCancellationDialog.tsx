import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import type { Schema } from '@/shared/api';
import { addDays, dayInZone, formatInZone, todayInZone } from '@/shared/time';
import { Button } from '@/shared/ui';

export interface LateCancellationDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  booking: Schema<'StudentBooking'> | null;
  confirmation?: Schema<'LateCancellationConfirmation'> | null;
  timezone: string;
  confirming?: boolean;
}

/**
 * Returns the exact Spanish message shown in the Figma late cancellation dialog (409).
 * E.g. "Si cancelas Algoritmos de hoy a las 20:00 no se te devolverá el crédito."
 */
function getLateCancellationMessage(booking: Schema<'StudentBooking'>, timezone: string): string {
  const courseName = booking.catalogItem?.name ?? 'la tutoría';
  const startsAt = booking.startsAt;
  if (!startsAt) {
    return `Si cancelas ${courseName} no se te devolverá el crédito.`;
  }

  const bookingDay = dayInZone(startsAt, timezone);
  const today = todayInZone(timezone);
  const tomorrow = addDays(today, 1);

  let dayPhrase = `del ${formatInZone(startsAt, timezone, { day: 'numeric', month: 'short' })}`;
  if (bookingDay === today) {
    dayPhrase = 'de hoy';
  } else if (bookingDay === tomorrow) {
    dayPhrase = 'de mañana';
  }

  const timeStr = formatInZone(startsAt, timezone, 'time');
  return `Si cancelas ${courseName} ${dayPhrase} a las ${timeStr} no se te devolverá el crédito.`;
}

/**
 * Confirmation dialog shown when the first DELETE attempt returns 409 Conflict with late=true.
 * Matches the Figma design:
 * - Centered on mobile and desktop
 * - Rounded 3xl corners
 * - Side-by-side buttons: "No, mantenerla" and "Cancelar igual"
 * - No close 'X' icon in the corner
 */
export function LateCancellationDialog({
  open,
  onClose,
  onConfirm,
  booking,
  timezone,
  confirming = false,
}: LateCancellationDialogProps) {
  const titleId = useId();
  const descriptionId = useId();
  const panelRef = useRef<HTMLDivElement>(null);

  // Close on Escape key
  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !confirming) {
        event.stopPropagation();
        onClose();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [open, confirming, onClose]);

  if (!open || !booking) return null;

  const description = getLateCancellationMessage(booking, timezone);

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-gray-900/40 backdrop-blur-[1px] transition-opacity"
        aria-hidden="true"
        onClick={() => !confirming && onClose()}
      />

      {/* Modal Dialog Card */}
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
        tabIndex={-1}
        className="relative w-full max-w-sm sm:max-w-md rounded-3xl bg-white p-6 sm:p-8 shadow-2xl focus:outline-none"
      >
        <h2 id={titleId} className="text-xl font-bold text-gray-900 text-left">
          Faltan menos de 12 horas
        </h2>

        <p id={descriptionId} className="mt-3 text-sm text-gray-600 leading-relaxed text-left">
          {description}
        </p>

        {/* Side-by-side action buttons on mobile and desktop */}
        <div className="mt-6 sm:mt-7 flex items-center gap-3">
          <Button
            variant="secondary"
            onClick={onClose}
            disabled={confirming}
            className="flex-1 rounded-xl py-2.5 text-sm font-semibold"
          >
            No, mantenerla
          </Button>

          <Button
            variant="danger"
            onClick={onConfirm}
            loading={confirming}
            className="flex-1 rounded-xl py-2.5 text-sm font-semibold"
          >
            Cancelar igual
          </Button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
