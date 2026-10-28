import type { Key, ReactNode } from 'react';
import { cx } from './cx';

export interface TableColumn<T> {
  key: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  align?: 'left' | 'right' | 'center';
  className?: string;
}

export interface TableProps<T> {
  /** What the table is, for screen readers. Hidden unless `showCaption`. */
  caption: string;
  showCaption?: boolean;
  columns: readonly TableColumn<T>[];
  rows: readonly T[];
  rowKey: (row: T) => Key;
  className?: string;
}

const ALIGN = { left: 'text-left', right: 'text-right', center: 'text-center' };

/**
 * A data table. On a narrow screen it scrolls sideways inside its own box, never the page. An empty
 * list is not drawn as an empty table: the screen shows an `EmptyState` instead.
 */
export function Table<T>({ caption, showCaption = false, columns, rows, rowKey, className }: TableProps<T>) {
  return (
    <div className={cx('overflow-x-auto rounded-xl border border-gray-200 bg-white', className)}>
      <table className="min-w-full divide-y divide-gray-200 text-sm">
        <caption className={showCaption ? 'px-4 py-3 text-left font-semibold text-gray-900' : 'sr-only'}>
          {caption}
        </caption>
        <thead className="bg-gray-50">
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={cx(
                  'whitespace-nowrap px-4 py-3 font-medium text-gray-700',
                  ALIGN[column.align ?? 'left'],
                  column.className,
                )}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {rows.map((row) => (
            <tr key={rowKey(row)} className="hover:bg-gray-50">
              {columns.map((column) => (
                <td
                  key={column.key}
                  className={cx('px-4 py-3 text-gray-900', ALIGN[column.align ?? 'left'], column.className)}
                >
                  {column.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
