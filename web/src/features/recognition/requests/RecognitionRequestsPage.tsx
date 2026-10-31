import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useApiQuery } from '@/shared/api';
import { useUniversity } from '@/shared/brand';
import { useZone } from '@/shared/time';
import {
  Avatar,
  Badge,
  EmptyState,
  ErrorState,
  PageHeader,
  Pagination,
  Skeleton,
  Table,
  Tabs,
  pageCount,
  type TableColumn,
} from '@/shared/ui';
import {
  EMPTY,
  QUEUE_TABS,
  figuresLine,
  ratingText,
  sentDay,
  statusBadge,
  statusOf,
  type QueueItem,
  type QueueTab,
} from './queue';

const PAGE_SIZE = 20;

function caseLink(item: QueueItem): string {
  return `/coordinador/reconocimiento/${item.id ?? ''}`;
}

/** The inbox of the empty state, as the frame draws it; `shared/ui` has none. */
function IconInbox({ className = 'h-6 w-6' }: { className?: string }) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      <polyline points="22 12 16 12 14 15 10 15 8 12 2 12" />
      <path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z" />
    </svg>
  );
}

function Student({ item }: { item: QueueItem }) {
  const name = item.student?.name ?? 'Estudiante';
  return (
    <div className="flex min-w-0 items-center gap-3">
      <Avatar name={name} size="sm" decorative />
      <div className="min-w-0">
        <p className="truncate font-semibold text-ink">{name}</p>
        {item.student?.studentCode && <p className="text-xs text-ink-2">{item.student.studentCode}</p>}
      </div>
    </div>
  );
}

function StatusBadge({ item }: { item: QueueItem }) {
  const badge = statusBadge(item.status);
  return <Badge tone={badge.tone}>{badge.label}</Badge>;
}

function ReviewLink({ item }: { item: QueueItem }) {
  return (
    <Link
      to={caseLink(item)}
      aria-label={`Revisar la solicitud de ${item.student?.name ?? 'este estudiante'}`}
      className="font-semibold text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
    >
      Revisar
    </Link>
  );
}

/** One request on a phone: the whole card leads to the case. */
function RequestCard({ item, zone }: { item: QueueItem; zone: string }) {
  const name = item.student?.name ?? 'Estudiante';
  return (
    <li>
      <Link
        to={caseLink(item)}
        aria-label={`Revisar la solicitud de ${name}`}
        className="block rounded-[20px] border border-line bg-white p-4 hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
      >
        <div className="flex items-start gap-3">
          <Avatar name={name} decorative />
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold text-ink">{name}</p>
            <p className="text-sm text-ink-2">
              {[item.student?.studentCode, `enviada ${sentDay(item.submittedAt, zone)}`].filter(Boolean).join(' · ')}
            </p>
          </div>
          <StatusBadge item={item} />
        </div>
        <p className="mt-2 text-sm text-ink">{figuresLine(item)}</p>
      </Link>
    </li>
  );
}

/**
 * FE-50 · `/coordinador/reconocimiento` · coordinator.
 *
 * The recognition requests of the university: what waits, the oldest first, and the history of
 * each decision. Every row leads to its case (FE-51).
 */
export default function RecognitionRequestsPage() {
  const zone = useZone();
  const university = useUniversity();
  const [tab, setTab] = useState<QueueTab>('PENDING');
  const [page, setPage] = useState(0);
  const status = statusOf(tab);
  const queue = useApiQuery(
    ['recognition', 'queue', { tab, page, size: PAGE_SIZE }],
    '/api/v1/coordinator/recognition/requests',
    { query: { status, page, size: PAGE_SIZE } },
  );

  const items = queue.data?.items ?? [];
  const totalPages = pageCount(queue.data?.total ?? 0, queue.data?.size ?? PAGE_SIZE);
  const who = university?.code ?? 'tu universidad';

  const columns: readonly TableColumn<QueueItem>[] = [
    { key: 'student', header: 'Estudiante', cell: (item) => <Student item={item} /> },
    { key: 'hours', header: 'Horas', cell: (item) => item.totalHours ?? '—' },
    { key: 'sessions', header: 'Sesiones', cell: (item) => item.sessionsCount ?? '—' },
    { key: 'rating', header: 'Promedio', cell: (item) => ratingText(item.averageRating) },
    { key: 'sent', header: 'Enviada', cell: (item) => sentDay(item.submittedAt, zone) },
    { key: 'status', header: 'Estado', cell: (item) => <StatusBadge item={item} /> },
    { key: 'review', header: <span className="sr-only">Acción</span>, cell: (item) => <ReviewLink item={item} /> },
  ];

  function choose(next: QueueTab) {
    setTab(next);
    setPage(0);
  }

  return (
    <>
      <PageHeader
        title="Solicitudes de reconocimiento"
        description={`Estudiantes que piden que la ${who} reconozca sus horas enseñadas. La más antigua primero.`}
      />
      <Tabs label="Qué solicitudes ver" items={QUEUE_TABS} value={tab} onChange={choose}>
        {queue.isPending ? (
          <Skeleton lines={3} label="Cargando las solicitudes" className="[&>div]:h-12 [&>div]:rounded-xl" />
        ) : queue.isError ? (
          <ErrorState error={queue.error} onRetry={() => void queue.refetch()} retrying={queue.isFetching} />
        ) : items.length === 0 ? (
          <EmptyState
            icon={<IconInbox className="h-8 w-8" />}
            title={EMPTY[tab].title}
            description={EMPTY[tab].description}
          />
        ) : (
          <>
            <Table
              caption="Solicitudes de reconocimiento"
              columns={columns}
              rows={items}
              rowKey={(item) => item.id ?? `${item.student?.id}-${item.submittedAt}`}
              className="hidden md:block"
            />
            <ul className="flex flex-col gap-3 md:hidden">
              {items.map((item) => (
                <RequestCard key={item.id ?? item.submittedAt} item={item} zone={zone} />
              ))}
            </ul>
            <Pagination page={page} totalPages={totalPages} onPageChange={setPage} label="Páginas de solicitudes" />
          </>
        )}
      </Tabs>
    </>
  );
}
