import { useEffect, useRef, useState } from 'react';
import { serverNow } from '@/shared/time';
import { Button, IconAlert, cx } from '@/shared/ui';
import type { JoinedSession } from '../session/session';
import {
  MAX_RECONNECTS,
  RECONNECT_DELAY_MS,
  anybodySharing,
  isFatal,
  loadJitsi,
  plainFrameUrl,
  ownShareOn,
  renewDelay,
  videoTarget,
  type JitsiApi,
} from './jitsi';

export type VideoPass = NonNullable<JoinedSession['video']>;

/**
 * How this call shares a screen. `command`: through the API of Jitsi. `own-button`: the plain
 * iframe of development has no API, so only the call's own button in its toolbar does it.
 */
export type ShareControl = { kind: 'command'; toggle: () => void } | { kind: 'own-button' };

/** What the events of the call say about sharing. Only the API sends them. */
export interface CallShare {
  /** Somebody in the call, this person included, shares a screen. */
  anybody: boolean;
  /** This person shares theirs. */
  mine: boolean;
}

const NOBODY_SHARES: CallShare = { anybody: false, mine: false };

export interface VideoCallProps {
  pass: VideoPass;
  /** Asks for a new pass (`POST join`); it arrives back through `pass`. */
  renew: () => Promise<unknown>;
  /** Fills the box its parent gives it, which may change size without restarting the call. */
  fill?: boolean;
  /** How to share a screen in this call, or null while there is no call to share in. */
  onShareControl?: (control: ShareControl | null) => void;
  onShareChange?: (share: CallShare) => void;
}

type CallState = 'LOADING' | 'IN_CALL' | 'RECONNECTING' | 'LEFT' | 'FAILED';

const WORDS: Partial<Record<CallState, string>> = {
  LOADING: 'Conectando el video…',
  RECONNECTING: 'Reconectando el video…',
};

/**
 * The call, in the iframe of Jitsi. The JWT goes as an option of its API, never in the address of
 * this page. The controls are Jitsi's. The pass is renewed in the background: Jitsi checks it when
 * somebody enters, so a call in progress goes on, and a reconnection finds a valid one.
 */
