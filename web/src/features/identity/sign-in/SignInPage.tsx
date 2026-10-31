import { useEffect, useState, type FormEvent } from 'react';
import { errorText, useApiMutation } from '@/shared/api';
import { useUniversityPreview } from '@/shared/brand';
import { AyniLogo, Button, Card, Field, Spinner, TextField, UniversityLogo } from '@/shared/ui';

const RESEND_SECONDS = 30;

/**
 * FE-01 · `/ingresar` · public.
 *
 * The student enters an institutional email. Once the domain identifies an
 * affiliated university, the screen adopts that university's identity and
 * allows the student to request a one-use access link.
 *
 * A domain of no university only hints, it does not block: the platform administrator's address
 * belongs to none and still gets a link. The backend decides, and refuses anybody else with a 400.
 */
export default function SignInPage() {
  const [email, setEmail] = useState('');
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [resendSeconds, setResendSeconds] = useState(0);

  const preview = useUniversityPreview(email);

  /*
   * Narrow the nullable value once here.
   * This avoids TS18047 when rendering name/code/logo below.
   */
  const university = preview.status === 'found' ? preview.university : null;

  const universityFound = university !== null;

  // Once the lookup has answered, whatever it said, the backend is the one to decide.
  const canSubmit = preview.status === 'found' || preview.status === 'notAffiliated' || preview.status === 'error';

  const request = useApiMutation('post', '/api/v1/access/request', {
    onSuccess: (_, variables) => {
      const destination = variables.body.email?.trim();

      if (!destination) return;

      setSentTo(destination);
      setResendSeconds(RESEND_SECONDS);
    },
  });

  /*
   * Countdown for resend.
   * A single timeout is scheduled each second and stops automatically at 0.
   */
  useEffect(() => {
    if (sentTo === null || resendSeconds <= 0) {
      return;
    }

    const timer = window.setTimeout(() => {
      setResendSeconds((current) => Math.max(0, current - 1));
    }, 1000);

    return () => {
      window.clearTimeout(timer);
    };
  }, [sentTo, resendSeconds]);

  function requestAccessLink(destination: string) {
    request.mutate({
      body: {
        email: destination.trim(),
      },
    });
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!canSubmit) {
      return;
    }

    requestAccessLink(email);
  }

  function useAnotherEmail() {
    setEmail('');
    setSentTo(null);
    setResendSeconds(0);
    request.reset();
  }

  const fieldHint =
    preview.status === 'notAffiliated'
      ? 'No encontramos una universidad afiliada a Ayni con ese dominio. Si administras Ayni, igual puedes pedir tu enlace.'
      : undefined;

  const fieldError =
    request.isError && sentTo === null
      ? errorText(request.error, {
          badRequest:
            preview.status === 'notAffiliated'
              ? 'Tu universidad todavía no está afiliada a Ayni'
              : 'No pudimos enviar el enlace a ese correo. Revisa que sea el institucional',
        })
      : preview.status === 'error'
        ? errorText(undefined)
        : undefined;

  const resendError =
    request.isError && sentTo !== null
      ? errorText(request.error, {
          badRequest: 'No pudimos reenviar el enlace. Inténtalo nuevamente.',
        })
      : undefined;

  const resendLabel = `0:${String(resendSeconds).padStart(2, '0')}`;

  return (
    <div className="flex w-full flex-col items-center gap-7">
      <AyniLogo className="text-gray-950" />

      <Card className="relative w-full overflow-hidden !p-0">
        <div
          className={universityFound || sentTo ? 'h-1 w-full bg-brand' : 'h-1 w-full bg-transparent'}
          aria-hidden="true"
        />

        {sentTo ? (
          <div className="flex flex-col gap-5 p-6 sm:p-8" role="status">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-brand-soft text-brand-text">
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                className="h-6 w-6"
                aria-hidden="true"
              >
                <rect x="3" y="5" width="18" height="14" rx="2" />
                <path d="m3 7 9 6 9-6" />
              </svg>
            </div>

            <div>
              <h1 className="text-2xl font-semibold tracking-tight text-gray-900">Revisa tu correo</h1>

              <p className="mt-3 text-sm leading-6 text-gray-600">
                Te enviamos un enlace a {sentTo}. Vence en 10 minutos.
              </p>
            </div>

            <Button
              variant="secondary"
              fullWidth
              loading={request.isPending}
              disabled={resendSeconds > 0}
              className="border-0 bg-gray-100 text-gray-500 hover:bg-gray-100 disabled:opacity-100"
              onClick={() => requestAccessLink(sentTo)}
            >
              {resendSeconds > 0 ? `Reenviar en ${resendLabel}` : 'Reenviar el enlace'}
            </Button>

            {resendError && <p className="text-sm font-medium text-red-700">{resendError}</p>}

            <button
              type="button"
              className="w-fit text-sm font-medium text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
              onClick={useAnotherEmail}
            >
              Usar otro correo
            </button>

            <p className="text-xs text-gray-500">Reenviar se habilita 30 segundos después del envío.</p>
          </div>
        ) : (
          <form onSubmit={submit} className="flex flex-col gap-5 p-6 sm:p-8" noValidate>
            <div>
              <h1 className="text-2xl font-semibold tracking-tight text-gray-900">Ingresa a Ayni</h1>

              <p className="mt-3 text-sm leading-6 text-gray-600">
                Usa tu correo institucional. Te enviamos un enlace para entrar: no hay contraseña.
              </p>
            </div>

            <Field label="Correo institucional" hint={fieldHint} error={fieldError}>
              <TextField
                type="email"
                autoComplete="email"
                inputMode="email"
                placeholder="nombre@universidad.edu.pe"
                value={email}
                onChange={(event) => {
                  request.reset();
                  setEmail(event.target.value);
                }}
                className={universityFound ? undefined : 'focus:!border-gray-400 focus:!ring-gray-400'}
              />
            </Field>

            {preview.status === 'searching' && (
              <div className="flex items-center gap-2 text-sm text-gray-600" role="status">
                <Spinner size="sm" decorative />

                <span>Buscando tu universidad…</span>
              </div>
            )}

            {university && (
              <div className="flex items-center gap-3 rounded-xl bg-brand-soft px-4 py-3">
                <UniversityLogo university={university} size="sm" />

                <div className="min-w-0">
                  <p className="font-medium leading-5 text-gray-900">{university.name ?? 'Universidad'}</p>

                  <p className="text-xs font-medium text-brand-text">{university.code ?? ''}</p>
                </div>
              </div>
            )}

            <Button
              type="submit"
              fullWidth
              loading={request.isPending}
              disabled={!canSubmit}
              className={canSubmit ? undefined : 'disabled:!bg-gray-100 disabled:!text-gray-400 disabled:opacity-100'}
            >
              Enviarme el enlace
            </Button>
          </form>
        )}
      </Card>

      <p className="max-w-sm text-center text-xs text-gray-500">
        Cada ingreso es un enlace de un solo uso que vence en 10 minutos.
      </p>
    </div>
  );
}
