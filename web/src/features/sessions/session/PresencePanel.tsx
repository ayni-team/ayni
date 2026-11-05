import { useId, useState } from 'react';
import { errorText, isApiError, useApiMutation } from '@/shared/api';
import { formatInZone, serverNow, useCountdown, useZone } from '@/shared/time';
import { Badge, Button, IconAlert, IconClock, cx, useToast } from '@/shared/ui';
import { IconShieldCheck } from './icons';
import {
  CODE_LENGTH,
  attemptsLeftText,
  attemptsText,
  codeFrom,
  confirmedDetail,
  ifNobodyConfirms,
  presenceState,
  unverifiedConsequence,
  type PresenceState,
} from './presence';
import type { Session } from './session';

const RESEND_WAIT_MS = 60_000;

type Refusal = { kind: 'WRONG' | 'WAIT' | 'OTHER'; text?: string };

/** What the panel holds while it is typed in, so the desktop panel and the phone's dialog share it. */
export function usePresence(session: Session, refresh: () => void) {
  const toast = useToast();
  const id = session.id ?? '';
  const [code, setCode] = useState('');
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const [waitUntil, setWaitUntil] = useState<number | null>(null);
  const confirm = useApiMutation('post', '/api/v1/sessions/{id}/presence');
  const resend = useApiMutation('post', '/api/v1/sessions/{id}/presence/resend');
  // Its own start, from the browser's clock: no server's instant is involved.
  const wait = useCountdown(waitUntil, { offsetMs: 0 });
  // The countdown only redraws: the state flips to expired on the second the code stops working.
  useCountdown(session.presence?.expiresAt);
  const state = presenceState(session, serverNow());

  function type(text: string) {
    setCode(codeFrom(text));
    setRefusal(null);
  }

  async function submit() {
    if (code.length !== CODE_LENGTH || confirm.isPending) return;
    setRefusal(null);
    try {
      await confirm.mutateAsync({ path: { id }, body: { code } });
      setCode('');
    } catch (failure) {
      if (isApiError(failure, 422)) setRefusal({ kind: 'WRONG' });
      else if (isApiError(failure, 409)) setRefusal({ kind: 'OTHER', text: 'Ya no se puede confirmar esta sesión.' });
      else setRefusal({ kind: 'OTHER', text: errorText(failure) });
    } finally {
      refresh();
    }
  }

  async function askAgain() {
    if (resend.isPending) return;
    setRefusal(null);
    try {
      await resend.mutateAsync({ path: { id } });
      setCode('');
      setWaitUntil(Date.now() + RESEND_WAIT_MS);
      toast.success('Te enviamos otro código a tu correo');
    } catch (failure) {
      if (isApiError(failure, 409)) {
        // Every refusal of a resend is a 409: too soon, or the three used up.
        setRefusal({ kind: 'WAIT' });
        setWaitUntil(Date.now() + RESEND_WAIT_MS);
      } else {
        setRefusal({ kind: 'OTHER', text: errorText(failure) });
      }
    } finally {
      refresh();
    }
  }

  return {
    state,
    code,
    type,
    refusal,
    submit,
    confirming: confirm.isPending,
    askAgain,
    resending: resend.isPending,
    canResend: waitUntil === null || wait.expired,
  };
}

export type Presence = ReturnType<typeof usePresence>;

function Message({ tone, title, children }: { tone: 'danger' | 'warning'; title: string; children?: string }) {
  return (
    <div
      role="alert"
      className={cx(
        'flex items-start gap-2.5 rounded-xl p-3 text-sm',
        tone === 'danger' ? 'bg-red-50 text-ink' : 'bg-credit-soft text-ink',
      )}
    >
      {tone === 'danger' ? (
        <IconAlert className="mt-0.5 h-5 w-5 shrink-0 text-red-700" />
      ) : (
        <IconClock className="mt-0.5 h-5 w-5 shrink-0 text-credit-text" />
      )}
      <div>
        <p className="font-semibold">{title}</p>
        {children && <p className="mt-0.5 text-[13px] text-ink-2">{children}</p>}
      </div>
    </div>
  );
}

/** Six boxes drawn over one input, which keeps paste, the phone's keyboard and the code of an SMS or email. */
function CodeBoxes({
  code,
  onChange,
  invalid,
  disabled,
  onEnter,
}: {
  code: string;
  onChange: (text: string) => void;
  invalid: boolean;
  disabled: boolean;
  onEnter: () => void;
}) {
  const id = useId();
  const [focused, setFocused] = useState(false);
  return (
    <div className="relative">
      <label htmlFor={id} className="sr-only">
        Código de 6 dígitos
      </label>
      <div aria-hidden="true" className="grid grid-cols-6 gap-2">
        {Array.from({ length: CODE_LENGTH }, (_, index) => {
          const current = focused && index === Math.min(code.length, CODE_LENGTH - 1);
          return (
            <div
              key={index}
              className={cx(
                'flex h-12 items-center justify-center rounded-xl border font-display text-xl font-semibold text-ink',
                disabled ? 'border-line bg-surface' : 'bg-white',
                !disabled && (invalid || current ? 'border-brand' : 'border-line'),
                current && 'border-2',
              )}
            >
              {code[index] ?? ''}
            </div>
          );
        })}
      </div>
      <input
        id={id}
        value={code}
        disabled={disabled}
        inputMode="numeric"
        autoComplete="one-time-code"
        aria-invalid={invalid || undefined}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') onEnter();
        }}
        className="absolute inset-0 h-full w-full cursor-text opacity-0 disabled:cursor-not-allowed"
      />
    </div>
  );
}

