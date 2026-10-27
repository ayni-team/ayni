/** Joins class names, skipping the empty ones: `cx('a', active && 'b')`. */
export function cx(...names: (string | false | null | undefined)[]): string {
  return names.filter(Boolean).join(' ');
}
