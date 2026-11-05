import { Avatar } from '@/shared/ui';
import { otherName, otherRole, type JoinedSession, type Session } from './session';

export interface RoomMediaProps {
  kind: 'video' | 'board';
  session: Session;
  /** The answer of joining: `video` is the pass, `roomName` the room. Null until it arrives. */
  joined: JoinedSession | null;
}

/**
 * The place of the video and of the whiteboard in the room. The layout of `LiveRoom` gives each its
 * box: the video on the right on a desktop and on top on a phone, the whiteboard filling the rest.
 * What goes inside changes here, and the page does not move.
 */
export function RoomMedia({ kind, session }: RoomMediaProps) {
  if (kind === 'video') {
    const name = otherName(session);
    return (
      <div className="flex min-h-[180px] flex-col items-center justify-center gap-3 rounded-[20px] bg-ink p-4 text-center text-white">
        <Avatar name={name} decorative />
        <p className="text-sm font-medium">
          {name} · {otherRole(session)}
        </p>
        <p className="text-xs text-white/80">El video no está disponible</p>
      </div>
    );
  }
  return (
    <div className="flex h-full min-h-[320px] items-center justify-center rounded-[20px] border border-line bg-white p-6 text-center">
      <p className="text-sm text-ink-2">La pizarra no está disponible por ahora.</p>
    </div>
  );
}