export function VideoCall({ pass, renew, fill = false, onShareControl, onShareChange }: VideoCallProps) {
  const box = useRef<HTMLDivElement>(null);
  const latest = useRef(pass);
  latest.current = pass;
  // Read from the events of one call: a new function from the parent does not mean a new call.
  const report = useRef({ onShareControl, onShareChange });
  report.current = { onShareControl, onShareChange };
  const failures = useRef(0);
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<CallState>('LOADING');
  const [noDevices, setNoDevices] = useState(false);

  useEffect(() => {
    let over = false;
    let api: JitsiApi | null = null;
    let reconnecting: number | undefined;
    const target = videoTarget(latest.current.url);
    if (!target) {
      setState('FAILED');
      return undefined;
    }

    if (target.plainHttp) {
      const frame = document.createElement('iframe');
      frame.src = plainFrameUrl(latest.current.url ?? '', latest.current.token);
      frame.allow = 'camera; microphone; display-capture; autoplay; clipboard-write';
      frame.title = 'Videollamada';
      frame.className = 'h-full w-full border-0';
      frame.onload = () => setState('IN_CALL');
      box.current?.appendChild(frame);
      report.current.onShareControl?.({ kind: 'own-button' });
      return () => {
        frame.remove();
        report.current.onShareControl?.(null);
      };
    }

    let share = NOBODY_SHARES;
    function shareChanged(next: Partial<CallShare>) {
      share = { ...share, ...next };
      report.current.onShareChange?.(share);
    }

    /** The call is gone: nobody shares in it any more, and there is nothing to send a command to. */
    function closeCall() {
      api?.dispose();
      api = null;
      report.current.onShareControl?.(null);
      if (share.anybody || share.mine) shareChanged(NOBODY_SHARES);
    }

    function reconnect() {
      failures.current += 1;
      if (failures.current > MAX_RECONNECTS) {
        setState('FAILED');
        return;
      }
      setState('RECONNECTING');
      reconnecting = window.setTimeout(() => {
        void renew().finally(() => {
          if (!over) setAttempt((current) => current + 1);
        });
      }, RECONNECT_DELAY_MS);
    }

    setState((current) => (current === 'RECONNECTING' ? current : 'LOADING'));
    loadJitsi(target.origin).then(
      (Jitsi) => {
        if (over || !box.current) return;
        api = new Jitsi(target.domain, {
          roomName: target.roomName,
          jwt: latest.current.token,
          parentNode: box.current,
          width: '100%',
          height: '100%',
          configOverwrite: { prejoinConfig: { enabled: false }, prejoinPageEnabled: false, disableDeepLinking: true },
          interfaceConfigOverwrite: { MOBILE_APP_PROMO: false },
        });
        // From here Jitsi draws its own state, such as asking for the camera: it must be seen.
        setState('IN_CALL');
        const call = api;
        report.current.onShareControl?.({ kind: 'command', toggle: () => call.executeCommand('toggleShareScreen') });
        api.addListener('screenSharingStatusChanged', (payload) => shareChanged({ mine: ownShareOn(payload) }));
        api.addListener('contentSharingParticipantsChanged', (payload) =>
          shareChanged({ anybody: anybodySharing(payload) }),
        );
        api.addListener('videoConferenceJoined', () => {
          failures.current = 0;
        });
        api.addListener('readyToClose', () => {
          closeCall();
          setState('LEFT');
        });
        api.addListener('errorOccurred', (payload) => {
          if (!isFatal(payload)) return;
          closeCall();
          reconnect();
        });
        api.addListener('cameraError', () => setNoDevices(true));
        api.addListener('micError', () => setNoDevices(true));
      },
      () => {
        if (!over) setState('FAILED');
      },
    );

    return () => {
      over = true;
      window.clearTimeout(reconnecting);
      closeCall();
    };
    // `renew` is called from events of this instance; a new function does not mean a new call.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attempt]);

  useEffect(() => {
    const delay = renewDelay(pass.expiresAt, serverNow());
    if (delay === null) return undefined;
    const timer = window.setTimeout(() => void renew(), delay);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pass.expiresAt]);

  function again() {
    failures.current = 0;
    setState('LOADING');
    void renew().finally(() => setAttempt((current) => current + 1));
  }

  const words = WORDS[state];
  return (
    <div className={cx(fill && 'flex h-full min-h-0 flex-col')}>
      <div
        className={cx(
          'relative overflow-hidden rounded-[20px] bg-ink',
          fill ? 'min-h-0 flex-1' : 'h-[220px] lg:h-[240px]',
        )}
      >
        <div ref={box} aria-label="Videollamada" className={cx('h-full w-full', state !== 'IN_CALL' && 'opacity-0')} />
        {words && (
          <p role="status" className="absolute inset-0 flex items-center justify-center text-sm text-white">
            {words}
          </p>
        )}
        {state === 'LEFT' && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 p-4 text-center text-white">
            <p className="text-sm">Saliste del video. La sala sigue abierta.</p>
            <Button variant="secondary" size="sm" onClick={again}>
              Volver a entrar
            </Button>
          </div>
        )}
        {state === 'FAILED' && (
          <div
            role="alert"
            className="absolute inset-0 flex flex-col items-center justify-center gap-2 p-4 text-center text-white"
          >
            <p className="text-sm font-semibold">No pudimos abrir el video</p>
            <p className="text-xs text-white/80">El resto de la sala sigue funcionando.</p>
            <Button variant="secondary" size="sm" onClick={again}>
              Reintentar
            </Button>
          </div>
        )}
      </div>
      {noDevices && (
        <p
          role="alert"
          className="mt-2 flex shrink-0 items-start gap-2 rounded-xl bg-credit-soft p-3 text-[13px] text-ink"
        >
          <IconAlert className="mt-0.5 h-4 w-4 shrink-0 text-credit-text" />
          Tu navegador no dejó usar la cámara o el micrófono. Revisa los permisos de este sitio.
        </p>
      )}
    </div>
  );
}
