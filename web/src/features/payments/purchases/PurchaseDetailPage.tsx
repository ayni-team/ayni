
import { useParams } from 'react-router-dom';
import { useApiQuery, type Schema } from '@/shared/api';
import { Badge, Card, DateTime, ErrorState, PageHeader, PageLoading } from '@/shared/ui';

type PurchaseDetail = Schema<'PurchaseDetailResponse'>;
type PurchaseStatus = NonNullable<PurchaseDetail['status']>;

const STATUS_LABELS: Record<PurchaseStatus, string> = {
  PENDING: 'Pendiente',
  CONFIRMED: 'Confirmada',
  FAILED: 'Fallida',
  EXPIRED: 'Expirada',
};

const STATUS_TONES = {
  PENDING: 'warning',
  CONFIRMED: 'success',
  FAILED: 'danger',
  EXPIRED: 'neutral',
} as const;

function formatAmount(amount?: number, currency?: string) {
  if (amount === undefined) return '—';

  try {
    return new Intl.NumberFormat('es-PE', {
      style: 'currency',
      currency: currency || 'PEN',
    }).format(amount);
  } catch {
    return `${amount.toFixed(2)} ${currency ?? ''}`.trim();
  }
}

function PurchaseStatusBadge({ status }: { status?: PurchaseStatus }) {
  if (!status) {
    return <Badge tone="neutral">Sin estado</Badge>;
  }

  return <Badge tone={STATUS_TONES[status]}>{STATUS_LABELS[status]}</Badge>;
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1 border-b border-line py-3 last:border-0 sm:flex-row sm:items-center sm:justify-between">
      <dt className="text-sm text-ink-2">{label}</dt>
      <dd className="break-all text-sm font-medium text-ink sm:text-right">{children}</dd>
    </div>
  );
}

export default function PurchaseDetailPage() {
  const { id } = useParams<{ id: string }>();

  const purchaseQuery = useApiQuery(
    ['payments', 'purchase', id],
    '/api/v1/payments/purchases/{purchaseId}',
    {
      path: { purchaseId: id ?? '' },
      enabled: Boolean(id),
    },
  );

  if (!id) {
    return (
      <div className="mx-auto w-full max-w-3xl">
        <PageHeader
          title="Comprobante de compra"
          back={{ to: '/creditos/compras', label: 'Mis compras' }}
        />
        <ErrorState
          title="Compra no encontrada"
          description="No se indicó un identificador de compra válido."
        />
      </div>
    );
  }

  if (purchaseQuery.isLoading) {
    return <PageLoading label="Cargando comprobante" />;
  }

  if (purchaseQuery.error || !purchaseQuery.data) {
    return (
      <div className="mx-auto w-full max-w-3xl">
        <PageHeader
          title="Comprobante de compra"
          back={{ to: '/creditos/compras', label: 'Mis compras' }}
        />
        <ErrorState
          title="No pudimos cargar el comprobante"
          description="Ocurrió un problema al consultar esta compra. Inténtalo nuevamente."
          error={purchaseQuery.error}
          onRetry={() => void purchaseQuery.refetch()}
          retrying={purchaseQuery.isFetching}
        />
      </div>
    );
  }

  const purchase = purchaseQuery.data;

  return (
    <div className="mx-auto w-full max-w-3xl">
      <PageHeader
        title="Comprobante de compra"
        description="Detalle de tu operación de compra de créditos."
        back={{ to: '/creditos/compras', label: 'Mis compras' }}
      />

      <Card className="mb-5">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm text-ink-2">Total de la compra</p>
            <p className="mt-1 text-3xl font-semibold text-ink">
              {formatAmount(purchase.amount, purchase.currency)}
            </p>
          </div>
          <PurchaseStatusBadge status={purchase.status} />
        </div>
      </Card>

      <Card title="Información de la compra">
        <dl>
          <DetailRow label="Créditos adquiridos">
            {purchase.credits ?? '—'}
          </DetailRow>

          <DetailRow label="Importe">
            {formatAmount(purchase.amount, purchase.currency)}
          </DetailRow>

          <DetailRow label="Estado">
            <PurchaseStatusBadge status={purchase.status} />
          </DetailRow>

          <DetailRow label="Fecha de compra">
            {purchase.purchasedAt ? (
              <DateTime value={purchase.purchasedAt} format="dateTime" />
            ) : (
              'No disponible'
            )}
          </DetailRow>

          <DetailRow label="Fecha de confirmación">
            {purchase.confirmedAt ? (
              <DateTime value={purchase.confirmedAt} format="dateTime" />
            ) : (
              'Pendiente de confirmación'
            )}
          </DetailRow>

          <DetailRow label="Referencia del proveedor">
            {purchase.providerReference || 'No disponible'}
          </DetailRow>

          <DetailRow label="Identificador de compra">
            {purchase.id || id}
          </DetailRow>
        </dl>
      </Card>

      {purchase.status === 'CONFIRMED' && (
        <Card
          title="Movimiento de créditos"
          description="Esta compra fue confirmada y generó un movimiento en tu billetera."
          className="mt-5"
        >
          {purchase.creditMovement?.referenceId ? (
            <p className="text-sm text-ink-2">
              Referencia del movimiento:{' '}
              <span className="font-medium text-ink">
                {purchase.creditMovement.referenceId}
              </span>
            </p>
          ) : (
            <p className="text-sm text-ink-2">
              El movimiento fue confirmado, pero su referencia no está disponible.
            </p>
          )}
        </Card>
      )}
    </div>
  );
}
