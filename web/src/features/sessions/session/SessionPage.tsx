import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { isApiError } from '@/shared/api';
import { ButtonLink, Card, ErrorState, Skeleton } from '@/shared/ui';
import { ClosedRoom, EndSessionButton, WaitingClose } from './Closing';
import { LiveRoom } from './LiveRoom';
import { RoomSummary } from './RoomSummary';
import { useSessionRoom } from './useSessionRoom';

function Loading() {
  return (
    <div className="flex flex-col gap-4">
      <Skeleton lines={2} label="Cargando la sesión" className="max-w-lg" />
      <Card>
        <Skeleton lines={4} label="Cargando" />
      </Card>
    </div>
  );
}

/**
 * FE-20 to FE-23 · `/sesion/:id` · student, tutor.
 *
 * One page for the whole life of a session, chosen by its state (`phaseOf`): before the room opens,
 * open, waiting for the other person, in progress and over.
 */
export default function SessionPage() {
  const { id = '' } = useParams<{ id: string }>();
  const { query, session, phase, joined, enter, entering } = useSessionRoom(id);
  const [backInRoom, setBackInRoom] = useState(false);

  if (query.isPending) return <Loading />;

  if (!session || !phase) {
    const missing = isApiError(query.error) && query.error.status === 404;
    const forbidden = isApiError(query.error) && query.error.status === 403;
    return (
      <ErrorState
        title={
          missing ? 'No encontramos esta sesión' : forbidden ? 'Esta sesión no es tuya' : 'No pudimos cargar la sesión'
        }
        description={missing ? 'Puede que el enlace esté mal. Búscala en tus tutorías.' : undefined}
        error={query.error}
        onRetry={missing || forbidden ? undefined : () => void query.refetch()}
        retrying={query.isFetching}
        action={
          missing || forbidden ? (
            <ButtonLink to="/mis-tutorias" variant="secondary">
              Ir a mis tutorías
            </ButtonLink>
          ) : undefined
        }
      />
    );
  }

  const refresh = () => void query.refetch();

  if (phase === 'LIVE') {
    if (session.endConfirmedAt && !backInRoom) {
      return <WaitingClose session={session} onBack={() => setBackInRoom(true)} />;
    }
    return (
      <LiveRoom
        session={session}
        joined={joined}
        refresh={refresh}
        actions={
          <EndSessionButton
            session={session}
            onEnded={() => {
              setBackInRoom(false);
              refresh();
            }}
          />
        }
      />
    );
  }

  if (session.status === 'COMPLETED' || session.status === 'UNVERIFIED') {
    return <ClosedRoom session={session} />;
  }

  return <RoomSummary session={session} phase={phase} onEnter={() => void enter()} entering={entering} />;
}
