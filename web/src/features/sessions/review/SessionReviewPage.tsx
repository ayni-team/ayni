import { useParams } from 'react-router-dom';
import { isApiError, useApiQuery } from '@/shared/api';
import { useZone } from '@/shared/time';
import { Badge, ButtonLink, Card, ErrorState, PageHeader, Skeleton } from '@/shared/ui';
import { BoardView } from '../whiteboard/BoardView';
import { absenceText, didNotHappen, durationText, isOpen, stateOf, whenLine, type Review } from './review';

const BACK = { to: '/mis-tutorias', label: 'Mis tutorías' };

function Board({ review }: { review: Review }) {
  const board = review.whiteboard;
  // A session that never started has no board, and the state above already says why.
  if (!board) return null;
  const elements = board.elements ?? [];
  return (
    <Card title="La pizarra" description="Tal como quedó al terminar.">
      {elements.length === 0 ? (
        <p className="text-sm text-ink-2">En esta sesión nadie dibujó en la pizarra.</p>
      ) : (
        <BoardView elements={elements} />
      )}
    </Card>
  );
}

function NeedAndMaterial({ review }: { review: Review }) {
  const material = review.supportMaterial;
  if (!review.needDescription && !material) return null;
  return (
    <Card>
      <p className="text-[13px] font-semibold text-ink-2">Lo que se pidió</p>
      {review.needDescription && <p className="mt-3 text-[15px] text-ink">“{review.needDescription}”</p>}
      {material && (
        <div className="mt-4 rounded-xl bg-surface p-4">
          <p className="text-xs font-semibold text-ink-2">Material de apoyo</p>
          {material.summary && <p className="mt-2 text-sm text-ink">{material.summary}</p>}
          {(material.suggestions?.length ?? 0) > 0 && (
            <ul className="mt-2 flex flex-col gap-1">
              {material.suggestions?.map((suggestion) => (
                <li key={suggestion} className="text-[13px] text-ink-2">
                  {suggestion}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Card>
  );
}

/**
 * FE-24 · `/sesion/:id/repaso` · student, tutor.
 *
 * What is left of a past session: what happened, how long it lasted, what was asked for and the
 * whiteboard as it was left. One that did not take place says why instead of an empty workspace.
 */
export default function SessionReviewPage() {
  const { id = '' } = useParams<{ id: string }>();
  const zone = useZone();
  const query = useApiQuery(['sessions', 'review', { id }], '/api/v1/sessions/{id}/review', { path: { id } });
  const review = query.data;

  if (query.isPending) {
    return (
      <>
        <PageHeader title="Repaso de la sesión" back={BACK} />
        <Card>
          <Skeleton lines={4} label="Cargando el repaso" />
        </Card>
      </>
    );
  }

  if (query.isError || !review) {
    const missing = isApiError(query.error) && query.error.status === 404;
    const forbidden = isApiError(query.error) && query.error.status === 403;
    return (
      <>
        <PageHeader title="Repaso de la sesión" back={BACK} />
        <ErrorState
          title={
            missing
              ? 'No encontramos esta sesión'
              : forbidden
                ? 'Esta sesión no es tuya'
                : 'No pudimos cargar el repaso'
          }
          error={query.error}
          onRetry={missing || forbidden ? undefined : () => void query.refetch()}
          retrying={query.isFetching}
        />
      </>
    );
  }

  const state = stateOf(review.status);
  const duration = durationText(review);
  const absence = absenceText(review);

  return (
    <>
      <PageHeader
        title={review.catalogItem?.name || 'Repaso de la sesión'}
        description={whenLine(review, zone)}
        back={BACK}
      />
      <div className="flex max-w-4xl flex-col gap-4">
        <Card
          title={
            <span className="flex items-center gap-2">
              Qué pasó <Badge tone={state.tone}>{state.label}</Badge>
            </span>
          }
        >
          <p className="text-sm text-ink">{state.note}</p>
          {absence && <p className="mt-2 text-sm text-ink">{absence}</p>}
          {duration && <p className="mt-2 text-sm text-ink-2">{duration}</p>}
          {didNotHappen(review) && (
            <p className="mt-2 text-sm text-ink-2">No hubo sala, así que tampoco hay pizarra.</p>
          )}
          {isOpen(review) && (
            <ButtonLink to={`/sesion/${id}`} className="mt-4">
              Ir a la sala
            </ButtonLink>
          )}
        </Card>
        <NeedAndMaterial review={review} />
        <Board review={review} />
      </div>
    </>
  );
}
