import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { forgetLoadedScripts } from './jitsi';
import { VideoCall } from './VideoCall';

const PASS = { url: 'https://meet.ayni.pe/room-1', token: 'the-jwt', expiresAt: '2099-01-01T00:00:00Z' };

/** A double of what `external_api.js` defines. */
function fakeJitsi() {
  const instances: {
    domain: string;
    options: Record<string, unknown>;
    fire: (e: string, p?: unknown) => void;
    dispose: ReturnType<typeof vi.fn>;
  }[] = [];
  class Jitsi {
    listeners = new Map<string, (payload?: unknown) => void>();
    dispose = vi.fn();
    constructor(
      public domain: string,
      public options: Record<string, unknown>,
    ) {
      instances.push({
        domain,
        options,
        dispose: this.dispose,
        fire: (event, payload) => this.listeners.get(event)?.(payload),
      });
    }
    addListener(event: string, listener: (payload?: unknown) => void) {
      this.listeners.set(event, listener);
    }
  }
  return { Jitsi, instances };
}

function scriptLoads(withApi = true) {
  const script = document.head.querySelector('script');
  if (!script) throw new Error('no script');
  if (!withApi) delete window.JitsiMeetExternalAPI;
  script.dispatchEvent(new Event(withApi ? 'load' : 'error'));
}

describe('FE-22 VideoCall', () => {
  let double: ReturnType<typeof fakeJitsi>;

  beforeEach(() => {
    double = fakeJitsi();
    window.JitsiMeetExternalAPI = double.Jitsi as never;
  });

  afterEach(() => {
    forgetLoadedScripts();
    document.head.querySelectorAll('script').forEach((script) => script.remove());
    delete window.JitsiMeetExternalAPI;
    vi.useRealTimers();
  });

  it('opens the room with the token as an option, and says it is connecting until Jitsi draws', async () => {
    render(<VideoCall pass={PASS} renew={vi.fn(async () => undefined)} />);

    expect(screen.getByRole('status')).toHaveTextContent('Conectando el video…');
    scriptLoads();

    await waitFor(() => expect(double.instances).toHaveLength(1));
    expect(double.instances[0].domain).toBe('meet.ayni.pe');
    expect(double.instances[0].options).toMatchObject({ roomName: 'room-1', jwt: 'the-jwt' });
    expect(window.location.href).not.toContain('the-jwt');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('opens a plain iframe for the Jitsi of docker compose, with no script', () => {
    const { unmount } = render(
      <VideoCall pass={{ ...PASS, url: 'http://localhost:8000/room-1' }} renew={vi.fn(async () => undefined)} />,
    );

    const frame = document.querySelector('iframe');
    expect(frame?.src).toContain('http://localhost:8000/room-1?jwt=the-jwt');
    expect(frame?.allow).toContain('camera');
    expect(document.head.querySelector('script')).toBeNull();
    expect(window.location.href).not.toContain('the-jwt');

    act(() => frame?.dispatchEvent(new Event('load')));
    expect(screen.queryByRole('status')).not.toBeInTheDocument();

    unmount();
    expect(document.querySelector('iframe')).toBeNull();
  });

  it('closes the call when the room is left', async () => {
    const { unmount } = render(<VideoCall pass={PASS} renew={vi.fn(async () => undefined)} />);
    scriptLoads();
    await waitFor(() => expect(double.instances).toHaveLength(1));

    unmount();

    expect(double.instances[0].dispose).toHaveBeenCalledTimes(1);
  });

  it('renews the pass before it expires without closing the call', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const expiresAt = new Date(Date.now() + 5 * 60_000).toISOString();
    const renew = vi.fn(async () => undefined);
    render(<VideoCall pass={{ ...PASS, expiresAt }} renew={renew} />);
    scriptLoads();
    await waitFor(() => expect(double.instances).toHaveLength(1));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(4.6 * 60_000);
    });

    expect(renew).toHaveBeenCalledTimes(1);
    expect(double.instances).toHaveLength(1);
    expect(double.instances[0].dispose).not.toHaveBeenCalled();
  });

  it('reconnects with a new pass after a fatal error', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const renew = vi.fn(async () => undefined);
    const { rerender } = render(<VideoCall pass={PASS} renew={renew} />);
    scriptLoads();
    await waitFor(() => expect(double.instances).toHaveLength(1));

    act(() => double.instances[0].fire('errorOccurred', { error: { isFatal: true } }));
    expect(screen.getByRole('status')).toHaveTextContent('Reconectando el video…');
    expect(double.instances[0].dispose).toHaveBeenCalled();

    rerender(<VideoCall pass={{ ...PASS, token: 'new-jwt' }} renew={renew} />);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(2100);
    });

    expect(renew).toHaveBeenCalled();
    await waitFor(() => expect(double.instances).toHaveLength(2));
    expect(double.instances[1].options.jwt).toBe('new-jwt');
  });

  it('ignores an error Jitsi recovers from', async () => {
    render(<VideoCall pass={PASS} renew={vi.fn(async () => undefined)} />);
    scriptLoads();
    await waitFor(() => expect(double.instances).toHaveLength(1));

    act(() => double.instances[0].fire('videoConferenceJoined'));
    act(() => double.instances[0].fire('errorOccurred', { error: { isFatal: false } }));

    expect(double.instances[0].dispose).not.toHaveBeenCalled();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('says the script did not load, and tries again', async () => {
    const user = userEvent.setup();
    const renew = vi.fn(async () => undefined);
    render(<VideoCall pass={PASS} renew={renew} />);

    scriptLoads(false);

    expect(await screen.findByText('No pudimos abrir el video')).toBeInTheDocument();
    window.JitsiMeetExternalAPI = double.Jitsi as never;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    await waitFor(() => expect(document.head.querySelectorAll('script')).toHaveLength(1));
    expect(renew).toHaveBeenCalled();
  });

  it('says when the browser did not allow the camera or the microphone', async () => {
    render(<VideoCall pass={PASS} renew={vi.fn(async () => undefined)} />);
    scriptLoads();
    await waitFor(() => expect(double.instances).toHaveLength(1));

    act(() => double.instances[0].fire('cameraError', { type: 'gum.permission_denied' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Tu navegador no dejó usar la cámara o el micrófono');
  });

  it('lets the person come back after leaving the call', async () => {
    const user = userEvent.setup();
    render(<VideoCall pass={PASS} renew={vi.fn(async () => undefined)} />);
    scriptLoads();
    await waitFor(() => expect(double.instances).toHaveLength(1));

    act(() => double.instances[0].fire('readyToClose'));
    expect(screen.getByText('Saliste del video. La sala sigue abierta.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Volver a entrar' }));
    await waitFor(() => expect(double.instances).toHaveLength(2));
  });
});
