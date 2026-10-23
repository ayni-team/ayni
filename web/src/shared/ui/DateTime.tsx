import { formatInZone, type DateTimePreset } from '../time/format';
import { useZone } from '../time/useZone';

export interface DateTimeProps {
  /** An ISO 8601 instant, as the API sends it (UTC). */
  value: string;
  /** The zone to show it in: the list's `timezone`. The university's when absent; never the browser's. */
  zone?: string;
  format?: DateTimePreset | Intl.DateTimeFormatOptions;
  className?: string;
}

/** An instant in the university's zone, as a `<time>` that keeps the exact value. */
export function DateTime({ value, zone, format = 'dateTime', className }: DateTimeProps) {
  const universityZone = useZone();
  return (
    <time dateTime={value} className={className}>
      {formatInZone(value, zone ?? universityZone, format)}
    </time>
  );
}
