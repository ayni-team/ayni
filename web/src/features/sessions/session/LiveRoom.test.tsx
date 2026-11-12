import { act, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import { fakeJitsi, jitsiScriptLoads } from '../video/fakeJitsi';
import { forgetLoadedScripts } from '../video/jitsi';
import SessionPage from './SessionPage';

const URL = '/api/v1/sessions/x';
const PAST = '2020-01-01T00:00:00Z';
const FUTURE = '2099-01-01T00:00:00Z';

const LIVE: Schema<'Session'> = {
  id: 'x',
  role: 'TUTOR',
  status: 'IN_PROGRESS',
  studentId: 's1',
  tutorId: 't1',
  counterpart: { id: 's1', name: 'Bruno Salas' },
  catalogItem: { name: 'Estadística Aplicada I' },
  scheduledStart: '2026-10-23T21:00:00Z',
  scheduledEnd: '2026-10-23T22:00:00Z',
  joinOpensAt: PAST,
  startedAt: PAST,
  studentJoinedAt: PAST,
  tutorJoinedAt: PAST,
  needDescription: 'Repasar para la PC2',
  presence: { expiresAt: FUTURE, attemptsLeft: 5, confirmedAt: PAST },
};

const HTTPS = 'https://meet.ayni.pe/room-1';
const PLAIN = 'http://localhost:8000/room-1';

function enter(videoUrl: string) {
  const server = fakeServer();
  serveFrame(server);
  signIn('STUDENT');
  server
    .on('GET', URL, { body: LIVE })
    .on('POST', `${URL}/join`, { body: { sessionId: 'x', video: { url: videoUrl, token: 't', expiresAt: FUTURE } } });
  return renderWithProviders(<SessionPage />, { route: '/sesion/x', path: '/sesion/:id' });
}

const viewTab = (name: string) => screen.getByRole('tab', { name });
const selectedView = () => screen.getByRole('tab', { selected: true }).textContent;

/** A browser that can share a screen: a desktop on a secure origin. */
function canShare(yes: boolean) {
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: yes ? { getDisplayMedia: vi.fn() } : undefined,
  });
}

