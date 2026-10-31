import { useState } from 'react';
import {
  Button,
  EmptyState,
  ErrorState,
  IconBell,
  PageHeader,
  Pagination,
  Skeleton,
  Tabs,
  pageCount,
  useToast,
  type TabItem,
} from '@/shared/ui';
import { NoticeList } from '../NoticeList';
import { unreadSummary } from '../notice';
import { useMarkAllRead, useNotices } from '../useNotices';

type Filter = 'ALL' | 'UNREAD';

const PAGE_SIZE = 20;

/**
 * FE-60 · `/avisos` · student, coordinator.
 *
 * The person's notices, newest first, with the unread ones first in colour. Every text is written
 * here from the kind of the notice; opening one marks it read and goes to the screen it is about.
 */
export default function NoticesPage() {
  const toast = useToast();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [page, setPage] = useState(0);
  const notices = useNotices({ unread: filter === 'UNREAD', page, size: PAGE_SIZE });
  const markAll = useMarkAllRead();

  const unread = notices.data?.unreadCount ?? 0;
  const items = notices.data?.items ?? [];
  const totalPages = pageCount(notices.data?.total ?? 0, notices.data?.size ?? PAGE_SIZE);
  const tabs: readonly TabItem<Filter>[] = [
    { value: 'ALL', label: 'Todos' },
    { value: 'UNREAD', label: 'Sin leer' },
  ];

  function choose(next: Filter) {
    setFilter(next);
    setPage(0);
  }

  async function readAll() {
    try {
      await markAll.mutateAsync({});
      toast.success('Marcaste todos los avisos como leídos');
    } catch {
      toast.error('No pudimos marcar los avisos. Inténtalo de nuevo');
    }
  }

  return (
    <>
      <PageHeader
        title="Avisos"
        description={notices.data ? unreadSummary(unread) : 'Los más nuevos primero'}
        actions={
          <Button
            variant="secondary"
            onClick={() => void readAll()}
            loading={markAll.isPending}
            disabled={unread === 0}
          >
            Marcar todas como leídas
          </Button>
        }
      />
      <Tabs label="Qué avisos ver" items={tabs} value={filter} onChange={choose}>
        {notices.isPending ? (
          <Skeleton lines={6} label="Cargando tus avisos" className="rounded-[20px] border border-line bg-white p-6" />
        ) : notices.isError ? (
          <ErrorState
            title="No pudimos cargar tus avisos"
            error={notices.error}
            onRetry={() => void notices.refetch()}
            retrying={notices.isFetching}
          />
        ) : items.length === 0 ? (
          <EmptyState
            icon={<IconBell className="h-8 w-8" />}
            title={filter === 'UNREAD' ? 'No tienes avisos sin leer' : 'Todavía no tienes avisos'}
            description={
              filter === 'UNREAD'
                ? 'Ya viste todo. Los avisos nuevos aparecerán aquí.'
                : 'Te avisaremos aquí cuando se confirme una tutoría, se cierre una sesión o se resuelva una solicitud.'
            }
          />
        ) : (
          <>
            <div className="overflow-hidden rounded-[20px] border border-line bg-white">
              <NoticeList notices={items} />
            </div>
            <Pagination page={page} totalPages={totalPages} onPageChange={setPage} label="Páginas de avisos" />
          </>
        )}
      </Tabs>
    </>
  );
}