function shellClasses(state: PresenceState, framed: boolean): string {
  if (!framed) return '';
  return cx(
    'rounded-[20px] border bg-white p-4 sm:p-5',
    state === 'CONFIRMED' ? 'border-info-text' : state === 'UPCOMING' ? 'border-line' : 'border-brand',
  );
}

/**
 * FE-21 · the presence code, inside the room. Five minutes after the start the server emails a code
 * to each one; whoever does not type it leaves the session unverified.
 */
export function PresencePanel({
  session,
  presence,
  framed = true,
}: {
  session: Session;
  presence: Presence;
  /** False inside the dialog of a phone, which is its own frame. */
  framed?: boolean;
}) {
  const zone = useZone();
  const { state, code, refusal } = presence;
  const data = session.presence;
  const expiresAt = data?.expiresAt ? formatInZone(data.expiresAt, zone, 'time') : '';

  if (state === 'UPCOMING') {
    const at = session.presenceCheckAt ? formatInZone(session.presenceCheckAt, zone, 'time') : '';
    return (
      <section aria-label="Tu presencia" className={shellClasses(state, framed)}>
        <p className="flex items-center gap-2.5 font-display text-lg font-semibold text-ink">
          <IconShieldCheck className="h-6 w-6 text-brand-text" />
          Confirma tu presencia
        </p>
        <p className="mt-2 text-sm text-ink-2">
          {at
            ? `A las ${at} te enviaremos un código de 6 dígitos a tu correo institucional.`
            : 'Te enviaremos un código de 6 dígitos a tu correo institucional.'}
        </p>
      </section>
    );
  }

  if (state === 'CONFIRMED') {
    const time = data?.confirmedAt ? formatInZone(data.confirmedAt, zone, 'time') : '';
    return (
      <section aria-label="Tu presencia" className={shellClasses(state, framed)}>
        <p className="flex items-center gap-2.5 font-display text-lg font-semibold text-ink">
          <IconShieldCheck className="h-6 w-6 text-info-text" />
          Presencia confirmada
        </p>
        <p className="mt-2 text-sm text-ink-2">{confirmedDetail(session, time)}</p>
      </section>
    );
  }

  const spent = state === 'NO_ATTEMPTS';
  const expired = state === 'EXPIRED';
  const wrong = refusal?.kind === 'WRONG';

  return (
    <section aria-label="Tu presencia" className={cx('flex flex-col gap-3', shellClasses(state, framed))}>
      {framed && (
        <p className="flex items-center gap-2.5 font-display text-lg font-semibold text-ink">
          <IconShieldCheck className="h-6 w-6 text-brand-text" />
          Confirma tu presencia
        </p>
      )}
      <p className="text-sm text-ink-2">
        Te enviamos un código de 6 dígitos a tu correo institucional. Escríbelo aquí.
      </p>
      <CodeBoxes
        code={code}
        onChange={presence.type}
        invalid={wrong}
        disabled={spent || expired}
        onEnter={() => void presence.submit()}
      />
      {spent && (
        <Message tone="danger" title="Se acabaron los 5 intentos">
          {unverifiedConsequence(session)}
        </Message>
      )}
      {expired && (
        <Message tone="warning" title="Tu código venció">
          Pide otro código para confirmar tu presencia.
        </Message>
      )}
      {wrong && !spent && (
        <Message tone="danger" title="Ese código no es correcto">
          {attemptsLeftText(data?.attemptsLeft)}
        </Message>
      )}
      {refusal?.kind === 'WAIT' && (
        <Message tone="warning" title="Espera un minuto para pedir otro código">
          Puedes pedirlo 3 veces por sesión. Los intentos usados se mantienen.
        </Message>
      )}
      {refusal?.kind === 'OTHER' && <Message tone="danger" title={refusal.text ?? ''} />}
      <Button
        fullWidth
        onClick={() => void presence.submit()}
        loading={presence.confirming}
        disabled={spent || expired || code.length !== CODE_LENGTH}
      >
        {presence.confirming ? 'Confirmando…' : 'Confirmar código'}
      </Button>
      <div className="flex items-center justify-between gap-3 text-[13px]">
        <span className="text-ink-2">
          {spent
            ? 'Sin intentos'
            : expired
              ? 'Venció'
              : `Vence a las ${expiresAt} · ${attemptsText(data?.attemptsLeft)}`}
        </span>
        <button
          type="button"
          onClick={() => void presence.askAgain()}
          disabled={spent || presence.resending || !presence.canResend}
          className="font-semibold text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand disabled:pointer-events-none disabled:text-ink-3"
        >
          Reenviar código
        </button>
      </div>
      {framed && (
        <div className="border-t border-line pt-3">
          <p className="mb-2 flex items-center justify-between text-sm text-ink">
            Tú <Badge tone="warning">Pendiente</Badge>
          </p>
          <p className="text-xs text-ink-2">{ifNobodyConfirms(session)}</p>
        </div>
      )}
    </section>
  );
}
