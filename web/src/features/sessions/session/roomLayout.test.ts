import { describe, expect, it } from 'vitest';
import {
  initialRoomView,
  readRoomView,
  roomLayout,
  roomViewReducer,
  saveRoomView,
  splitColumns,
  type RoomViewState,
} from './roomLayout';

describe('the layout of the room in progress', () => {
  it('gives each view its areas on a desktop', () => {
    expect(roomLayout('board', 'wide')).toEqual({ board: 'large', video: 'small', side: 'column' });
    expect(roomLayout('split', 'wide')).toEqual({ board: 'half', video: 'half', side: 'bar' });
    expect(roomLayout('video', 'wide')).toEqual({ board: 'hidden', video: 'large', side: 'column' });
  });

  it('stacks them on a phone, the video first and taller the more it matters', () => {
    expect(roomLayout('board', 'narrow')).toEqual({ board: 'large', video: 'small', side: 'below' });
    expect(roomLayout('split', 'narrow')).toEqual({ board: 'half', video: 'half', side: 'below' });
    expect(roomLayout('video', 'narrow')).toEqual({ board: 'hidden', video: 'large', side: 'below' });
  });

  it('covers the four cases of whiteboard and shared screen', () => {
    // Without the whiteboard, with or without a screen: the video view.
    expect(roomLayout('video', 'wide').board).toBe('hidden');
    // The whiteboard without a screen: the whiteboard view. Both: the split view.
    expect(roomLayout('board', 'wide').video).toBe('small');
    expect(roomLayout('split', 'wide')).toMatchObject({ board: 'half', video: 'half' });
  });

  it('shares the width of the split view as the person chose', () => {
    expect(splitColumns('even')).toEqual({ board: '1fr', video: '1fr' });
    expect(splitColumns('board')).toEqual({ board: '3fr', video: '2fr' });
    expect(splitColumns('video')).toEqual({ board: '2fr', video: '3fr' });
  });
});

describe('the view, as a share starts and ends', () => {
  const onBoard: RoomViewState = initialRoomView(null);

  it('starts on the whiteboard view, half and half', () => {
    expect(onBoard).toEqual({ view: 'board', ratio: 'even', sharing: false, before: null });
  });

  it('opens the split view when a share starts on the whiteboard view, and goes back when it ends', () => {
    const sharing = roomViewReducer(onBoard, { type: 'sharing', on: true });
    expect(sharing).toMatchObject({ view: 'split', sharing: true, before: 'board' });

    expect(roomViewReducer(sharing, { type: 'sharing', on: false })).toMatchObject({
      view: 'board',
      sharing: false,
      before: null,
    });
  });

  it('leaves the split and the video views alone', () => {
    for (const view of ['split', 'video'] as const) {
      const state = roomViewReducer(onBoard, { type: 'choose', view });
      const sharing = roomViewReducer(state, { type: 'sharing', on: true });
      expect(sharing.view).toBe(view);
      expect(roomViewReducer(sharing, { type: 'sharing', on: false }).view).toBe(view);
    }
  });

  it('keeps what the person chose during the share when it ends', () => {
    const sharing = roomViewReducer(onBoard, { type: 'sharing', on: true });
    const chosen = roomViewReducer(sharing, { type: 'choose', view: 'video' });

    expect(roomViewReducer(chosen, { type: 'sharing', on: false }).view).toBe('video');
  });

  it('does not move on a repeated event', () => {
    const sharing = roomViewReducer(onBoard, { type: 'sharing', on: true });
    const back = roomViewReducer(sharing, { type: 'choose', view: 'board' });

    // A second "somebody shares" (another person starts too) does not open the split view again.
    expect(roomViewReducer(back, { type: 'sharing', on: true })).toBe(back);
    expect(roomViewReducer(onBoard, { type: 'sharing', on: false })).toBe(onBoard);
  });

  it('changes the ratio without touching the view', () => {
    expect(roomViewReducer(onBoard, { type: 'ratio', ratio: 'video' })).toMatchObject({
      view: 'board',
      ratio: 'video',
    });
  });
});

describe('remembering the view in the tab', () => {
  function memory(): Storage {
    const data = new Map<string, string>();
    return {
      getItem: (key) => data.get(key) ?? null,
      setItem: (key, value) => void data.set(key, value),
      removeItem: (key) => void data.delete(key),
      clear: () => data.clear(),
      key: () => null,
      get length() {
        return data.size;
      },
    };
  }

  it('keeps the view and the ratio of each session', () => {
    const storage = memory();
    saveRoomView('s1', { view: 'video', ratio: 'board' }, () => storage);

    expect(readRoomView('s1', () => storage)).toEqual({ view: 'video', ratio: 'board' });
    expect(readRoomView('s2', () => storage)).toBeNull();
    expect(initialRoomView(readRoomView('s1', () => storage))).toMatchObject({ view: 'video', ratio: 'board' });
  });

  it('ignores what it cannot read', () => {
    const storage = memory();
    storage.setItem('ayni.room.s1', '{not json');
    storage.setItem('ayni.room.s2', JSON.stringify({ view: 'theatre', ratio: 7 }));

    expect(readRoomView('s1', () => storage)).toBeNull();
    expect(initialRoomView(readRoomView('s2', () => storage))).toMatchObject({ view: 'board', ratio: 'even' });
  });

  it('works without storage at all', () => {
    const refused = () => {
      throw new DOMException('denied', 'SecurityError');
    };

    expect(readRoomView('s1', refused)).toBeNull();
    expect(() => saveRoomView('s1', { view: 'split', ratio: 'even' }, refused)).not.toThrow();
  });
});
