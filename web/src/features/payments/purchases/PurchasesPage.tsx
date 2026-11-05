import { Link } from 'react-router-dom';
import { useApiQuery } from '@/shared/api';
import {
  Badge,
  ButtonLink,
  Credits,
  DateTime,
  EmptyState,
  ErrorState,
  IconCoins,
  PageHeader,
  Skeleton,
  Table,
  type TableColumn,
} from '@/shared/ui';
import { amountText, newestFirst, statusBadge, type Purchase } from './purchases';

function When({ purchase }: { purchase: Purchase }) {
  return purchase.createdAt ? <DateTime value={purchase.createdAt} /> : <>—</>;
}

function StatusBadge({ purchase }: { purchase: Purchase }) {
  const badge = statusBadge(purchase.status);
  return <Badge tone={badge.tone}>{badge.label}</Badge>;
}

function ReceiptLink({ purchase }: { purchase: Purchase }) {
  if (!purchase.id) return null;
  return (
    <Link
      to={`/creditos/compras/${purchase.id}`}
      className="font-medium text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
    >
      Ver comprobante
    </Link>
  );
}

const COLUMNS: readonly TableColumn<Purchase>[] = [
  { key: 'when', header: 'Fecha', cell: (purchase) => <When purchase={purchase} />, className: 'whitespace-nowrap' },
  { key: 'credits', header: 'Créditos', cell: (purchase) => <Credits amount={purchase.credits ?? 0} /> },
  { key: 'amount', header: 'Monto', cell: (purchase) => amountText(purchase.amount, purchase.currency) },
  { key: 'status', header: 'Estado', cell: (purchase) => <StatusBadge purchase={purchase} /> },
  {
    key: 'receipt',
    header: <span className="sr-only">Comprobante</span>,
    align: 'right',
    cell: (purchase) => <ReceiptLink purchase={purchase} />,
    className: 'whitespace-nowrap',
  },
];

/**
 * FE-32 · `/creditos/compras` · student.
 *
 * The purchases of credits, the newest first. Each leads to its receipt.
 */
export default function PurchasesPage() {
  const query = useApiQuery(['payments', 'purchases'], '/api/v1/payments/purchases');
  const purchases = newestFirst(query.data ?? []);

  return (
    <>
      <PageHeader
        title="Mis compras"
        description="Los créditos que compraste y el comprobante de cada compra."
        back={{ to: '/creditos', label: 'Créditos' }}
        actions={<ButtonLink to="/creditos/comprar">Comprar créditos</ButtonLink>}
      />
      {query.isPending ? (
        <Skeleton lines={3} label="Cargando tus compras" className="[&>div]:h-12 [&>div]:rounded-xl" />
      ) : query.isError ? (
        <ErrorState
          title="No pudimos cargar tus compras"
          error={query.error}
          onRetry={() => void query.refetch()}
          retrying={query.isFetching}
        />
      ) : purchases.length === 0 ? (
        <EmptyState
          icon={<IconCoins className="h-8 w-8" />}
          title="Todavía no tienes compras"
          description="Cuando compres créditos, aparecerán aquí con su comprobante."
        />
      ) : (
        <>
          <Table
            caption="Tus compras de créditos"
            columns={COLUMNS}
            rows={purchases}
            rowKey={(purchase) => purchase.id ?? `${purchase.createdAt}-${purchase.credits}`}
            className="hidden rounded-[20px] md:block"
          />
          <ul className="flex flex-col gap-3 md:hidden">
            {purchases.map((purchase) => (
              <li
                key={purchase.id ?? `${purchase.createdAt}-${purchase.credits}`}
                className="rounded-[20px] border border-line bg-white p-4"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-semibold text-ink">
                      <Credits amount={purchase.credits ?? 0} /> · {amountText(purchase.amount, purchase.currency)}
                    </p>
                    <p className="text-xs text-ink-2">
                      <When purchase={purchase} />
                    </p>
                  </div>
                  <StatusBadge purchase={purchase} />
                </div>
                <p className="mt-2 text-sm">
                  <ReceiptLink purchase={purchase} />
                </p>
              </li>
            ))}
          </ul>
        </>
      )}
    </>
  );
}
