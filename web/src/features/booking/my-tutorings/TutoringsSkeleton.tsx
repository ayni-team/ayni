/**
 * Skeleton loader for the tutorings list, displayed while fetching bookings.
 * Renders 4 card placeholders to match the Figma loading state.
 */
export function TutoringsSkeleton() {
  return (
    <div className="space-y-4" role="status" aria-label="Cargando tutorías">
      {Array.from({ length: 4 }).map((_, index) => (
        <div key={index} className="flex items-center gap-4 rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
          {/* Date box skeleton */}
          <div className="h-16 w-14 sm:h-16 sm:w-16 flex-shrink-0 rounded-xl bg-gray-200 animate-pulse" />

          {/* Details skeleton */}
          <div className="min-w-0 flex-1 space-y-2.5">
            <div className="h-4 w-44 sm:w-60 rounded bg-gray-200 animate-pulse" />
            <div className="h-3.5 w-32 sm:w-44 rounded bg-gray-200 animate-pulse" />
            <div className="h-3.5 w-24 sm:w-32 rounded bg-gray-200 animate-pulse" />
          </div>
        </div>
      ))}
    </div>
  );
}
