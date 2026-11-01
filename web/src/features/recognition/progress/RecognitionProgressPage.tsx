import { useState } from 'react';
import { errorText, useApiMutation, useApiQuery } from '@/shared/api';
import { useZone } from '@/shared/time';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  IconAlert,
  IconAward,
  PageHeader,
  Skeleton,
  useToast,
} from '@/shared/ui';
import {
  dayOf,
  figures,
  isOpen,
  missingText,
  percent,
  refusalText,
  requestLine,
  requestStatus,
  sessionLine,
  type MyRequest,
  type MyRequestSession,
} from './progress';

function isPending(request: MyRequest): boolean {
  return request.status === 'SUBMITTED' || request.status === 'UNDER_REVIEW';
}

/** The name of a course, asked to the catalogue once and kept: the request carries only its id. */
function CourseName({ id }: { id: string | undefined }) {
  const course = useApiQuery(['skills', 'catalog', id], '/api/v1/catalog/{id}', {
    path: { id: id ?? '' },
    enabled: Boolean(id),
    staleTime: Infinity,
    retry: false,
  });
  return <>{course.data?.name ?? 'Curso'}</>;
}

function SessionItem({ session, zone }: { session: MyRequestSession; zone: string }) {
  return (
    <li className="flex flex-col gap-0.5 py-2.5 first:pt-0 last:pb-0">
      <span className="text-sm font-semibold text-ink">
        <CourseName id={session.catalogItemId} />
      </span>
      <span className="text-xs text-ink-2">{sessionLine(session, zone)}</span>
    </li>
  );
}

function RequestItem({ request, zone }: { request: MyRequest; zone: string }) {
  const status = requestStatus(request.status);
  const sessions = request.sessions ?? [];
  const decided = request.reviewedAt ? dayOf(request.reviewedAt, zone) : '';
  return (
    <li className="rounded-[16px] border border-line bg-white p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <p className="min-w-0 text-sm text-ink">{requestLine(request, zone)}</p>
        <Badge tone={status.tone}>{status.label}</Badge>
      </div>
      {isPending(request) && (
        <p className="mt-2 text-sm text-ink-2">Tu coordinación la revisará y te avisaremos cuando decida.</p>
      )}
      {decided && <p className="mt-2 text-xs text-ink-2">Decidida el {decided}</p>}
      {request.decisionReason && (
        <div className="mt-3 rounded-xl bg-gray-50 px-3 py-2.5">
          <p className="text-xs font-semibold text-ink-2">Motivo de la decisión</p>
          <p className="mt-0.5 whitespace-pre-line text-sm text-ink">{request.decisionReason}</p>
        </div>
      )}
      {sessions.length > 0 && (
        <details className="mt-3 group">
          <summary className="cursor-pointer text-sm font-semibold text-brand-text">
            {sessions.length === 1
              ? 'Ver la sesión que la respalda'
              : `Ver las ${sessions.length} sesiones que la respaldan`}
          </summary>
          <ul className="mt-3 divide-y divide-line">
            {sessions.map((session, index) => (
              <SessionItem key={session.sessionId ?? index} session={session} zone={zone} />
            ))}
          </ul>
        </details>
      )}
    </li>
  );
}

function MyRequests({ zone }: { zone: string }) {
  const mine = useApiQuery(['recognition', 'requests', 'mine'], '/api/v1/recognition/requests/mine');

  if (mine.isPending) {
    return (
      <Card title="Mis solicitudes">
        <Skeleton lines={3} label="Cargando tus solicitudes" />
      </Card>
    );
  }
  if (mine.isError) {
    return <ErrorState error={mine.error} onRetry={() => void mine.refetch()} retrying={mine.isFetching} />;
  }
  const requests = mine.data ?? [];
  if (requests.length === 0) {
    return (
      <EmptyState
        icon={<IconAward className="h-8 w-8" />}
        title="Todavía no solicitaste reconocimiento"
        description="Cuando llegues a las horas que pide tu universidad, podrás enviar tu solicitud desde aquí."
      />
    );
  }
  return (
    <section aria-labelledby="my-requests">
      <h2 id="my-requests" className="mb-3 font-display text-lg font-semibold text-ink">
        Mis solicitudes
      </h2>
      <ul className="flex flex-col gap-3">
        {requests.map((request, index) => (
          <RequestItem key={request.id ?? index} request={request} zone={zone} />
        ))}
      </ul>
    </section>
  );
}

