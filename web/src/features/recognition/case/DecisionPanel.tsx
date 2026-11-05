import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { errorText, isApiError } from '@/shared/api';
import { Badge, Button, Card, Dialog, Field, IconAlert, TextArea, useToast } from '@/shared/ui';
import { statusBadge } from '../requests/queue';
import {
  DECISIONS,
  REASON_MAX,
  decidedAt,
  isDecided,
  reasonError,
  type Decision,
  type RecognitionCase,
} from './recognitionCase';
import { useDecide } from './useRecognitionCase';

/** The decision that stands, and that it is final. */
function DecidedCard({ request, zone, conflict }: { request: RecognitionCase; zone: string; conflict: boolean }) {
  const badge = statusBadge(request.status);
  const when = decidedAt(request.reviewedAt, zone);
  return (
    <Card>
      {conflict && (
        <div role="status" className="mb-4 flex gap-2 rounded-xl bg-amber-50 px-3 py-2.5 text-amber-900">
          <IconAlert className="mt-0.5 h-4 w-4 shrink-0" />
          <div>
            <p className="text-sm font-semibold">Otra persona ya decidió esta solicitud</p>
            <p className="text-xs">Te mostramos la decisión que quedó.</p>
          </div>
        </div>
      )}
      <h2 className="font-display text-lg font-semibold text-ink">Decisión</h2>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <Badge tone={badge.tone}>{badge.label}</Badge>
        {when && <span className="text-xs text-ink-2">{when}</span>}
      </div>
      {request.decisionReason && <p className="mt-3 text-sm text-ink">“{request.decisionReason}”</p>}
      <p className="mt-3 text-xs text-ink-2">Es definitiva: no se puede volver a decidir.</p>
    </Card>
  );
}

/**
 * Approving or rejecting, in two steps: the reason, then a confirmation, because the decision is
 * final and the student reads the reason. When someone else decided first (409), the decision that
 * stands is shown instead.
 */
export function DecisionPanel({ request, zone }: { request: RecognitionCase; zone: string }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const decide = useDecide();
  const [reason, setReason] = useState('');
  const [error, setError] = useState<string>();
  const [confirming, setConfirming] = useState<Decision>();
  const [conflict, setConflict] = useState(false);
  const student = request.student?.name ?? 'el estudiante';

  if (isDecided(request.status)) {
    return <DecidedCard request={request} zone={zone} conflict={conflict} />;
  }

  function ask(decision: Decision) {
    const problem = reasonError(reason);
    setError(problem);
    if (!problem) setConfirming(decision);
  }

  async function confirm() {
    if (!confirming || !request.id) return;
    try {
      await decide.mutateAsync({ path: { id: request.id }, body: { decision: confirming, reason: reason.trim() } });
      toast.success(DECISIONS[confirming].done);
      setConfirming(undefined);
    } catch (failure) {
      setConfirming(undefined);
      if (isApiError(failure, 409)) {
        setConflict(true);
        await queryClient.invalidateQueries({ queryKey: ['recognition'] });
      } else if (isApiError(failure, 400)) {
        setError('Revisa el motivo e inténtalo de nuevo');
      } else {
        toast.error(errorText(failure));
      }
    }
  }

  const pending = confirming ? DECISIONS[confirming] : undefined;

  return (
    <Card title="Decidir" description="La decisión es definitiva y el estudiante verá el motivo.">
      <Field label={`Motivo (obligatorio, hasta ${REASON_MAX} caracteres)`} error={error} required>
        <TextArea
          value={reason}
          maxLength={REASON_MAX}
          showCount
          onChange={(event) => {
            setReason(event.target.value);
            if (error) setError(undefined);
          }}
        />
      </Field>
      <div className="mt-4 flex flex-wrap justify-end gap-2">
        <Button variant="secondary" onClick={() => ask('REJECT')} disabled={decide.isPending}>
          Rechazar
        </Button>
        <Button onClick={() => ask('APPROVE')} disabled={decide.isPending}>
          Aprobar
        </Button>
      </div>

      <Dialog
        open={Boolean(confirming)}
        onClose={() => setConfirming(undefined)}
        dismissible={!decide.isPending}
        title={pending ? `¿${pending.verb} la solicitud de ${student}?` : ''}
        description="Es definitiva: no se podrá volver a decidir, y el estudiante verá este motivo."
        footer={
          <>
            <Button
              variant={confirming === 'REJECT' ? 'danger' : 'primary'}
              loading={decide.isPending}
              onClick={() => void confirm()}
            >
              {decide.isPending ? pending?.doing : pending?.verb}
            </Button>
            <Button variant="ghost" onClick={() => setConfirming(undefined)} disabled={decide.isPending}>
              Cancelar
            </Button>
          </>
        }
      >
        <p className="rounded-xl bg-gray-50 px-3 py-2.5 text-sm text-ink">“{reason.trim()}”</p>
      </Dialog>
    </Card>
  );
}
