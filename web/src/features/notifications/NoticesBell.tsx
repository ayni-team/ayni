import { Link } from 'react-router-dom';
import { IconBell } from '@/shared/ui';

/**
 * The bell of the header, mounted by the frame for students and coordinators. For now it only
 * leads to `/avisos`; FE-60 replaces it with the counter of `NotificationsPage.unreadCount`
 * (in the university's colour, refreshed every 60 seconds while the tab is visible).
 */
export function NoticesBell() {
  return (
    <Link
      to="/avisos"
      aria-label="Avisos"
      className="relative inline-flex rounded-md p-2 text-gray-700 hover:bg-gray-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
    >
      <IconBell />
    </Link>
  );
}
