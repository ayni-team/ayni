import { VideoCall } from '../video/VideoCall';
import { IconVideo } from './icons';
import { otherRole, type JoinedSession, type Session } from './session';

export interface RoomMediaProps {
  kind: 'video' | 'board';
  session: Session;
  /** The answer of joining: `video` is the pass, `roomName` the room. Null until it arrives. */
  joined: JoinedSession | null;
  /** Asks for a new pass for the call. */
  renewPass: () => Promise<unknown>;
}

/**
 * The place of the video and of the whiteboard in the room. The layout of `LiveRoom` gives each its
 * box: the video on the right on a desktop and on top on a phone, the whiteboard filling the rest.
 * What goes inside changes here, and the page does not move.
 */
export function RoomMedia({ kind, session, joined, renewPass }: RoomMediaProps) {
  if (kind === 'video') {
    if (joined === null) {
      return (
        <div
          role="status"
          className="flex h-[220px] items-center justify-center rounded-[20px] bg-ink text-sm text-white lg:h-[240px]"
        >
          Conectando el video…
        </div>
      );
    }
    if (joined.video) return <VideoCall pass={joined.video} renew={renewPass} />;
    return (
      <div className="flex h-[220px] flex-col items-center justify-center gap-2 rounded-[20px] bg-ink p-4 text-center text-white lg:h-[240px]">
        <IconVideo className="h-6 w-6" />
        <p className="text-sm font-semibold">El video no está disponible</p>
        <p className="text-xs text-white/80">Sigue en la pizarra; tu {otherRole(session)} te ve aquí.</p>
      </div>
    );
  }
  return (
    <div className="flex h-full min-h-[320px] items-center justify-center rounded-[20px] border border-line bg-white p-6 text-center">
      <p className="text-sm text-ink-2">La pizarra no está disponible por ahora.</p>
    </div>
  );
}
