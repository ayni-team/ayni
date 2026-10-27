import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { api, isApiError } from '@/shared/api';
import { rememberSession } from '@/shared/session';
import { ButtonLink, Card, ErrorState, PageLoading } from '@/shared/ui';

type Failure = 'spent' | 'platform';

/**
 * FE-02 · `/access/confirm?token=` · public. Where the emailed link lands (US38, TS03). It hands the
 * token to the API once, keeps the session and goes to `/`, which sends each role where it belongs
 * (and a student who has not finished the onboarding to `/onboarding`).
 */
export default function ConfirmAccessPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [failure, setFailure] = useState<Failure | null>(null);
  const [attempt, setAttempt] = useState(0);
  // A link works once: React's development double effect must not spend it twice.
  const sent = useRef(-1);

  useEffect(() => {
    const token = params.get('token');
    if (!token) {
      setFailure('spent');
      return;
    }
    if (sent.current === attempt) return;
    sent.current = attempt;
    api
      .post('/api/v1/access/confirm', { body: { token } })
      .then((answer) => {
        rememberSession(answer);
        navigate('/', { replace: true });
      })
      .catch((error: unknown) => {
        // 400: unknown, used or expired; 409: used at the same moment somewhere else.
        setFailure(isApiError(error) && (error.status === 400 || error.status === 409) ? 'spent' : 'platform');
      });
  }, [params, navigate, attempt]);

  if (failure === 'spent') {
    return (
      <Card>
        <ErrorState
          title="El enlace venció o ya se usó"
          description="Pide uno nuevo: llega a tu correo institucional y funciona una sola vez."
          action={<ButtonLink to="/ingresar">Pedir otro enlace</ButtonLink>}
        />
      </Card>
    );
  }
  if (failure === 'platform') {
    return (
      <Card>
        <ErrorState
          description="No pudimos abrir tu sesión. Inténtalo de nuevo."
          onRetry={() => {
            setFailure(null);
            setAttempt((value) => value + 1);
          }}
        />
      </Card>
    );
  }
  return <PageLoading label="Abriendo tu sesión" />;
}
