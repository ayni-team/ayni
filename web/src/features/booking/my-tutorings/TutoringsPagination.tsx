export interface TutoringsPaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  label?: string;
}

/**
 * Pagination component matching the Figma design for FE-12:
 * Text-only links in brand red (no button borders or chevron icons).
 * On desktop they are aligned to the left with gap-8; on mobile they space across the screen.
 */
export function TutoringsPagination({
  page,
  totalPages,
  onPageChange,
  label = 'Paginación de tutorías',
}: TutoringsPaginationProps) {
  if (totalPages <= 1) return null;

  const isFirst = page <= 0;
  const isLast = page >= totalPages - 1;

  return (
    <nav
      aria-label={label}
      className="flex items-center justify-between sm:justify-start sm:gap-8 pt-6 px-1"
    >
      <button
        type="button"
        disabled={isFirst}
        onClick={() => onPageChange(page - 1)}
        className="text-sm font-semibold text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand disabled:pointer-events-none disabled:opacity-40"
      >
        Anterior
      </button>

      <span className="text-sm text-gray-600 select-none">
        Página {page + 1} de {totalPages}
      </span>

      <button
        type="button"
        disabled={isLast}
        onClick={() => onPageChange(page + 1)}
        className="text-sm font-semibold text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand disabled:pointer-events-none disabled:opacity-40"
      >
        Siguiente
      </button>
    </nav>
  );
}
