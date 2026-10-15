import { FormEvent, useState } from 'react';
import { api, ApiError } from '../../shared/api/client';

/**
 * Asking for an access link (US38). There are no passwords: the link arrives
 * at the institutional email and opening it signs the person in.
 */
export default function SignInPage() {
  const [email, setEmail] = useState('');
  const [state, setState] = useState<'idle' | 'sending' | 'sent'>('idle');
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setState('sending');
    setError(null);
    try {
      await api.post('/v1/access/request', { email });
      setState('sent');
    } catch (failure) {
      setState('idle');
      setError(failure instanceof ApiError ? failure.message : 'No se pudo enviar el enlace');
    }
  }

  if (state === 'sent') {
    return (
      <>
        <h1>Revisa tu correo</h1>
        <p>
          Enviamos un enlace a <strong>{email}</strong>. Funciona una sola vez y vence en
          minutos.
        </p>
      </>
    );
  }

  return (
    <>
      <h1>Ingresar</h1>
      <p>Escribe tu correo institucional y te enviaremos un enlace para ingresar.</p>
      <form onSubmit={submit} style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
        <input
          type="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="u202400001@upc.edu.pe"
          style={{ padding: '0.5rem', minWidth: '18rem' }}
        />
        <button type="submit" disabled={state === 'sending'}>
          Enviar enlace
        </button>
      </form>
      {error && <p style={{ color: '#b00020' }}>{error}</p>}
    </>
  );
}
