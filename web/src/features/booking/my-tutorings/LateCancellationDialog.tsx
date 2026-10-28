import type { Schema } from '@/shared/api';
import { addDays, dayInZone, formatInZone, todayInZone } from '@/shared/time';
import { Button, Dialog } from '@/shared/ui';

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
function getLateCancellationMessage(
  booking: Schema<'StudentBooking'>,
  timezone: string,
  refundWillBeIssued: boolean,
): string {
  const refund = refundWillBeIssued ? 'se te devolverá el crédito' : 'no se te devolverá el crédito';
  const courseName = booking.catalogItem?.name ?? 'la tutoría';
  const startsAt = booking.startsAt;
  if (!startsAt) {
    return `Si cancelas ${courseName} ${refund}.`;
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
  return `Si cancelas ${courseName} ${dayPhrase} a las ${timeStr} ${refund}.`;
}

/**
 * Confirmation dialog shown when the first DELETE attempt returns 409 Conflict with late=true.
 * It is the shared Dialog (focus trap, Escape, focus given back), not dismissible while the second
 * DELETE is on its way.
 */
export function LateCancellationDialog({
  open,
  onClose,
  onConfirm,
  booking,
  confirmation,
  timezone,
  confirming = false,
}: LateCancellationDialogProps) {
  if (!booking) return null;

  return (
    <Dialog
      open={open}
      onClose={onClose}
      dismissible={!confirming}
      title="Faltan menos de 12 horas"
      description={getLateCancellationMessage(booking, timezone, confirmation?.refundWillBeIssued === true)}
      footer={
        <>
          <Button variant="danger" onClick={onConfirm} loading={confirming}>
            Cancelar igual
          </Button>
          <Button variant="secondary" onClick={onClose} disabled={confirming}>
            No, mantenerla
          </Button>
        </>
      }
    />
  );
}
