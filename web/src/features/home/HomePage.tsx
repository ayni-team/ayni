import { useEffect, useState } from 'react';
import { CURRENT_TENANT } from '../../shared/config';

/**
 * Landing screen. It exists to prove the chain works end to end: the browser
 * reaches the web container, the web container proxies to the API and the API
 * answers.
 *
 * It calls the health endpoint directly rather than through the api client,
 * because that endpoint belongs to the platform and not to the product.
 */
export default function HomePage() {
  const [status, setStatus] = useState('comprobando...');

  useEffect(() => {
    fetch('/actuator/health')
      .then((response) => response.json())
      .then((body) => setStatus(body.status))
      .catch(() => setStatus('sin respuesta'));
  }, []);

  return (
    <>
      <h1>Banco de tiempo académico</h1>
      <p>Una hora de tutoría enseñada equivale a una hora recibida.</p>

      <dl style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '0.5rem 1.5rem' }}>
        <dt style={{ color: '#666' }}>API</dt>
        <dd style={{ margin: 0, fontWeight: 600 }}>{status}</dd>
        <dt style={{ color: '#666' }}>Universidad</dt>
        <dd style={{ margin: 0, fontWeight: 600 }}>{CURRENT_TENANT}</dd>
      </dl>

      <p style={{ marginTop: '2rem', color: '#666' }}>
        Documentación de la API en{' '}
        <a href="http://localhost:8080/swagger-ui.html">Swagger UI</a>.
      </p>
    </>
  );
}
