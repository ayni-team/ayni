import { cx } from '@/shared/ui';
import { VideoCall, type CallShare, type ShareControl } from '../video/VideoCall';
import { Whiteboard } from '../whiteboard/Whiteboard';
import { IconVideo } from './icons';
import { otherRole, type JoinedSession, type Session } from './session';

export interface RoomMediaProps {
  kind: 'video' | 'board';
  session: Session;
  /** The answer of joining: `video` is the pass, `roomName` the room. Null until it arrives. */
  joined: JoinedSession | null;
  /** Asks for a new pass for the call. */
  renewPass: () => Promise<unknown>;
  /** The whiteboard says the session is over before the next look at the session does. */
  onBoardClosed?: () => void;
  /** The video fills the box the layout gives it instead of its own height. */
  fill?: boolean;
  onShareControl?: (control: ShareControl | null) => void;
  onShareChange?: (share: CallShare) => void;
}

/**
 * The place of the video and of the whiteboard in the room. The layout of `LiveRoom` gives each its
 * box, and changes it with the view the person chooses; what goes inside changes here, and the page
 * does not move.
 */
export function RoomMedia({
  kind,
  session,
  joined,
  renewPass,
  onBoardClosed,
  fill = false,
  onShareControl,
  onShareChange,
}: RoomMediaProps) {
  const height = fill ? 'h-full min-h-0' : 'h-[220px] lg:h-[240px]';
  if (kind === 'video') {
    if (joined === null) {
      return (
        <div
          role="status"
          className={cx('flex items-center justify-center rounded-[20px] bg-ink text-sm text-white', height)}
        >
          Conectando el video…
        </div>
      );
    }
    if (joined.video) {
      return (
        <VideoCall
          pass={joined.video}
          renew={renewPass}
          fill={fill}
          onShareControl={onShareControl}
          onShareChange={onShareChange}
        />
      );
    }
    return (
      <div
        className={cx(
          'flex flex-col items-center justify-center gap-2 rounded-[20px] bg-ink p-4 text-center text-white',
          height,
        )}
      >
        <IconVideo className="h-6 w-6" />
        <p className="text-sm font-semibold">El video no está disponible</p>
        <p className="text-xs text-white/80">Sigue en la pizarra; tu {otherRole(session)} te ve aquí.</p>
      </div>
    );
  }
  return <Whiteboard sessionId={session.id ?? ''} onClosed={onBoardClosed} />;
}
