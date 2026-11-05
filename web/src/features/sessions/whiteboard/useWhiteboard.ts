import { useEffect, useMemo, useSyncExternalStore } from 'react';
import { currentSession } from '@/shared/session';
import { WhiteboardChannel, channelUrl } from './channel';

/**
 * The live whiteboard of a session in progress: the board as the server orders it, with this
 * client's drawing on top until it is confirmed, and what to do to it. See `WhiteboardChannel`.
 */
export function useWhiteboard(sessionId: string, onClosed?: () => void) {
  const channel = useMemo(
    () =>
      new WhiteboardChannel({
        url: channelUrl(sessionId),
        token: () => currentSession()?.sessionToken ?? null,
        onClosed,
      }),
    // `onClosed` is read when the channel closes; a new function does not mean a new channel.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [sessionId],
  );
  const snapshot = useSyncExternalStore(channel.subscribe, channel.getSnapshot);

  useEffect(() => {
    channel.connect();
    const retry = () => channel.retryNow();
    window.addEventListener('online', retry);
    document.addEventListener('visibilitychange', retry);
    return () => {
      window.removeEventListener('online', retry);
      document.removeEventListener('visibilitychange', retry);
      channel.stop();
    };
  }, [channel]);

  return {
    ...snapshot,
    put: channel.put.bind(channel),
    remove: channel.remove.bind(channel),
    clear: channel.clear.bind(channel),
  };
}
