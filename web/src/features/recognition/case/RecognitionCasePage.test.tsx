import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { fakeServer } from '@/test/fakeServer';
import { CARLOS, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import RecognitionCasePage from './RecognitionCasePage';

const ID = 'd0000000-0000-4000-8000-000000000001';
const QUIET = 'e0000000-0000-4000-8000-000000000001';
const FLAGGED = 'e0000000-0000-4000-8000-000000000002';

const SHORT: Schema<'RecognitionCaseAlert'> = {
  sessionId: FLAGGED,
  kind: 'SHORT_SESSIONS',
  severity: 'MEDIUM',
  description: 'Duró 22 minutos de 60 reservados.',
};

const CASE: Schema<'RecognitionCase'> = {
  id: ID,
  status: 'UNDER_REVIEW',
  student: { id: 's1', name: 'Bruno Salas', studentCode: 'U202300002' },
  totalHours: 42,
  sessionsCount: 38,
  averageRating: 4.8,
  submittedAt: '2026-10-20T15:00:00Z',
  reviewedAt: null,
  decisionReason: null,
  sessions: [
    {
      sessionId: QUIET,
      startedAt: '2026-10-21T21:02:00Z',
      endedAt: '2026-10-21T22:00:00Z',
      durationMinutes: 58,
      hours: 1,
      presenceVerified: true,
      stars: 5,
      alerts: [],
    },
    {
      sessionId: FLAGGED,
      startedAt: '2026-10-20T23:00:00Z',
      endedAt: '2026-10-20T23:22:00Z',
      durationMinutes: 22,
      hours: 1,
      presenceVerified: true,
      stars: 4,
      alerts: [SHORT],
    },
  ],
  alerts: [SHORT],
};

function evidence(sessionId: string, taught: string): Schema<'RecognitionSessionEvidence'> {
  return {
    sessionId,
    requestId: ID,
    taught: { catalogItemId: 'c1', name: taught, kind: 'COURSE', courseCode: 'CC215' },
    scheduledStart: '2026-10-20T23:00:00Z',
    scheduledEnd: '2026-10-21T00:00:00Z',
    startedAt: '2026-10-20T23:00:00Z',
    endedAt: '2026-10-20T23:22:00Z',
    durationMinutes: 22,
    hours: 1,
    attendance: [
      { role: 'TUTOR', userId: 't1', name: 'Bruno Salas', presenceVerified: true },
      { role: 'STUDENT', userId: 'a1', name: 'Ana Torres', presenceVerified: true },
    ],
    tutorStars: 4,
  };
}

function serveCase(body: Schema<'RecognitionCase'> = CASE) {
  const server = fakeServer();
  serveFrame(server, CARLOS);
  signIn('COORDINATOR');
  server
    .on('GET', `/api/v1/coordinator/recognition/requests/${ID}`, { body })
    .on('GET', `/api/v1/coordinator/recognition/requests/${ID}/sessions/${FLAGGED}`, {
      body: evidence(FLAGGED, 'Algoritmos'),
    })
    .on('GET', `/api/v1/coordinator/recognition/requests/${ID}/sessions/${QUIET}`, {
      body: evidence(QUIET, 'Bases de datos'),
    });
  return server;
}

function renderCase() {
  return renderWithProviders(<RecognitionCasePage />, {
    route: `/coordinador/reconocimiento/${ID}`,
    path: '/coordinador/reconocimiento/:id',
  });
}

describe('FE-51 RecognitionCasePage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the request as it was submitted: the student, the figures and the open alerts', async () => {
    serveCase();
    renderCase();

    expect(await screen.findByRole('heading', { level: 1, name: 'Bruno Salas' })).toBeInTheDocument();
    expect(screen.getByText('U202300002 · enviada el 20 oct 2026 · así se presentó, no cambia')).toBeInTheDocument();
    expect(screen.getByText('En revisión')).toBeInTheDocument();
    expect(screen.getByText('42')).toBeInTheDocument();
    expect(screen.getByText('38')).toBeInTheDocument();
    expect(screen.getByText('★ 4.8')).toBeInTheDocument();
    expect(screen.getByText('Alertas abiertas').nextSibling).toHaveTextContent('1');
    expect(screen.getByRole('link', { name: /Volver a solicitudes/ })).toHaveAttribute(
      'href',
      '/coordinador/reconocimiento',
    );
  });

  it('puts the alert of the audit inside the session it is about, in Spanish', async () => {
    serveCase();
    renderCase();

    const flagged = await screen.findByRole('button', { name: /Ver la evidencia de la sesión del Mar 20 oct/ });
    expect(within(flagged).getByText('Duración corta · gravedad media')).toBeInTheDocument();
    expect(within(flagged).getByText('Duró 22 minutos de 60 reservados.')).toBeInTheDocument();
    const quiet = screen.getByRole('button', { name: /Ver la evidencia de la sesión del Mié 21 oct/ });
    expect(within(quiet).queryByText(/gravedad/)).not.toBeInTheDocument();
  });

  it('opens on the session with an alert and shows its evidence and the attendance of both', async () => {
    serveCase();
    renderCase();

    expect(await screen.findByText('Algoritmos · CC215')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Mar 20 oct/ })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('18:00 – 19:00')).toBeInTheDocument();
    expect(screen.getByText('18:00 – 18:22 · 22 min · 1 hora')).toBeInTheDocument();
    expect(screen.getByText('★ 4')).toBeInTheDocument();
    expect(screen.getByText('Bruno Salas · tutor')).toBeInTheDocument();
    expect(screen.getByText('Ana Torres · estudiante')).toBeInTheDocument();
    expect(screen.getAllByText('Verificada')).toHaveLength(2);
  });

  it('shows the evidence of another session when the coordinator chooses it', async () => {
    serveCase();
    const { user } = renderCase();
    await screen.findByText('Algoritmos · CC215');

    await user.click(screen.getByRole('button', { name: /Mié 21 oct/ }));

    expect(await screen.findByText('Bases de datos · CC215')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Mié 21 oct/ })).toHaveAttribute('aria-pressed', 'true');
  });

  it('says it does not exist when the request is not of this university', async () => {
    const server = serveCase();
    server.on('GET', `/api/v1/coordinator/recognition/requests/${ID}`, {
      status: 404,
      body: { status: 404, message: 'not found' },
    });
    renderCase();

    expect(await screen.findByText('No encontramos lo que buscas')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('not found')).not.toBeInTheDocument());
  });
});
