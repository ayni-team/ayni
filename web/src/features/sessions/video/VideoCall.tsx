import { useEffect, useRef, useState } from 'react';
import { serverNow } from '@/shared/time';
import { Button, IconAlert, cx } from '@/shared/ui';
import type { JoinedSession } from '../session/session';
import {
  MAX_RECONNECTS,
  RECONNECT_DELAY_MS,
  isFatal,
  loadJitsi,
  plainFrameUrl,
  renewDelay,
  videoTarget,
  type JitsiApi,
} from './jitsi';

export type VideoPass = NonNullable<JoinedSession['video']>;

export interface VideoCallProps {
  pass: VideoPass;
  /** Asks for a new pass (`POST join`); it arrives back through `pass`. */
  renew: () => Promise<unknown>;
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
export function VideoCall({ pass, renew }: VideoCallProps) {
  const box = useRef<HTMLDivElement>(null);
  const latest = useRef(pass);
  latest.current = pass;
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
      return () => frame.remove();
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
        api.addListener('videoConferenceJoined', () => {
          failures.current = 0;
        });
        api.addListener('readyToClose', () => {
          api?.dispose();
          api = null;
          setState('LEFT');
        });
        api.addListener('errorOccurred', (payload) => {
          if (!isFatal(payload)) return;
          api?.dispose();
          api = null;
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
      api?.dispose();
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
    <div>
      <div className="relative h-[220px] overflow-hidden rounded-[20px] bg-ink lg:h-[240px]">
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
        <p role="alert" className="mt-2 flex items-start gap-2 rounded-xl bg-credit-soft p-3 text-[13px] text-ink">
          <IconAlert className="mt-0.5 h-4 w-4 shrink-0 text-credit-text" />
          Tu navegador no dejó usar la cámara o el micrófono. Revisa los permisos de este sitio.
        </p>
      )}
    </div>
  );
}
