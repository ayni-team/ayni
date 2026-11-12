import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { errorText, isApiError, useApiMutation } from '@/shared/api';
import { useZone } from '@/shared/time';
import {
  Badge,
  Button,
  ButtonLink,
  Card,
  Dialog,
  IconAlert,
  IconCheck,
  IconClock,
  PageHeader,
  useToast,
} from '@/shared/ui';
import { RatingPanel } from '@/features/reputation/rate/RatingPanel';
import {
  courseName,
  durationLine,
  otherName,
  statusBadge,
  unverifiedDetail,
  waitingCloseLine,
  whenLine,
  type Session,
} from './session';

function Title({ session }: { session: Session }) {
  const zone = useZone();
  const badge = statusBadge(session.status);
  return (
    <PageHeader
      title={
        <span className="flex flex-wrap items-center gap-3">
          {courseName(session)} <Badge tone={badge.tone}>{badge.label}</Badge>
        </span>
      }
      description={whenLine(session, zone)}
    />
  );
}

/**
 * "Terminar sesión", with its confirmation. The first of the two to confirm waits for the other;
 * the second closes the session. A session already over answers 409, and the page shows how it ended.
 */
export function EndSessionButton({ session, onEnded }: { session: Session; onEnded: () => void }) {
  const toast = useToast();
  const [confirming, setConfirming] = useState(false);
  const end = useApiMutation('post', '/api/v1/sessions/{id}/end');

  async function confirm() {
    try {
      await end.mutateAsync({ path: { id: session.id ?? '' } });
      setConfirming(false);
      onEnded();
    } catch (failure) {
      setConfirming(false);
      if (isApiError(failure, 409)) onEnded();
      else toast.error(errorText(failure));
    }
  }

  return (
    <>
      <Button icon={<IconCheck className="h-4 w-4" />} onClick={() => setConfirming(true)}>
        <span className="hidden sm:inline">Terminar sesión</span>
        <span className="sm:hidden">Terminar</span>
      </Button>
      <Dialog
        open={confirming}
        onClose={() => setConfirming(false)}
        dismissible={!end.isPending}
        title="¿Terminar la sesión?"
        description={`La sesión se cierra cuando ${otherName(session)} también la termine, o sola al pasar la hora reservada.`}
        footer={
          <>
            <Button onClick={() => void confirm()} loading={end.isPending}>
              Terminar sesión
            </Button>
            <Button variant="secondary" onClick={() => setConfirming(false)} disabled={end.isPending}>
              Seguir en la sala
            </Button>
          </>
        }
      />
    </>
  );
}

/** One of the two ended it: it waits for the other, or for the job that closes it. */
export function WaitingClose({ session, onBack }: { session: Session; onBack: () => void }) {
  const zone = useZone();
  return (
    <>
      <Title session={session} />
      <Card className="max-w-3xl">
        <IconClock className="h-7 w-7 text-brand-text" />
        <h2 className="mt-3 font-display text-lg font-semibold text-ink">Terminaste la sesión</h2>
        <p className="mt-2 text-sm text-ink-2">{waitingCloseLine(session, zone)}</p>
        <Button variant="secondary" className="mt-4" onClick={onBack}>
          Volver a la sala
        </Button>
      </Card>
    </>
  );
}

/** A session that took place and closed: how it ended and, when both confirmed, the rating. */
export function ClosedRoom({ session }: { session: Session }) {
  const zone = useZone();
  const client = useQueryClient();
  const asTutor = session.role === 'TUTOR';

  // The close moves credits: the tutor earns the hour, or the student recovers it.
  useEffect(() => {
    void client.invalidateQueries({ queryKey: ['wallet'] });
  }, [client]);

  if (session.status === 'UNVERIFIED') {
    return (
      <>
        <Title session={session} />
        <Card className="max-w-3xl">
          <div className="flex items-start gap-3 rounded-xl bg-credit-soft p-4">
            <IconAlert className="mt-0.5 h-5 w-5 shrink-0 text-credit-text" />
            <div>
              <p className="text-sm font-semibold text-ink">La sesión quedó sin verificar</p>
              <p className="mt-0.5 text-[13px] text-ink-2">{unverifiedDetail(session)}</p>
            </div>
          </div>
          <p className="mt-4 text-sm text-ink-2">Una sesión sin verificar no se califica.</p>
          <div className="mt-4 flex flex-wrap gap-2">
            <ButtonLink to="/creditos" variant="secondary">
              Ver movimientos
            </ButtonLink>
            {asTutor ? (
              <ButtonLink to="/tutor/agenda">Volver a mi agenda</ButtonLink>
            ) : (
              <ButtonLink to="/buscar">Buscar otra hora</ButtonLink>
            )}
          </div>
        </Card>
      </>
    );
  }

  return (
    <>
      <Title session={session} />
      <div className="flex max-w-3xl flex-col gap-4">
        <Card>
          <p className="flex items-center gap-3 text-[15px] font-semibold text-ink">
            <IconCheck className="h-5 w-5 text-info-text" />
            Los dos confirmaron su presencia
          </p>
          <p className="mt-2 text-[13px] text-ink-2">
            {durationLine(session, zone)} Puedes repasar la pizarra cuando quieras.
          </p>
        </Card>
        <RatingPanel
          sessionId={session.id ?? ''}
          reader={asTutor ? 'TUTOR' : 'STUDENT'}
          otherName={otherName(session)}
        />
      </div>
    </>
  );
}
