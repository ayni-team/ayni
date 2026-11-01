import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { useZone } from '@/shared/time';
import {
  IconAlert,
  IconAward,
  IconBell,
  IconCalendar,
  IconCheck,
  IconChevronRight,
  IconClock,
  IconClose,
  IconWallet,
  cx,
} from '@/shared/ui';
import { noticeView, noticeWhen, type Notice, type NoticeIcon } from './notice';
import { useMarkRead } from './useNotices';

/** The shield of an evidence that was reviewed, as the frame draws it; `shared/ui` has none. */
function IconShield({ className = 'h-5 w-5' }: { className?: string }) {
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
      <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

const ICONS: Record<NoticeIcon, (props: { className?: string }) => ReactNode> = {
  calendar: IconCalendar,
  check: IconCheck,
  clock: IconClock,
  wallet: IconWallet,
  alert: IconAlert,
  cancel: IconClose,
  award: IconAward,
  shield: IconShield,
  bell: IconBell,
};

export interface NoticeListProps {
  notices: Notice[];
  /** The tray of the bell is tighter than the page. */
  compact?: boolean;
  /** Called after a notice was opened, so the tray can close. */
  onOpen?: () => void;
}

/**
 * The notices, newest first. An unread one wears the tint and the dot of the university. Opening a
 * notice marks it read and goes to the screen it is about; one without a screen is only marked.
 */
export function NoticeList({ notices, compact = false, onOpen }: NoticeListProps) {
  const zone = useZone();
  const navigate = useNavigate();
  const markRead = useMarkRead();
  const now = new Date(Date.now());

  function open(notice: Notice, to: string | undefined, unread: boolean) {
    if (unread && notice.id) markRead.mutate({ path: { id: notice.id } });
    onOpen?.();
    if (to) navigate(to);
  }

  return (
    <ul className="divide-y divide-line">
      {notices.map((notice, index) => {
        const view = noticeView(notice, zone);
        const Icon = ICONS[view.icon];
        const when = noticeWhen(notice.createdAt, zone, now);
        return (
          <li key={notice.id ?? index}>
            <button
              type="button"
              onClick={() => open(notice, view.to, view.unread)}
              className={cx(
                'flex w-full items-center gap-3 text-left focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-brand',
                compact ? 'px-3 py-2.5' : 'px-4 py-3.5 sm:px-5',
                view.unread ? 'bg-brand-soft hover:brightness-[0.98]' : 'bg-white hover:bg-gray-50',
              )}
            >
              <span
                aria-hidden="true"
                className={cx('h-2 w-2 shrink-0 rounded-full', view.unread ? 'bg-brand' : 'bg-transparent')}
              />
              <span
                aria-hidden="true"
                className={cx(
                  'inline-flex shrink-0 items-center justify-center rounded-full',
                  compact ? 'h-8 w-8' : 'h-9 w-9',
                  view.unread ? 'bg-white text-brand-text' : 'bg-gray-100 text-ink-2',
                )}
              >
                <Icon className="h-[18px] w-[18px]" />
              </span>
              <span className="min-w-0 flex-1">
                <span
                  className={cx('block text-ink', compact ? 'text-sm' : 'text-[15px]', view.unread && 'font-semibold')}
                >
                  {view.title}
                  {view.unread && <span className="sr-only"> (sin leer)</span>}
                </span>
                {view.detail && <span className="mt-0.5 block text-xs text-ink-2">{view.detail}</span>}
              </span>
              {when && <span className="shrink-0 text-xs text-ink-2">{when}</span>}
              {!compact && <IconChevronRight className="h-4 w-4 shrink-0 text-ink-3" />}
            </button>
          </li>
        );
      })}
    </ul>
  );
}
