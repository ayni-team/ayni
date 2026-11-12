/**
 * How the room in progress shares the window between the whiteboard and the call. The state is the
 * view the person chose, not what the call is doing, so it works the same where the call sends no
 * events (the plain iframe of development). A shared screen only moves the view by itself where the
 * call does send them.
 */

export type RoomView = 'board' | 'split' | 'video';

/** `wide` is a desktop (1024 px and up, `lg`), `narrow` a tablet or a phone. */
export type Viewport = 'wide' | 'narrow';

export interface RoomAreas {
  /** `hidden` stays mounted out of sight: see `LiveRoom`. */
  board: 'large' | 'half' | 'hidden';
  video: 'small' | 'half' | 'large';
  /** Presence and the need: a column beside the work, folded into the toolbar, or after the work. */
  side: 'column' | 'bar' | 'below';
}

export const ROOM_VIEWS: readonly RoomView[] = ['board', 'split', 'video'];

/** Where each part goes. A phone stacks them, the video first, so `side` is always `below` there. */
export function roomLayout(view: RoomView, viewport: Viewport): RoomAreas {
  const narrow = viewport === 'narrow';
  switch (view) {
    case 'board':
      return { board: 'large', video: 'small', side: narrow ? 'below' : 'column' };
    case 'split':
      return { board: 'half', video: 'half', side: narrow ? 'below' : 'bar' };
    case 'video':
      return { board: 'hidden', video: 'large', side: narrow ? 'below' : 'column' };
  }
}

/** How a split view shares the width: more board, half and half, or more video. */
export type SplitRatio = 'board' | 'even' | 'video';

export const SPLIT_RATIOS: readonly SplitRatio[] = ['video', 'even', 'board'];

/** The two columns of a split view, as grid tracks. */
export function splitColumns(ratio: SplitRatio): { board: string; video: string } {
  switch (ratio) {
    case 'board':
      return { board: '3fr', video: '2fr' };
    case 'video':
      return { board: '2fr', video: '3fr' };
    case 'even':
      return { board: '1fr', video: '1fr' };
  }
}

export interface RoomViewState {
  view: RoomView;
  ratio: SplitRatio;
  /** Whether somebody shares a screen in the call, as its last event said. */
  sharing: boolean;
  /** The view to go back to when the share ends, only when the share changed it by itself. */
  before: RoomView | null;
}

export type RoomViewAction =
  { type: 'choose'; view: RoomView } | { type: 'ratio'; ratio: SplitRatio } | { type: 'sharing'; on: boolean };

/**
 * A share that starts on the whiteboard view opens the split view, and its end goes back. What the
 * person chooses meanwhile wins: then the end of the share leaves the view alone.
 */
export function roomViewReducer(state: RoomViewState, action: RoomViewAction): RoomViewState {
  switch (action.type) {
    case 'choose':
      return { ...state, view: action.view, before: null };
    case 'ratio':
      return { ...state, ratio: action.ratio };
    case 'sharing':
      if (action.on === state.sharing) return state;
      if (action.on) {
        return state.view === 'board'
          ? { ...state, sharing: true, view: 'split', before: 'board' }
          : { ...state, sharing: true };
      }
      return state.before
        ? { ...state, sharing: false, view: state.before, before: null }
        : { ...state, sharing: false };
  }
}

const STORAGE_PREFIX = 'ayni.room.';

export function initialRoomView(saved: Partial<Pick<RoomViewState, 'view' | 'ratio'>> | null): RoomViewState {
  return { view: saved?.view ?? 'board', ratio: saved?.ratio ?? 'even', sharing: false, before: null };
}

/** The view chosen in this session's room, in this tab. Anything unreadable is no choice. */
export function readRoomView(sessionId: string, storage: () => Storage = () => window.sessionStorage) {
  try {
    const raw = storage().getItem(STORAGE_PREFIX + sessionId);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { view?: unknown; ratio?: unknown };
    const view = ROOM_VIEWS.find((known) => known === parsed.view);
    const ratio = SPLIT_RATIOS.find((known) => known === parsed.ratio);
    return { view, ratio };
  } catch {
    return null;
  }
}

/** Keeps the choice for a reload. A browser that refuses storage only forgets it. */
export function saveRoomView(
  sessionId: string,
  state: Pick<RoomViewState, 'view' | 'ratio'>,
  storage: () => Storage = () => window.sessionStorage,
): void {
  try {
    storage().setItem(STORAGE_PREFIX + sessionId, JSON.stringify({ view: state.view, ratio: state.ratio }));
  } catch {
    // Private windows, blocked storage: the room works the same, it just does not remember.
  }
}

/**
 * Whether this browser can share a screen at all. Phones cannot, and neither can a page that is
 * not a secure origin (http by the address of the network): there the button would do nothing.
 */
export function canShareScreen(): boolean {
  return typeof navigator !== 'undefined' && typeof navigator.mediaDevices?.getDisplayMedia === 'function';
}
