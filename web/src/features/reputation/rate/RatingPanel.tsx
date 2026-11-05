import { useState, type FormEvent } from 'react';
import { errorText, isApiError, useApiMutation, useApiQuery } from '@/shared/api';
import {
  Button,
  ButtonLink,
  Card,
  Checkbox,
  ErrorState,
  Field,
  IconInfo,
  IconStar,
  Skeleton,
  TextArea,
  cx,
  useToast,
} from '@/shared/ui';
import { RATING_TAGS } from '../ratingTags';
import {
  COMMENT_LIMIT,
  FACTS,
  STARS,
  commentOf,
  factsSummary,
  starsLabel,
  studentSummary,
  toggled,
  type RegisteredRating,
  type TutorFacts,
} from './rating';

/** The session closes first; the window to rate opens a moment after, when the event arrives. */
const WAIT_FOR_WINDOW_MS = 3000;

export interface RatingPanelProps {
  sessionId: string;
  role: 'STUDENT' | 'TUTOR';
  /** The other person, as the session names them. */
  otherName: string;
}

function Star({ on, className }: { on: boolean; className?: string }) {
  return <IconStar className={cx('h-5 w-5', on ? 'fill-credit text-credit-text' : 'text-ink-3', className)} />;
}

function StarBox({ on, children }: { on: boolean; children?: React.ReactNode }) {
  return (
    <span
      className={cx(
        'flex h-12 w-12 items-center justify-center rounded-xl border',
        on ? 'border-credit bg-credit-soft' : 'border-line bg-white',
      )}
    >
      {children}
    </span>
  );
}

function Registered({
  rating,
  role,
  sessionId,
  already,
}: {
  rating: RegisteredRating;
  role: RatingPanelProps['role'];
  sessionId: string;
  already: boolean;
}) {
  return (
    <Card>
      {already && (
        <p className="mb-4 flex items-center gap-2.5 rounded-xl bg-info-soft p-3 text-[13px] font-semibold text-ink">
          <IconInfo className="h-5 w-5 shrink-0 text-info-text" />
          Ya calificaste esta sesión
        </p>
      )}
      <h2 className="font-display text-lg font-semibold text-ink">Gracias por calificar</h2>
      {role === 'STUDENT' ? (
        <>
          <div role="img" aria-label={starsLabel(rating.stars ?? 0)} className="mt-3 flex gap-2">
            {STARS.map((star) => (
              <StarBox key={star} on={star <= (rating.stars ?? 0)}>
                <Star on={star <= (rating.stars ?? 0)} />
              </StarBox>
            ))}
          </div>
          <p className="mt-3 text-[13px] text-ink-2">{studentSummary(rating)}</p>
        </>
      ) : (
        <ul className="mt-3 flex flex-col gap-1 text-sm text-ink">
          {factsSummary(rating).map((fact) => (
            <li key={fact}>{fact}</li>
          ))}
        </ul>
      )}
      {rating.comment && <p className="mt-2 whitespace-pre-line text-[13px] text-ink-2">“{rating.comment}”</p>}
      <ButtonLink to={`/sesion/${sessionId}/repaso`} variant="secondary" className="mt-4">
        Repasar la sesión
      </ButtonLink>
    </Card>
  );
}

function CommentField({
  value,
  onChange,
  placeholder,
}: {
  value: string;
  onChange: (text: string) => void;
  placeholder?: string;
}) {
  return (
    <Field label="Comentario (opcional)">
      <TextArea
        rows={3}
        maxLength={COMMENT_LIMIT}
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
      />
    </Field>
  );
}

/**
 * FE-23 · rating a session. One endpoint, two directions: the student gives stars and tags, the
 * tutor answers three facts. The answer of the server is shown as it was registered, and a second
 * attempt shows what is already there.
 */
