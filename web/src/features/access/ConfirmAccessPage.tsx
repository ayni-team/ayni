import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../../shared/api/client';
import { rememberSession, Session } from '../../shared/session';

/**
 * Where the emailed link lands (US38, TS03). It hands the token of the link to
 * the API once, keeps the session token that comes back and goes home.
 */
export default function ConfirmAccessPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  // A link works once: React's development double effect must not spend it twice.
  const sent = useRef(false);

  useEffect(() => {
    const token = params.get('token');
    if (!token) {
      setError('El enlace no tiene token');
      return;
    }
    if (sent.current) {
      return;
    }
    sent.current = true;
    api
      .post<Session>('/v1/access/confirm', { token })
      .then((session) => {
        rememberSession(session);
        navigate('/', { replace: true });
      })
      .catch((failure) =>
        setError(failure instanceof ApiError ? failure.message : 'No se pudo abrir el enlace'),
      );
  }, [params, navigate]);

  return error ? (
    <>
      <h1>No pudimos abrir el enlace</h1>
      <p style={{ color: '#b00020' }}>{error}</p>
      <p>Pide uno nuevo desde la pantalla de ingreso.</p>
    </>
  ) : (
    <p>Abriendo tu sesión...</p>
  );
}
