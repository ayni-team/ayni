import { useEffect, useRef, useState } from 'react';
import { errorText, useApiMutation, useApiQuery } from '@/shared/api';
import { useCountdown } from '@/shared/time';
import { useToast } from '@/shared/ui';
import { isOver, myArrival, phaseOf, type JoinedSession } from './session';

/** A reasonable pace: the arrival of the other person shows within ten seconds. */
const REFRESH_MS = 10_000;

/**
 * The state of the room: the session, kept fresh while the page is visible, and the answer of
 * joining, which holds the room and the video pass and which only joining hands over.
 */
export function useSessionRoom(id: string) {
  const toast = useToast();
  const query = useApiQuery(['sessions', 'session', { id }], '/api/v1/sessions/{id}', {
    path: { id },
    refetchInterval: (state) => (isOver(state.state.data?.status) ? false : REFRESH_MS),
  });
  const session = query.data;
  const opens = useCountdown(session?.joinOpensAt);
  const phase = session ? phaseOf(session, opens.expired) : null;

  const [joined, setJoined] = useState<JoinedSession | null>(null);
  const join = useApiMutation('post', '/api/v1/sessions/{id}/join');

  async function enter(): Promise<void> {
    try {
      setJoined(await join.mutateAsync({ path: { id } }));
    } catch (failure) {
      toast.error(errorText(failure));
    } finally {
      void query.refetch();
    }
  }

  // Back on the page of a room already entered: asking again changes nothing and hands over the room.
  const asked = useRef(false);
  const alreadyIn = Boolean(session && myArrival(session) && !isOver(session.status));
  useEffect(() => {
    if (!alreadyIn || joined || asked.current) return;
    asked.current = true;
    join.mutateAsync({ path: { id } }).then(setJoined, () => undefined);
    // `join` changes on every render; `asked` keeps this to once.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [alreadyIn, joined, id]);

  return { query, session, phase, joined, enter, entering: join.isPending };
}