export function RatingPanel({ sessionId, role, otherName }: RatingPanelProps) {
  const toast = useToast();
  const [stars, setStars] = useState(0);
  const [tags, setTags] = useState<string[]>([]);
  const [facts, setFacts] = useState<TutorFacts>({ wasPunctual: false, connectionOk: false, sessionFlowed: false });
  const [comment, setComment] = useState('');
  const [already, setAlready] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  const registered = useApiQuery(['reputation', 'rating', { sessionId }], '/api/v1/sessions/{id}/rating', {
    path: { id: sessionId },
    retry: false,
    refetchInterval: (query) =>
      isApiError(query.state.error) && query.state.error.status === 404 ? WAIT_FOR_WINDOW_MS : false,
  });
  const rate = useApiMutation('post', '/api/v1/sessions/{id}/rating', {
    invalidates: [['reputation', 'rating', { sessionId }]],
  });

  const windowClosed = isApiError(registered.error) && registered.error.status === 404;

  if (registered.isPending || windowClosed) {
    return (
      <Card>
        <Skeleton lines={3} label="Preparando la calificación" />
      </Card>
    );
  }
  if (registered.isError) {
    return (
      <ErrorState
        title="No pudimos cargar tu calificación"
        error={registered.error}
        onRetry={() => void registered.refetch()}
        retrying={registered.isFetching}
      />
    );
  }
  if (registered.data?.rated) {
    return <Registered rating={registered.data} role={role} sessionId={sessionId} already={already} />;
  }
  if (dismissed) {
    return (
      <Card>
        <p className="text-sm text-ink-2">Puedes volver a esta página para calificar la sesión.</p>
        <Button variant="secondary" className="mt-3" onClick={() => setDismissed(false)}>
          Calificar ahora
        </Button>
      </Card>
    );
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (role === 'STUDENT' && stars === 0) return;
    const body =
      role === 'STUDENT' ? { stars, tags, comment: commentOf(comment) } : { ...facts, comment: commentOf(comment) };
    try {
      await rate.mutateAsync({ path: { id: sessionId }, body });
    } catch (failure) {
      if (isApiError(failure, 409)) {
        setAlready(true);
        void registered.refetch();
      } else {
        toast.error(errorText(failure));
      }
    }
  }

  return (
    <Card>
      <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
        {role === 'STUDENT' ? (
          <>
            <h2 className="font-display text-xl font-semibold text-ink">¿Cómo te fue con {otherName}?</h2>
            <fieldset>
              <legend className="sr-only">Estrellas</legend>
              <div className="flex gap-2">
                {STARS.map((star) => (
                  <label
                    key={star}
                    className="cursor-pointer rounded-xl focus-within:outline focus-within:outline-2 focus-within:outline-brand"
                  >
                    <input
                      type="radio"
                      name="stars"
                      value={star}
                      checked={stars === star}
                      onChange={() => setStars(star)}
                      aria-label={starsLabel(star)}
                      className="sr-only"
                    />
                    <StarBox on={star <= stars}>
                      <Star on={star <= stars} />
                    </StarBox>
                  </label>
                ))}
              </div>
              <p className="mt-2 text-[13px] text-ink-2">{stars > 0 ? `${stars} de 5` : 'Elige de 1 a 5 estrellas'}</p>
            </fieldset>
            <fieldset>
              <legend className="text-[13px] font-semibold text-ink">¿Qué destacas? (opcional)</legend>
              <div className="mt-2 flex flex-wrap gap-2">
                {RATING_TAGS.map((tag) => {
                  const selected = tags.includes(tag.value);
                  return (
                    <button
                      key={tag.value}
                      type="button"
                      aria-pressed={selected}
                      onClick={() => setTags(toggled(tags, tag.value))}
                      className={cx(
                        'rounded-full px-3 py-1 text-[13px] font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand',
                        selected ? 'bg-brand-soft text-brand-text' : 'bg-surface text-ink-2',
                      )}
                    >
                      {tag.label}
                    </button>
                  );
                })}
              </div>
            </fieldset>
            <div>
              <CommentField value={comment} onChange={setComment} />
              <p className="mt-1.5 text-xs text-ink-2">Tu tutor no ve quién lo calificó.</p>
            </div>
          </>
        ) : (
          <>
            <div>
              <h2 className="font-display text-xl font-semibold text-ink">¿Cómo fue la sesión con {otherName}?</h2>
              <p className="mt-1 text-[13px] text-ink-2">Tres hechos, no una nota. Los tres son obligatorios.</p>
            </div>
            <div className="flex flex-col gap-3">
              {FACTS.map((fact) => (
                <Checkbox
                  key={fact.key}
                  label={fact.label}
                  checked={facts[fact.key]}
                  onChange={(event) => setFacts({ ...facts, [fact.key]: event.target.checked })}
                />
              ))}
            </div>
            <CommentField
              value={comment}
              onChange={setComment}
              placeholder="Escribe algo para ti o para la universidad"
            />
          </>
        )}
        <div className="flex flex-wrap justify-end gap-2">
          {role === 'STUDENT' && (
            <Button variant="ghost" onClick={() => setDismissed(true)}>
              Ahora no
            </Button>
          )}
          <Button type="submit" loading={rate.isPending} disabled={role === 'STUDENT' && stars === 0}>
            {role === 'STUDENT' ? 'Enviar calificación' : 'Enviar'}
          </Button>
        </div>
      </form>
    </Card>
  );
}
