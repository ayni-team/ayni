import { useState, type FormEvent } from 'react';
import { errorText, useApiMutation } from '@/shared/api';
import { useUniversityPreview } from '@/shared/brand';
import { AyniLogo, Button, Card, Field, TextField, UniversityLogo } from '@/shared/ui';

/**
 * FE-01 · `/ingresar` · public. The foundation's working base: one field, the institutional email;
 * once it looks like an address the university of its domain paints its name, logo and colours, and
 * the submit asks for the link. The rest of the screen (resend after 30 seconds, the frame's final
 * look) is FE-01's pull request.
 */
export default function SignInPage() {
  const [email, setEmail] = useState('');
  const [sentTo, setSentTo] = useState<string | null>(null);
  const preview = useUniversityPreview(email);
  const request = useApiMutation('post', '/api/v1/access/request', {
    onSuccess: (_, variables) => setSentTo(variables.body.email ?? null),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (preview.status !== 'found') return;
    request.mutate({ body: { email: email.trim() } });
  }

  const error =
    preview.status === 'notAffiliated'
      ? 'Tu universidad todavía no está afiliada a Ayni'
      : preview.status === 'error'
        ? errorText(undefined)
        : request.isError
          ? errorText(request.error, { badRequest: 'No pudimos enviar el enlace a ese correo. Revisa que sea el institucional' })
          : undefined;

  return (
    <Card className="flex flex-col gap-6">
      <div className="flex flex-col items-center gap-3 text-center">
        {preview.university ? (
          <>
            <UniversityLogo university={preview.university} size="lg" />
            <p className="text-sm font-medium text-gray-700">{preview.university.name}</p>
          </>
        ) : (
          <AyniLogo className="text-brand-text" />
        )}
      </div>

      {sentTo ? (
        <div className="flex flex-col gap-4 text-center" role="status">
          <h1 className="text-xl font-semibold text-gray-900">Revisa tu correo</h1>
          <p className="text-sm text-gray-700">Te enviamos un enlace a {sentTo}. Vence en 10 minutos.</p>
          <Button
            variant="secondary"
            onClick={() => {
              setSentTo(null);
              request.reset();
            }}
          >
            Usar otro correo
          </Button>
        </div>
      ) : (
        <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
          <div className="text-center">
            <h1 className="text-xl font-semibold text-gray-900">Ingresar</h1>
            <p className="mt-1 text-sm text-gray-600">Escribe tu correo institucional y te enviaremos un enlace para ingresar.</p>
          </div>
          <Field
            label="Correo institucional"
            hint={preview.status === 'searching' ? 'Buscando tu universidad…' : undefined}
            error={error}
          >
            <TextField
              type="email"
              autoComplete="email"
              inputMode="email"
              placeholder="u202400001@upc.edu.pe"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </Field>
          <Button type="submit" fullWidth loading={request.isPending} disabled={preview.status !== 'found'}>
            Enviarme el enlace
          </Button>
        </form>
      )}
    </Card>
  );
}