describe('FE-22 the views of the room in progress', () => {
  let double: ReturnType<typeof fakeJitsi>;

  beforeEach(() => {
    double = fakeJitsi();
    window.JitsiMeetExternalAPI = double.Jitsi as never;
    canShare(true);
  });

  afterEach(() => {
    forgetLoadedScripts();
    document.head.querySelectorAll('script').forEach((script) => script.remove());
    delete window.JitsiMeetExternalAPI;
    window.sessionStorage.clear();
    canShare(false);
    vi.restoreAllMocks();
  });

  async function inCall() {
    const calls = double.instances.length;
    const rendered = enter(HTTPS);
    await screen.findByRole('tab', { name: 'Pizarra' });
    await waitFor(() => expect(document.head.querySelector('script')).not.toBeNull());
    // The script loads once per page: a second room finds it already there.
    if (calls === 0) jitsiScriptLoads();
    await waitFor(() => expect(double.instances).toHaveLength(calls + 1));
    return rendered;
  }

  it('starts on the whiteboard, and changes view without restarting the call', async () => {
    const { user } = await inCall();
    expect(selectedView()).toBe('Pizarra');

    await user.click(viewTab('Video'));
    await user.click(viewTab('Dividida'));
    await user.click(viewTab('Pizarra'));

    expect(double.instances).toHaveLength(1);
    expect(double.instances[0].dispose).not.toHaveBeenCalled();
  });

  it('opens the split view when somebody shares, and goes back when the share ends', async () => {
    await inCall();

    act(() => double.instances[0].fire('contentSharingParticipantsChanged', { data: ['bruno'] }));
    expect(selectedView()).toBe('Dividida');

    act(() => double.instances[0].fire('contentSharingParticipantsChanged', { data: [] }));
    expect(selectedView()).toBe('Pizarra');
  });

  it('keeps the view the person chose while a screen was shared', async () => {
    const { user } = await inCall();
    act(() => double.instances[0].fire('contentSharingParticipantsChanged', { data: ['bruno'] }));

    await user.click(viewTab('Video'));
    act(() => double.instances[0].fire('contentSharingParticipantsChanged', { data: [] }));

    expect(selectedView()).toBe('Video');
  });

  it('shares the screen with the API, and offers to stop', async () => {
    const { user } = await inCall();

    await user.click(screen.getByRole('button', { name: 'Compartir pantalla' }));
    expect(double.instances[0].executeCommand).toHaveBeenCalledWith('toggleShareScreen');

    act(() => double.instances[0].fire('screenSharingStatusChanged', { on: true }));
    expect(selectedView()).toBe('Dividida');
    await user.click(screen.getByRole('button', { name: 'Dejar de compartir pantalla' }));
    expect(double.instances[0].executeCommand).toHaveBeenCalledTimes(2);
  });

  it('does not offer to share where the browser cannot (a phone, an insecure origin)', async () => {
    canShare(false);
    await inCall();

    expect(screen.queryByRole('button', { name: 'Compartir pantalla' })).not.toBeInTheDocument();
    expect(viewTab('Video')).toBeInTheDocument();
  });

  it('hides the whiteboard in the video view without unmounting it', async () => {
    const { user } = await inCall();
    // The line of the board's state, whatever it says: it lives as long as the board does.
    const status = screen
      .getAllByRole('status')
      .find((line) => /pizarra|conexión|Guardado/.test(line.textContent ?? ''));
    if (!status) throw new Error('no board');

    await user.click(viewTab('Video'));

    expect(status.isConnected).toBe(true);
    const hidden = status.closest('[aria-hidden="true"]');
    expect(hidden).not.toBeNull();
    expect(hidden).toHaveAttribute('inert');

    await user.click(viewTab('Pizarra'));
    expect(status.isConnected).toBe(true);
    expect(status.closest('[inert]')).toBeNull();
  });

  it('remembers the view for this session in the tab', async () => {
    const first = await inCall();
    await first.user.click(viewTab('Video'));
    first.unmount();

    await inCall();

    expect(selectedView()).toBe('Video');
  });

  it('works when the browser refuses storage', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError');
    });
    const { user } = await inCall();

    await user.click(viewTab('Dividida'));

    expect(selectedView()).toBe('Dividida');
  });

  it('folds presence and the need into the toolbar of the split view', async () => {
    const { user } = await inCall();
    await user.click(viewTab('Dividida'));

    await user.click(screen.getByRole('button', { name: 'Lo que necesita Bruno Salas' }));
    expect(await screen.findByRole('dialog')).toHaveTextContent('Repasar para la PC2');
    await user.keyboard('{Escape}');

    await user.click(screen.getByRole('button', { name: 'Presencia confirmada' }));
    expect(await screen.findByRole('dialog')).toHaveTextContent('Tu presencia');
  });
});

describe('FE-22 the views with the plain iframe of development', () => {
  beforeEach(() => canShare(true));

  afterEach(() => {
    window.sessionStorage.clear();
    canShare(false);
  });

  it('keeps the same iframe through every view', async () => {
    const { user } = enter(PLAIN);
    await screen.findByRole('tab', { name: 'Pizarra' });
    const frame = await waitFor(() => {
      const found = document.querySelector('iframe');
      expect(found).not.toBeNull();
      return found;
    });

    for (const view of ['Video', 'Dividida', 'Pizarra']) {
      await user.click(viewTab(view));
      expect(document.querySelector('iframe')).toBe(frame);
    }
    expect(frame?.isConnected).toBe(true);
    expect(document.querySelectorAll('iframe')).toHaveLength(1);
  });

  it('points to the call’s own button, in a box wide enough to show it', async () => {
    const { user } = enter(PLAIN);
    await screen.findByRole('tab', { name: 'Pizarra' });

    await user.click(await screen.findByRole('button', { name: 'Compartir pantalla' }));

    expect(selectedView()).toBe('Dividida');
    expect(screen.getByText(/se comparte con su propio botón/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Entendido' }));
    expect(screen.queryByText(/se comparte con su propio botón/)).not.toBeInTheDocument();
  });
});
