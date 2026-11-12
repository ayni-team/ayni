import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { errorText, isApiError, useApiMutation } from '@/shared/api';
import { useZone } from '@/shared/time';
import {
  Badge,
  Button,
  ButtonLink,
  Card,
  Field,
  IconAlert,
  IconCheck,
  IconClock,
  PageHeader,
  TextField,
  cx,
} from '@/shared/ui';
import { amountText } from '../purchases/purchases';
import {
  MONTHLY_LIMIT_TEXT,
  creditsError,
  outcomeOf,
  receiptLine,
  statusBadge,
  type Outcome,
  type PurchaseAnswer,
} from './buy';

const BACK = { to: '/creditos', label: 'Volver a Créditos' };

const STEPS = [
  'Eliges cuántos créditos; el precio lo calcula el servidor.',
  'Si el pago queda en proceso, espera: no vuelvas a comprar.',
  'Los créditos comprados no vencen, pero no cuentan para canjes ni donaciones.',
];

const TONES: Record<Outcome['tone'], { box: string; icon: string }> = {
  info: { box: 'bg-info-soft', icon: 'text-info-text' },
  warning: { box: 'bg-credit-soft', icon: 'text-credit-text' },
  danger: { box: 'bg-red-50', icon: 'text-red-700' },
};

function Notice({ outcome }: { outcome: Pick<Outcome, 'tone' | 'title' | 'detail'> }) {
  const tone = TONES[outcome.tone];
  const Icon = outcome.tone === 'info' ? IconCheck : outcome.tone === 'warning' ? IconClock : IconAlert;
  return (
    <div role="status" className={cx('flex items-start gap-3 rounded-xl p-4', tone.box)}>
      <Icon className={cx('mt-0.5 h-5 w-5 shrink-0', tone.icon)} />
      <div>
        <p className="text-sm font-semibold text-ink">{outcome.title}</p>
        {outcome.detail && <p className="mt-0.5 text-[13px] text-ink-2">{outcome.detail}</p>}
      </div>
    </div>
  );
}

function Result({ purchase, onRetry }: { purchase: PurchaseAnswer; onRetry: () => void }) {
  const zone = useZone();
  const outcome = outcomeOf(purchase);
  const badge = statusBadge(purchase.status);
  const count = purchase.credits ?? 0;
  return (
    <Card>
      <div className="flex flex-col gap-4">
        <Notice outcome={outcome} />
        <div className="rounded-xl bg-surface p-4">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm font-semibold text-ink">{count === 1 ? '1 crédito' : `${count} créditos`}</p>
            <p className="font-display text-lg font-semibold tabular-nums text-ink">
              {amountText(purchase.amount, purchase.currency)}
            </p>
          </div>
          <div className="mt-1 flex flex-wrap items-center justify-between gap-2">
            <p className="break-all text-xs text-ink-2">{receiptLine(purchase, zone)}</p>
            <Badge tone={badge.tone}>{badge.label}</Badge>
          </div>
        </div>
        <div className="flex flex-wrap justify-end gap-2">
          {purchase.status === 'CONFIRMED' ? (
            <>
              {purchase.id && (
                <ButtonLink to={`/creditos/compras/${purchase.id}`} variant="ghost">
                  Ver la compra
                </ButtonLink>
              )}
              <ButtonLink to="/creditos">Ver mis créditos</ButtonLink>
            </>
          ) : outcome.canRetry ? (
            <>
              <ButtonLink to="/creditos" variant="ghost">
                Volver a Créditos
              </ButtonLink>
              <Button onClick={onRetry}>Intentar de nuevo</Button>
            </>
          ) : (
            <ButtonLink to="/creditos/compras">Ver mis compras</ButtonLink>
          )}
        </div>
      </div>
    </Card>
  );
}

interface FormValues {
  credits: string;
}

/**
 * FE-31 · `/creditos/comprar` · student.
 *
 * An amount and a confirm button. The price lives on the server, so it shows only after the answer.
 * Each attempt carries an `Idempotency-Key`: the hook keeps it while the platform fails and replaces
 * it once the server answered, so a double click or a retry never charges twice.
 */
export default function BuyCreditsPage() {
  const [purchase, setPurchase] = useState<PurchaseAnswer | null>(null);
  const [refusal, setRefusal] = useState<string>();
  const buy = useApiMutation('post', '/api/v1/payments/purchases', {
    idempotent: true,
    invalidates: [['wallet'], ['payments']],
  });
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ defaultValues: { credits: '1' }, mode: 'onSubmit' });

  async function submit(values: FormValues) {
    setRefusal(undefined);
    try {
      setPurchase(await buy.mutateAsync({ body: { credits: Number(values.credits.trim()) } }));
    } catch (failure) {
      setRefusal(isApiError(failure, 409) ? MONTHLY_LIMIT_TEXT : errorText(failure));
    }
  }

  const unavailable = purchase?.providerUnavailable === true;
  const showResult = purchase !== null && !unavailable;
  const fieldError = errors.credits?.message ?? refusal;

  return (
    <>
      <PageHeader title="Comprar créditos" back={BACK} />
      <div className="flex max-w-2xl flex-col gap-4">
        {showResult ? (
          <Result purchase={purchase} onRetry={() => setPurchase(null)} />
        ) : (
          <Card>
            <form onSubmit={(event) => void handleSubmit(submit)(event)} className="flex flex-col gap-4" noValidate>
              {unavailable && (
                <Notice
                  outcome={{
                    tone: 'danger',
                    title: 'Las compras están temporalmente no disponibles',
                    detail: 'El resto de Ayni sigue funcionando: puedes reservar con los créditos que ya tienes.',
                  }}
                />
              )}
              <Field
                label="¿Cuántos créditos quieres?"
                error={unavailable ? undefined : fieldError}
                hint={
                  unavailable
                    ? 'Vuelve a intentarlo más tarde.'
                    : fieldError
                      ? undefined
                      : '1 crédito = 1 hora. El precio te lo mostramos al procesar la compra.'
                }
              >
                <TextField
                  inputMode="numeric"
                  autoComplete="off"
                  disabled={unavailable || buy.isPending}
                  {...register('credits', { validate: (value) => creditsError(value) })}
                />
              </Field>
              <div className="flex justify-end">
                <Button type="submit" loading={buy.isPending} disabled={unavailable}>
                  {buy.isPending ? 'Procesando…' : 'Comprar'}
                </Button>
              </div>
            </form>
          </Card>
        )}
        {purchase === null && (
          <Card title="Cómo funciona">
            <ul className="flex flex-col gap-3">
              {STEPS.map((step) => (
                <li key={step} className="flex items-start gap-2.5 text-[13px] text-ink-2">
                  <IconCheck className="mt-0.5 h-4 w-4 shrink-0 text-info-text" />
                  {step}
                </li>
              ))}
            </ul>
          </Card>
        )}
      </div>
    </>
  );
}
