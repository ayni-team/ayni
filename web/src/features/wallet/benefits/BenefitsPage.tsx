import { useState } from 'react';
import { errorBody, errorText, isApiError, useApiMutation, useApiQuery, useIdempotencyKey } from '@/shared/api';
import {
  Button,
  Card,
  CreditCoin,
  Credits,
  DateTime,
  Dialog,
  EmptyState,
  ErrorState,
  Field,
  IconAward,
  PageHeader,
  Skeleton,
  TextField,
  useToast,
} from '@/shared/ui';
import {
  confirmationText,
  donationError,
  doneText,
  earnedAvailable,
  poolText,
  refusalText,
  type Benefit,
  type CreditUse,
  type Refusal,
} from './benefits';

interface Pending {
  use: CreditUse;
  confirmationId: string;
}

/**
 * FE-33 · `/creditos/beneficios` · student.
 *
 * The benefits of the university and a donation to the incoming students. Both are irreversible and
 * two-step: the first call asks for a confirmation, shown in a dialog, and the second one sends it
 * with the same `Idempotency-Key`, so a double click or a retry spends once.
 */
export default function BenefitsPage() {
  const toast = useToast();
  const wallet = useApiQuery(['wallet', 'balance'], '/api/v1/wallet');
  const benefits = useApiQuery(['wallet', 'benefits'], '/api/v1/wallet/benefits');
  const pool = useApiQuery(['wallet', 'donation-pool'], '/api/v1/wallet/donation-pool');
  const donations = useApiQuery(['wallet', 'donations'], '/api/v1/wallet/donations');
  const redeem = useApiMutation('post', '/api/v1/wallet/benefits/{benefitId}/redemptions', {
    idempotent: true,
    invalidates: [['wallet']],
  });
  const donate = useApiMutation('post', '/api/v1/wallet/donations', { idempotent: true, invalidates: [['wallet']] });
  const key = useIdempotencyKey();
  const [pending, setPending] = useState<Pending | null>(null);
  const [askingFor, setAskingFor] = useState<string>();
  const [amount, setAmount] = useState('1');
  const [amountProblem, setAmountProblem] = useState<string>();
  const [dialogProblem, setDialogProblem] = useState<string>();

  const earned = earnedAvailable(wallet.data);
  const sending = redeem.isPending || donate.isPending;

  async function send(use: CreditUse, confirmed: boolean, confirmationId?: string) {
    const idempotencyKey = key.current();
    return use.kind === 'REDEMPTION'
      ? redeem.mutateAsync({
          path: { benefitId: use.benefitId },
          body: { confirmed, confirmationId },
          idempotencyKey,
        })
      : donate.mutateAsync({ body: { credits: use.credits, confirmed, confirmationId }, idempotencyKey });
  }

  /** The first step: nothing is spent. It answers the confirmation to send back. */
  async function ask(use: CreditUse, id: string) {
    if (sending) return;
    setAskingFor(id);
    setAmountProblem(undefined);
    key.renew();
    try {
      const answer = await send(use, false);
      setDialogProblem(undefined);
      setPending({ use, confirmationId: answer.confirmationId ?? '' });
    } catch (failure) {
      toast.error(errorText(failure));
    } finally {
      setAskingFor(undefined);
    }
  }

  function close() {
    if (sending) return;
    setPending(null);
    key.renew();
  }

  /** The second step: the same key and the confirmation. It keeps the key if the platform failed. */
  async function confirm() {
    if (!pending) return;
    setDialogProblem(undefined);
    try {
      await send(pending.use, true, pending.confirmationId);
      toast.success(doneText(pending.use));
      key.renew();
      setPending(null);
      setAmount('1');
    } catch (failure) {
      if (isApiError(failure, 409)) {
        toast.error(refusalText(errorBody<Refusal>(failure)));
        key.renew();
        setPending(null);
      } else {
        setDialogProblem(errorText(failure));
      }
    }
  }

  function askDonation() {
    const wrong = donationError(amount);
    setAmountProblem(wrong);
    if (!wrong) void ask({ kind: 'DONATION', credits: Number(amount.trim()) }, 'donation');
  }

  function askRedemption(benefit: Benefit) {
    void ask(
      {
        kind: 'REDEMPTION',
        benefitId: benefit.id ?? '',
        name: benefit.name ?? 'este beneficio',
        credits: benefit.creditsCost ?? 0,
      },
      benefit.id ?? '',
    );
  }

  const list = benefits.data ?? [];
  const history = donations.data ?? [];

  return (
    <>
      <PageHeader
        title="Beneficios y donaciones"
        description="Solo se usan los créditos que ganaste enseñando. Los comprados o asignados no cuentan."
        back={{ to: '/creditos', label: 'Créditos' }}
      />
      <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_420px]">
        <div className="flex min-w-0 flex-col gap-4">
          <Card>
            <p className="text-[13px] font-semibold text-ink-2">Créditos ganados disponibles</p>
            {wallet.isPending ? (
              <Skeleton lines={1} label="Cargando tu saldo" className="mt-2 max-w-24" />
            ) : wallet.isError ? (
              <p className="mt-1 text-sm text-ink-2">No pudimos cargar tu saldo.</p>
            ) : (
              <p className="mt-1 flex items-center gap-2 font-display text-[32px] font-semibold tabular-nums text-ink">
                <CreditCoin className="h-6 w-6" />
                {earned}
              </p>
            )}
          </Card>
          {benefits.isPending ? (
            <Card>
              <Skeleton lines={3} label="Cargando los beneficios" />
            </Card>
          ) : benefits.isError ? (
            <ErrorState
              title="No pudimos cargar los beneficios"
              error={benefits.error}
              onRetry={() => void benefits.refetch()}
              retrying={benefits.isFetching}
            />
          ) : list.length === 0 ? (
            <EmptyState
              icon={<IconAward className="h-8 w-8" />}
              title="Tu universidad todavía no ofrece beneficios"
              description="Cuando los ofrezca, podrás canjearlos aquí. Mientras tanto, puedes donar tus créditos."
            />
          ) : (
            <Card title="Beneficios de tu universidad">
              <ul className="divide-y divide-line">
                {list.map((benefit) => (
                  <li
                    key={benefit.id}
                    className="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0 last:pb-0"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="font-semibold text-ink">{benefit.name ?? 'Beneficio'}</p>
                      {benefit.description && <p className="mt-0.5 text-[13px] text-ink-2">{benefit.description}</p>}
                      <Credits
                        amount={benefit.creditsCost ?? 0}
                        className="mt-1 block text-sm font-semibold text-credit-text"
                      />
                    </div>
                    <Button
                      variant="secondary"
                      aria-label={`Canjear ${benefit.name ?? 'este beneficio'}`}
                      loading={askingFor === benefit.id}
                      disabled={sending}
                      onClick={() => askRedemption(benefit)}
                    >
                      Canjear
                    </Button>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>
        <div className="flex min-w-0 flex-col gap-4">
          <Card title="Dona a los estudiantes nuevos" description="Tus créditos ganados ayudan a quien recién empieza.">
            <div className="flex flex-col gap-4">
              {!pool.isError && (
                <p className="rounded-xl bg-surface p-3 text-sm text-ink">
                  <span className="font-semibold">El fondo tiene </span>
                  {pool.isPending ? '…' : poolText(pool.data)}
                </p>
              )}
              <Field label="¿Cuántos créditos quieres donar?" error={amountProblem}>
                <TextField inputMode="numeric" value={amount} onChange={(event) => setAmount(event.target.value)} />
              </Field>
              <div className="flex justify-end">
                <Button loading={askingFor === 'donation'} disabled={sending} onClick={askDonation}>
                  Donar
                </Button>
              </div>
              {history.length > 0 && (
                <div className="border-t border-line pt-3">
                  <h3 className="text-[13px] font-semibold text-ink">Tus donaciones</h3>
                  <ul className="mt-2 flex flex-col gap-1.5">
                    {history.map((donation) => (
                      <li key={donation.id} className="flex items-center justify-between gap-3 text-sm text-ink">
                        {donation.createdAt ? <DateTime value={donation.createdAt} format="date" /> : <span>—</span>}
                        <Credits amount={donation.credits ?? 0} className="font-semibold" />
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </Card>
        </div>
      </div>

      <Dialog
        open={pending !== null}
        onClose={close}
        dismissible={!sending}
        title="¿Confirmas?"
        description={pending ? confirmationText(pending.use) : undefined}
        footer={
          <>
            <Button loading={sending} onClick={() => void confirm()}>
              Confirmar
            </Button>
            <Button variant="secondary" disabled={sending} onClick={close}>
              Cancelar
            </Button>
          </>
        }
      >
        {dialogProblem && (
          <p role="alert" className="text-sm font-medium text-red-700">
            {dialogProblem}
          </p>
        )}
      </Dialog>
    </>
  );
}