/**
 * FE-34 · `/reconocimiento` · student.
 *
 * How many hours of verified tutoring the student has taught against the hours the university asks
 * for, a button to request recognition once they are reached, and the requests already sent with
 * their state, the decision and the sessions that back each.
 */
export default function RecognitionProgressPage() {
  const toast = useToast();
  const zone = useZone();
  const [refusal, setRefusal] = useState<string>();
  const progress = useApiQuery(['recognition', 'progress'], '/api/v1/recognition/progress');
  const mine = useApiQuery(['recognition', 'requests', 'mine'], '/api/v1/recognition/requests/mine');
  const submit = useApiMutation('post', '/api/v1/recognition/requests', { invalidates: [['recognition']] });
  const pending = (mine.data ?? []).some(isPending);

  async function request() {
    setRefusal(undefined);
    try {
      await submit.mutateAsync({});
      toast.success('Enviaste tu solicitud de reconocimiento');
    } catch (failure) {
      const text = refusalText(failure);
      if (text) setRefusal(text);
      else toast.error(errorText(failure));
    }
  }

  const data = progress.data;
  const open = isOpen(data);
  const sessions = data?.sessionsCount ?? 0;

  return (
    <>
      <PageHeader
        title="Reconocimiento"
        description="Las horas que enseñas en tutorías verificadas cuentan para que tu universidad reconozca tu trabajo."
      />
      <div className="flex flex-col gap-6">
        {progress.isPending ? (
          <Card title="Tu avance">
            <Skeleton lines={3} label="Cargando tu avance" />
          </Card>
        ) : progress.isError ? (
          <ErrorState error={progress.error} onRetry={() => void progress.refetch()} retrying={progress.isFetching} />
        ) : (
          <Card title="Tu avance">
            <div className="flex flex-col gap-4">
              <div className="flex flex-wrap items-end justify-between gap-2">
                <p className="font-display text-3xl font-semibold text-ink">{figures(data)}</p>
                <p className="text-sm text-ink-2">
                  {sessions === 1 ? 'En 1 sesión verificada' : `En ${sessions} sesiones verificadas`}
                </p>
              </div>
              {open && (
                <div
                  role="progressbar"
                  aria-label="Horas enseñadas"
                  aria-valuemin={0}
                  aria-valuemax={100}
                  aria-valuenow={percent(data)}
                  aria-valuetext={figures(data)}
                  className="h-3 overflow-hidden rounded-full bg-gray-100"
                >
                  <div className="h-full rounded-full bg-brand" style={{ width: `${percent(data)}%` }} />
                </div>
              )}
              {pending ? (
                // The hours of a request in review no longer count here, so "te faltan" would mislead.
                <p className="text-sm font-semibold text-ink">
                  Las horas que enseñaste ya están en tu solicitud en revisión. Las nuevas sesiones contarán para la
                  siguiente.
                </p>
              ) : (
                <p className={data?.requirementMet ? 'text-sm font-semibold text-ink' : 'text-sm text-ink-2'}>
                  {missingText(data)}
                </p>
              )}
              {refusal && (
                <p role="alert" className="flex gap-2 rounded-xl bg-amber-50 px-3 py-2.5 text-sm text-amber-900">
                  <IconAlert className="mt-0.5 h-4 w-4 shrink-0" />
                  {refusal}
                </p>
              )}
              <div className="flex justify-end">
                <Button
                  onClick={() => void request()}
                  disabled={!data?.canRequest || pending}
                  loading={submit.isPending}
                >
                  Solicitar reconocimiento
                </Button>
              </div>
            </div>
          </Card>
        )}
        <MyRequests zone={zone} />
      </div>
    </>
  );
}
