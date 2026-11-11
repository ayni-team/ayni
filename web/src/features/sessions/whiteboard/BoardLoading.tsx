import { Spinner } from '@/shared/ui';

/** While Excalidraw loads: it is a chunk of its own. */
export function BoardLoading({ label }: { label: string }) {
  return (
    <div className="absolute inset-0 flex items-center justify-center gap-2 text-sm text-ink-2">
      <Spinner size="sm" decorative />
      {label}
    </div>
  );
}
