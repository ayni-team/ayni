import { useEffect, useState, type ReactNode } from 'react';
import { useUniversity } from '@/shared/brand';
import { formatInZone, serverNow, useZone } from '@/shared/time';
import { AyniLogo, Button, Card, Dialog, UniversityLogo } from '@/shared/ui';
import { IconShieldCheck } from './icons';
import { PresencePanel, usePresence } from './PresencePanel';
import { RoomMedia } from './RoomMedia';
import { courseName, elapsedText, hoursLine, otherName, otherRole, type JoinedSession, type Session } from './session';

function Elapsed({ startedAt }: { startedAt: string | null | undefined }) {
  const [now, setNow] = useState(serverNow);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(serverNow()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  return <span className="tabular-nums">{elapsedText(startedAt, now)}</span>;
}

function NeedCard({ session }: { session: Session }) {
  if (!session.needDescription) return null;
  return (
    <Card className="sm:p-5">
      <p className="text-[13px] font-semibold text-ink-2">
        {session.role === 'TUTOR' ? `Lo que necesita ${otherName(session)}` : 'Lo que pediste'}
      </p>
      <p className="mt-2 text-sm text-ink">“{session.needDescription}”</p>
    </Card>
  );
}

export interface LiveRoomProps {
  session: Session;
  joined: JoinedSession | null;
  refresh: () => void;
  /** What ends the session (FE-23), on the right of the header. */
  actions?: ReactNode;
}

/**
 * The room in progress takes the whole window, without the frame of the application: the whiteboard
 * and the video need the space, and the session is the one thing to do. The header keeps a way to
 * know where one is.
 */
export function LiveRoom({ session, joined, refresh, actions }: LiveRoomProps) {
  const zone = useZone();
  const university = useUniversity();
  const presence = usePresence(session, refresh);
  const [codeOpen, setCodeOpen] = useState(false);
  const pendingCode = presence.state === 'CODE' || presence.state === 'EXPIRED' || presence.state === 'NO_ATTEMPTS';
  const expiresAt = session.presence?.expiresAt ? formatInZone(session.presence.expiresAt, zone, 'time') : '';

  return (
    <div className="fixed inset-0 z-30 flex flex-col overflow-y-auto bg-surface lg:overflow-hidden">
      <header className="flex items-center justify-between gap-3 border-b border-line bg-white px-4 py-3 lg:px-6">
        <div className="hidden min-w-0 items-center gap-3 lg:flex lg:w-1/4">
          <AyniLogo className="h-9" />
          <UniversityLogo university={university} size="sm" />
        </div>
        <div className="min-w-0 lg:text-center">
          <h1 className="truncate font-display text-lg font-semibold text-ink">{courseName(session)}</h1>
          <p className="flex flex-wrap items-center gap-x-2 text-[13px] text-ink-2 lg:justify-center">
            <span className="flex items-center gap-1.5 font-semibold text-brand-text">
              <span aria-hidden="true" className="h-2 w-2 rounded-full bg-brand" />
              En curso · <Elapsed startedAt={session.startedAt} />
            </span>
            <span className="hidden sm:inline">· {hoursLine(session, zone)}</span>
            <span className="hidden sm:inline">
              · con {otherName(session)} ({otherRole(session)})
            </span>
          </p>
        </div>
        <div className="flex shrink-0 justify-end lg:w-1/4">{actions}</div>
      </header>

      <div className="grid flex-1 grid-cols-1 gap-4 p-4 lg:min-h-0 lg:grid-cols-[minmax(0,1fr)_360px] lg:grid-rows-[auto_minmax(0,1fr)]">
        <div className="min-w-0 lg:col-start-2 lg:row-start-1">
          <RoomMedia kind="video" session={session} joined={joined} />
        </div>
        <div className="min-h-0 min-w-0 lg:col-start-1 lg:row-span-2 lg:row-start-1">
          <RoomMedia kind="board" session={session} joined={joined} />
        </div>
        <div className="flex min-w-0 flex-col gap-4 lg:col-start-2 lg:row-start-2 lg:overflow-y-auto">
          <div className={pendingCode ? 'hidden lg:block' : undefined}>
            <PresencePanel session={session} presence={presence} />
          </div>
          <NeedCard session={session} />
        </div>
      </div>

      {pendingCode && (
        <div className="sticky bottom-0 flex items-center justify-between gap-3 border-t border-line bg-brand-soft px-4 py-3 lg:hidden">
          <p className="flex items-center gap-2 text-sm font-semibold text-ink">
            <IconShieldCheck className="h-5 w-5 text-brand-text" />
            {presence.state === 'CODE' ? `Vence ${expiresAt}` : 'Tu presencia'}
          </p>
          <Button onClick={() => setCodeOpen(true)}>Confirmar presencia</Button>
        </div>
      )}
      <Dialog open={codeOpen && pendingCode} onClose={() => setCodeOpen(false)} title="Confirma tu presencia">
        <PresencePanel session={session} presence={presence} framed={false} />
      </Dialog>
    </div>
  );
}
