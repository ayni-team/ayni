import { IconCheck } from '@/shared/ui';

export interface CancellationBannerProps {
  message: string;
}

/**
 * Notice banner shown at the top of the tutorings list after successfully cancelling a booking.
 * Matches the dark rounded pill shown in the Figma 204 cancelled state.
 */
export function CancellationBanner({ message }: CancellationBannerProps) {
  return (
    <div
      role="status"
      className="mb-4 inline-flex items-center gap-2 rounded-lg bg-gray-900 px-4 py-2.5 text-sm font-medium text-white shadow-sm"
    >
      <IconCheck className="h-4 w-4 text-white flex-shrink-0" />
      <span>{message}</span>
    </div>
  );
}
