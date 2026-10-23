import type { ReactNode } from 'react';
import { errorText } from '../api/errors';
import { Button } from './Button';
import { IconAlert } from './icons';

export interface ErrorStateProps {
  title?: ReactNode;
  /** The text; without it, the one of the guide's table for the error. */
  description?: ReactNode;
  error?: unknown;
  onRetry?: () => void;
  retrying?: boolean;
  /** Something else to offer instead of or besides retrying. */
  action?: ReactNode;
}

/** Something failed. It says so in Spanish (never the server's message) and offers to retry. */
export function ErrorState({ title = 'No pudimos cargar esto', description, error, onRetry, retrying, action }: ErrorStateProps) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 rounded-xl border border-red-200 bg-white px-6 py-10 text-center">
      <IconAlert className="h-8 w-8 text-red-700" />
      <h2 className="text-base font-semibold text-gray-900">{title}</h2>
      <p className="max-w-md text-sm text-gray-600">{description ?? errorText(error)}</p>
      {(onRetry || action) && (
        <div className="mt-2 flex flex-wrap justify-center gap-2">
          {onRetry && (
            <Button variant="secondary" onClick={onRetry} loading={retrying}>
              Reintentar
            </Button>
          )}
          {action}
        </div>
      )}
    </div>
  );
}
