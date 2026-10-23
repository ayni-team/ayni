import { Button } from './Button';
import { IconChevronLeft, IconChevronRight } from './icons';

export interface PaginationProps {
  /** The page, counted from 0 as the API counts it. */
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  label?: string;
}

/** How many pages a list has, for the answers that give `total` and `size` instead. */
export function pageCount(total: number, size: number): number {
  return size > 0 ? Math.ceil(total / size) : 0;
}

/** Previous and next, with where the person is. Nothing is drawn when everything fits in a page. */
export function Pagination({ page, totalPages, onPageChange, label = 'Paginación' }: PaginationProps) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label={label} className="flex items-center justify-between gap-3 pt-4">
      <Button
        variant="secondary"
        size="sm"
        icon={<IconChevronLeft className="h-4 w-4" />}
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        Anterior
      </Button>
      <p className="text-sm text-gray-700">
        Página {page + 1} de {totalPages}
      </p>
      <Button variant="secondary" size="sm" disabled={page >= totalPages - 1} onClick={() => onPageChange(page + 1)}>
        Siguiente
        <IconChevronRight className="h-4 w-4" />
      </Button>
    </nav>
  );
}
