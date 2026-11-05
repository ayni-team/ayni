import type { ReactNode } from 'react';
import { Avatar, Badge, Card, Skeleton } from '@/shared/ui';
import { realFigures, shortDay, studentRating, taughtText, timeRange } from './recognitionCase';
import { useSessionEvidence } from './useRecognitionCase';

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4 py-1.5 text-sm">
      <dt className="text-ink-2">{label}</dt>
      <dd className="text-right text-ink">{children}</dd>
    </div>
  );
}

const ROLES = { TUTOR: 'tutor', STUDENT: 'estudiante' } as const;

/** The evidence of the session the coordinator chose: what was taught, when, and who was there. */
export function EvidencePanel({ requestId, sessionId, zone }: { requestId: string; sessionId?: string; zone: string }) {
  const evidence = useSessionEvidence(requestId, sessionId);
  const data = evidence.data;
  const title = data ? `Evidencia · ${shortDay(data.startedAt, zone)}` : 'Evidencia';

  return (
    <Card title={title}>
      {!sessionId ? (
        <p className="text-sm text-ink-2">Elige una sesión para ver su evidencia.</p>
      ) : evidence.isPending ? (
        <Skeleton lines={5} label="Cargando la evidencia" />
      ) : evidence.isError || !data ? (
        <div role="alert" className="flex flex-col items-start gap-2 text-sm text-ink-2">
          No pudimos cargar la evidencia de esta sesión.
          <button
            type="button"
            onClick={() => void evidence.refetch()}
            className="font-semibold text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
          >
            Reintentar
          </button>
        </div>
      ) : (
        <>
          <dl>
            <Row label="Enseñó">{taughtText(data)}</Row>
            <Row label="Programada">{timeRange(data.scheduledStart, data.scheduledEnd, zone) || 'Sin dato'}</Row>
            <Row label="Real">{realFigures(data, zone) || 'Sin dato'}</Row>
            <Row label="Calificación del estudiante">{studentRating(data)}</Row>
          </dl>
          <h3 className="mb-2 mt-3 text-sm font-semibold text-ink">Asistencia</h3>
          <ul className="flex flex-col gap-3">
            {(data.attendance ?? []).map((attendee, index) => {
              const name = attendee.name ?? 'Participante';
              return (
                <li key={attendee.userId ?? index} className="flex items-center gap-3">
                  <Avatar name={name} decorative />
                  <span className="min-w-0 flex-1 text-sm text-ink">
                    {name}
                    {attendee.role && ` · ${ROLES[attendee.role]}`}
                  </span>
                  {attendee.presenceVerified ? (
                    <Badge tone="info">Verificada</Badge>
                  ) : (
                    <Badge tone="warning">Sin verificar</Badge>
                  )}
                </li>
              );
            })}
          </ul>
        </>
      )}
    </Card>
  );
}
