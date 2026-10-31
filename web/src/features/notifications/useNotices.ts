import { useApiMutation, useApiQuery } from '@/shared/api';

/** Every query of this feature starts with it, so marking a notice read refreshes the bell and the page. */
export const NOTICES_KEY = ['notifications'] as const;

/** The bell refreshes on load and every minute while the tab is visible: there is no real time. */
export const BELL_REFRESH_MS = 60_000;

export interface NoticesQuery {
  unread: boolean;
  page: number;
  size: number;
}

/** A page of the person's notices, newest first, with `unreadCount` of all of them. */
export function useNotices({ unread, page, size }: NoticesQuery, options: { refetchInterval?: number } = {}) {
  return useApiQuery(['notifications', 'list', { unread, page, size }], '/api/v1/notifications', {
    query: { unread, page, size },
    refetchInterval: options.refetchInterval,
    refetchIntervalInBackground: false,
  });
}

export function useMarkRead() {
  return useApiMutation('post', '/api/v1/notifications/{id}/read', { invalidates: [NOTICES_KEY] });
}

export function useMarkAllRead() {
  return useApiMutation('post', '/api/v1/notifications/read-all', { invalidates: [NOTICES_KEY] });
}
