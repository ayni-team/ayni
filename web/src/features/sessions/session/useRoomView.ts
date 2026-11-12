import { useEffect, useMemo, useReducer } from 'react';
import {
  initialRoomView,
  readRoomView,
  roomViewReducer,
  saveRoomView,
  type RoomView,
  type SplitRatio,
} from './roomLayout';

/** The view of the room in progress, remembered for this session in this tab. */
export function useRoomView(sessionId: string) {
  const [state, dispatch] = useReducer(roomViewReducer, sessionId, (id) => initialRoomView(readRoomView(id)));
  const { view, ratio } = state;

  useEffect(() => saveRoomView(sessionId, { view, ratio }), [sessionId, view, ratio]);

  const actions = useMemo(
    () => ({
      choose: (next: RoomView) => dispatch({ type: 'choose', view: next }),
      setRatio: (next: SplitRatio) => dispatch({ type: 'ratio', ratio: next }),
      sharingChanged: (on: boolean) => dispatch({ type: 'sharing', on }),
    }),
    [],
  );
  return { ...state, ...actions };
}
