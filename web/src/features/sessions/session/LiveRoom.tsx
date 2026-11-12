import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { useUniversity } from '@/shared/brand';
import { formatInZone, serverNow, useZone } from '@/shared/time';
import { AyniLogo, Button, Card, Dialog, IconCheck, UniversityLogo, cx } from '@/shared/ui';
import type { ShareControl } from '../video/VideoCall';
import { IconShieldCheck } from './icons';
import { PresencePanel, usePresence, type Presence } from './PresencePanel';
import { RoomMedia } from './RoomMedia';
import { RoomToolbar } from './RoomToolbar';
import { roomLayout, splitColumns, type RoomAreas, type RoomView } from './roomLayout';
import { courseName, elapsedText, hoursLine, otherName, otherRole, type JoinedSession, type Session } from './session';
import { useRoomView } from './useRoomView';

function Elapsed({ startedAt }: { startedAt: string | null | undefined }) {
  const [now, setNow] = useState(serverNow);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(serverNow()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  return <span className="tabular-nums">{elapsedText(startedAt, now)}</span>;
}

function needTitle(session: Session): string {
  return session.role === 'TUTOR' ? `Lo que necesita ${otherName(session)}` : 'Lo que pediste';
}

function NeedCard({ session }: { session: Session }) {
  if (!session.needDescription) return null;
  return (
    <Card className="sm:p-5">
      <p className="text-[13px] font-semibold text-ink-2">{needTitle(session)}</p>
      <p className="mt-2 text-sm text-ink">“{session.needDescription}”</p>
    </Card>
  );
}

/*
 * The classes of each area, from `roomLayout`: the phone's first, then the desktop's (`lg:`). Only
 * classes change between views; the video and the whiteboard are never moved to another parent,
 * so the call is not cut and the board keeps its canvas and its undo.
 */
const GRID: Record<RoomView, string> = {
  board: 'lg:grid-cols-[minmax(0,1fr)_360px] lg:grid-rows-[240px_minmax(0,1fr)]',
  split: 'lg:grid-cols-[minmax(0,var(--room-board))_minmax(0,var(--room-video))] lg:grid-rows-[minmax(0,1fr)]',
  video: 'lg:grid-cols-[minmax(0,1fr)_360px] lg:grid-rows-[minmax(0,1fr)]',
};

const VIDEO_NARROW: Record<RoomAreas['video'], string> = {
  small: 'h-[220px]',
  half: 'h-[45svh] min-h-[260px]',
  large: 'relative z-10 col-start-1 row-start-1 h-[65svh] min-h-[300px]',
};
const VIDEO_WIDE: Record<RoomAreas['video'], string> = {
  small: 'lg:col-start-2 lg:row-start-1',
  half: 'lg:col-start-2 lg:row-start-1',
  large: 'lg:col-start-1 lg:row-start-1',
};

/*
 * A hidden board stays mounted under the video, in the same cell and at its real size, so it keeps
 * receiving the other person's drawing. Unmounting it would close the channel, lose the person's
 * undo and reload Excalidraw on the way back; a box of size zero would leave Excalidraw measuring
 * nothing. `invisible` alone is not enough: Excalidraw makes parts of its interface visible again,
 * so it is also transparent and `inert` (no focus, no clicks, nothing for screen readers).
 */
const BOARD_NARROW: Record<RoomAreas['board'], string> = {
  large: 'h-[75svh]',
  half: 'h-[60svh]',
  hidden: 'invisible pointer-events-none col-start-1 row-start-1 opacity-0',
};
const BOARD_WIDE: Record<RoomAreas['board'], string> = {
  large: 'lg:col-start-1 lg:row-span-2 lg:row-start-1',
  half: 'lg:col-start-1 lg:row-start-1',
  hidden: 'lg:col-start-1 lg:row-start-1',
};

function areaClasses(view: RoomView) {
  const narrow = roomLayout(view, 'narrow');
  const wide = roomLayout(view, 'wide');
  const side =
    wide.side === 'bar'
      ? 'lg:hidden'
      : cx('lg:col-start-2 lg:overflow-y-auto', wide.video === 'small' ? 'lg:row-start-2' : 'lg:row-start-1');
  return {
    grid: GRID[view],
    video: cx('min-w-0 lg:h-auto lg:min-h-0', VIDEO_NARROW[narrow.video], VIDEO_WIDE[wide.video]),
    board: cx('min-h-0 min-w-0 lg:h-auto', BOARD_NARROW[narrow.board], BOARD_WIDE[wide.board]),
    side: cx('flex min-w-0 flex-col gap-4', side),
  };
}

/** Presence and the need, folded into the toolbar of the split view on a desktop. */
function FoldedSide({
  session,
  presence,
  pendingCode,
  onOpen,
}: {
  session: Session;
  presence: Presence;
  pendingCode: boolean;
  onOpen: (what: 'presence' | 'need') => void;
}) {
  return (
    <>
      {session.needDescription && (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => onOpen('need')}
          className="whitespace-nowrap"
          aria-label={needTitle(session)}
        >
          {session.role === 'TUTOR' ? 'Lo que necesita' : 'Lo que pediste'}
        </Button>
      )}
      <Button
        variant={pendingCode ? 'primary' : 'secondary'}
        size="sm"
        className="whitespace-nowrap"
        onClick={() => onOpen('presence')}
        icon={
          presence.state === 'CONFIRMED' ? <IconCheck className="h-4 w-4" /> : <IconShieldCheck className="h-4 w-4" />
        }
      >
        {presence.state === 'CONFIRMED' ? 'Presencia confirmada' : pendingCode ? 'Confirmar presencia' : 'Tu presencia'}
      </Button>
    </>
  );
}

