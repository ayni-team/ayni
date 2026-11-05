import type { ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { isApiError, useApiQuery } from '@/shared/api';
import { Badge, Card, Credits, DateTime, ErrorState, IconInfo, PageHeader, Skeleton } from '@/shared/ui';
import { amountText, movementLink, statusBadge, statusNote } from './purchases';

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1 border-b border-line py-3 last:border-0 sm:flex-row sm:items-center sm:justify-between">
      <dt className="text-sm text-ink-2">{label}</dt>
      <dd className="break-all text-sm font-medium text-ink sm:text-right">{children}</dd>
    </div>
  );
}

/**
 * FE-32 · `/creditos/compras/:id` · student.
 *
 * The receipt of a purchase: what was bought, the provider's reference and, once confirmed, a link
 * to the movement it caused in the wallet.
 */
export default function PurchaseDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const query = useApiQuery(['payments', 'purchase', { id }], '/api/v1/payments/purchases/{purchaseId}', {
    path: { purchaseId: id },
  });
  const purchase = query.data;
  const badge = statusBadge(purchase?.status);
  const note = statusNote(purchase?.status);
  const movement = purchase ? movementLink(purchase) : null;
  const missing = isApiError(query.error) && query.error.status === 404;

  return (
    <>
      <PageHeader title="Comprobante de compra" back={{ to: '/creditos/compras', label: 'Mis compras' }} />
      {query.isPending ? (
        <Card>
          <Skeleton lines={4} label="Cargando el comprobante" />
        </Card>
      ) : query.isError || !purchase ? (
        <ErrorState
          title={missing ? 'No encontramos esa compra' : 'No pudimos cargar el comprobante'}
          error={query.error}
          onRetry={missing ? undefined : () => void query.refetch()}
          retrying={query.isFetching}
        />
      ) : (
        <div className="flex max-w-2xl flex-col gap-4">
          <Card>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-[13px] font-semibold text-ink-2">Total de la compra</p>
                <p className="mt-1 font-display text-[32px] font-semibold tabular-nums leading-tight text-ink">
                  {amountText(purchase.amount, purchase.currency)}
                </p>
              </div>
              <Badge tone={badge.tone}>{badge.label}</Badge>
            </div>
            {note && (
              <p className="mt-3 flex items-start gap-2 rounded-xl bg-surface p-3 text-sm text-ink">
                <IconInfo className="mt-0.5 h-4 w-4 shrink-0 text-ink-2" />
                {note}
              </p>
            )}
          </Card>
          <Card title="Detalle">
            <dl>
              <Row label="Créditos">
                <Credits amount={purchase.credits ?? 0} />
              </Row>
              <Row label="Fecha de compra">
                {purchase.purchasedAt ? <DateTime value={purchase.purchasedAt} /> : '—'}
              </Row>
              {purchase.confirmedAt && (
                <Row label="Fecha de confirmación">
                  <DateTime value={purchase.confirmedAt} />
                </Row>
              )}
              <Row label="Referencia del proveedor">{purchase.providerReference || '—'}</Row>
            </dl>
            {movement && (
              <p className="mt-4 text-sm">
                <Link
                  to={movement}
                  className="font-semibold text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
                >
                  Ver el movimiento en mis créditos
                </Link>
              </p>
            )}
          </Card>
        </div>
      )}
    </>
  );
}
