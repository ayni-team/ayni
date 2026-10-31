import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useZone } from '@/shared/time';
import { Avatar, Badge, ErrorState, PageHeader, Skeleton } from '@/shared/ui';
import { ratingText, statusBadge } from '../requests/queue';
import { CaseSessions } from './CaseSessions';
import { DecisionPanel } from './DecisionPanel';
import { EvidencePanel } from './EvidencePanel';
import { firstToReview, longDay, type RecognitionCase } from './recognitionCase';
import { useRecognitionCase } from './useRecognitionCase';

const BACK = { to: '/coordinador/reconocimiento', label: 'Volver a solicitudes' };

function Figure({ label, value, warn = false }: { label: string; value: string | number; warn?: boolean }) {
  return (
    <div className="rounded-[16px] border border-line bg-white px-4 py-3">
      <p className="text-xs text-ink-2">{label}</p>
      <p
        className={
          warn
            ? 'mt-1 font-display text-2xl font-semibold text-amber-800'
            : 'mt-1 font-display text-2xl font-semibold text-ink'
        }
      >
        {value}
      </p>
    </div>
  );
}

function CaseFigures({ request }: { request: RecognitionCase }) {
  const alerts = request.alerts?.length ?? 0;
  return (
    <div className="mb-6 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <Figure label="Horas" value={request.totalHours ?? 0} />
      <Figure label="Sesiones" value={request.sessionsCount ?? 0} />
      <Figure label="Promedio" value={ratingText(request.averageRating)} />
      <Figure label="Alertas abiertas" value={alerts} warn={alerts > 0} />
    </div>
  );
}

/**
 * FE-51 · `/coordinador/reconocimiento/:id` · coordinator.
 *
 * A recognition request as it was submitted: the student, the figures, the sessions that back it
 * with the alerts of the audit next to them, the evidence of the session chosen, and the decision:
 * approving or rejecting with a reason, final, once.
 */
export default function RecognitionCasePage() {
  const { id = '' } = useParams();
  const zone = useZone();
  const recognitionCase = useRecognitionCase(id);
  const [chosen, setChosen] = useState<string>();

  if (recognitionCase.isPending) {
    return (
      <>
        <PageHeader title="Solicitud de reconocimiento" back={BACK} />
        <Skeleton lines={6} label="Cargando la solicitud" />
      </>
    );
  }

  if (recognitionCase.isError || !recognitionCase.data) {
    return (
      <>
        <PageHeader title="Solicitud de reconocimiento" back={BACK} />
        <ErrorState
          error={recognitionCase.error}
          onRetry={() => void recognitionCase.refetch()}
          retrying={recognitionCase.isFetching}
        />
      </>
    );
  }

  const request = recognitionCase.data;
  const sessions = request.sessions ?? [];
  const selected = chosen ?? firstToReview(sessions);
  const name = request.student?.name ?? 'Estudiante';
  const badge = statusBadge(request.status);
  const caption = [
    request.student?.studentCode,
    `enviada el ${longDay(request.submittedAt, zone)}`,
    'así se presentó, no cambia',
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <>
      <PageHeader
        back={BACK}
        title={
          <span className="flex items-center gap-3">
            <Avatar name={name} decorative />
            {name}
          </span>
        }
        description={caption}
        actions={<Badge tone={badge.tone}>{badge.label}</Badge>}
      />
      <CaseFigures request={request} />
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,460px)]">
        <CaseSessions sessions={sessions} selected={selected} onSelect={setChosen} zone={zone} />
        <div className="flex flex-col gap-6">
          <EvidencePanel requestId={id} sessionId={selected} zone={zone} />
          <DecisionPanel request={request} zone={zone} />
        </div>
      </div>
    </>
  );
}