export interface LiveRoomProps {
  session: Session;
  joined: JoinedSession | null;
  refresh: () => void;
  renewPass: () => Promise<unknown>;
  /** What ends the session (FE-23), on the right of the header. */
  actions?: ReactNode;
}

/**
 * The room in progress takes the whole window, without the frame of the application: the whiteboard
 * and the video need the space, and the session is the one thing to do. The header keeps a way to
 * know where one is; under it, the person chooses how the two share the window (`roomLayout`).
 */
export function LiveRoom({ session, joined, refresh, renewPass, actions }: LiveRoomProps) {
  const zone = useZone();
  const university = useUniversity();
  const presence = usePresence(session, refresh);
  const room = useRoomView(session.id ?? '');
  const [share, setShare] = useState<ShareControl | null>(null);
  const [sharingMine, setSharingMine] = useState(false);
  const [dialog, setDialog] = useState<'presence' | 'need' | null>(null);
  const pendingCode = presence.state === 'CODE' || presence.state === 'EXPIRED' || presence.state === 'NO_ATTEMPTS';
  const expiresAt = session.presence?.expiresAt ? formatInZone(session.presence.expiresAt, zone, 'time') : '';
  const areas = areaClasses(room.view);
  const columns = splitColumns(room.ratio);
  const gridVars = { '--room-board': columns.board, '--room-video': columns.video } as CSSProperties;
  const boardHidden = roomLayout(room.view, 'wide').board === 'hidden';
  const boardBox = useRef<HTMLDivElement>(null);
  // React 18 does not know `inert` as a property yet.
  useEffect(() => {
    boardBox.current?.toggleAttribute('inert', boardHidden);
  }, [boardHidden]);

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

      <RoomToolbar
        view={room.view}
        onView={room.choose}
        ratio={room.ratio}
        onRatio={room.setRatio}
        share={share}
        sharingMine={sharingMine}
        folded={<FoldedSide session={session} presence={presence} pendingCode={pendingCode} onOpen={setDialog} />}
      />

      <div
        style={gridVars}
        className={cx('grid flex-1 grid-cols-1 gap-4 p-4 lg:min-h-0', areas.grid)}
        data-view={room.view}
      >
        <div className={areas.video}>
          <RoomMedia
            kind="video"
            session={session}
            joined={joined}
            renewPass={renewPass}
            fill
            onShareControl={setShare}
            onShareChange={(change) => {
              setSharingMine(change.mine);
              room.sharingChanged(change.anybody || change.mine);
            }}
          />
        </div>
        <div ref={boardBox} className={areas.board} aria-hidden={boardHidden || undefined}>
          <RoomMedia kind="board" session={session} joined={joined} renewPass={renewPass} onBoardClosed={refresh} />
        </div>
        <div className={areas.side}>
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
          <Button onClick={() => setDialog('presence')}>Confirmar presencia</Button>
        </div>
      )}
      <Dialog
        open={dialog === 'presence' && (pendingCode || room.view === 'split')}
        onClose={() => setDialog(null)}
        title={presence.state === 'CONFIRMED' ? 'Tu presencia' : 'Confirma tu presencia'}
      >
        <PresencePanel session={session} presence={presence} framed={false} />
      </Dialog>
      <Dialog
        open={dialog === 'need' && Boolean(session.needDescription)}
        onClose={() => setDialog(null)}
        title={needTitle(session)}
      >
        <p className="text-sm text-ink">“{session.needDescription}”</p>
      </Dialog>
    </div>
  );
}
