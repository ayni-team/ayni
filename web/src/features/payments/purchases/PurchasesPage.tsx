import { Link } from 'react-router-dom';
import { useApiQuery, type Schema } from '@/shared/api';
import { Badge, Card, DateTime, EmptyState, ErrorState, PageHeader, PageLoading } from '@/shared/ui';

type Purchase = Schema<'PurchaseResponse'>;
type PurchaseStatus = NonNullable<Purchase['status']>;

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

export default function PurchasesPage() {
  const purchasesQuery = useApiQuery(['payments', 'purchases'], '/api/v1/payments/purchases');

  const purchases = purchasesQuery.data ?? [];

  if (purchasesQuery.isLoading) {
    return <PageLoading label="Cargando tus compras" />;
  }

  return (
    <div className="mx-auto w-full max-w-5xl">
      <PageHeader
        title="Mis compras"
        description="Consulta tu historial de compras de créditos y revisa sus comprobantes."
      />

      {purchasesQuery.error ? (
        <ErrorState
          title="No pudimos cargar tus compras"
          description="Ocurrió un problema al consultar tu historial. Inténtalo nuevamente."
          error={purchasesQuery.error}
          onRetry={() => void purchasesQuery.refetch()}
          retrying={purchasesQuery.isFetching}
        />
      ) : purchases.length === 0 ? (
        <EmptyState
          title="Todavía no tienes compras"
          description="Cuando compres créditos, encontrarás aquí el historial de tus operaciones."
        />
      ) : (
        <Card
          title="Historial de compras"
          description={`${purchases.length} ${purchases.length === 1 ? 'compra registrada' : 'compras registradas'}`}
        >
          <div className="divide-y divide-line">
            {purchases.map((purchase, index) => (
              <article
                key={purchase.id ?? `purchase-${index}`}
                className="flex flex-col gap-3 py-4 first:pt-0 last:pb-0 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0 space-y-1">
                  <p className="font-semibold text-ink">
                    {purchase.credits ?? 0} {purchase.credits === 1 ? 'crédito' : 'créditos'}
                  </p>

                  <p className="text-sm text-ink-2">
                    {purchase.createdAt ? (
                      <DateTime value={purchase.createdAt} format="dateTime" />
                    ) : (
                      'Fecha no disponible'
                    )}
                  </p>

                  <PurchaseStatusBadge status={purchase.status} />
                </div>

                <div className="flex items-center justify-between gap-4 sm:justify-end">
                  <p className="font-semibold tabular-nums text-ink">
                    {formatAmount(purchase.amount, purchase.currency)}
                  </p>

                  {purchase.id && (
                    <Link
                      to={`/creditos/compras/${purchase.id}`}
                      className="whitespace-nowrap text-sm font-semibold text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
                    >
                      Ver comprobante
                    </Link>
                  )}
                </div>
              </article>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}
