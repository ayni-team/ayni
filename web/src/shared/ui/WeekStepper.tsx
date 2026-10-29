import { cx } from './cx';
import { IconChevronLeft, IconChevronRight } from './icons';

export interface WeekStepperProps {
  /** The range shown, already in words ("26 oct – 1 nov"). */
  text: string;
  onPrevious: () => void;
  onNext: () => void;
  /** What the group is, for screen readers ("Semana"). */
  label?: string;
  previousLabel?: string;
  nextLabel?: string;
  disablePrevious?: boolean;
  disableNext?: boolean;
  className?: string;
}

const STEP =
  'flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-ink-2 hover:bg-surface hover:text-ink ' +
  'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-brand ' +
  'disabled:pointer-events-none disabled:opacity-40';

/**
 * The week before and after, as FE-10 and FE-41 draw it: a box as tall as a field with a chevron on
 * each side and the range in the middle.
 */
export function WeekStepper({
  text,
  onPrevious,
  onNext,
  label = 'Semana',
  previousLabel = 'Semana anterior',
  nextLabel = 'Semana siguiente',
  disablePrevious,
  disableNext,
  className,
}: WeekStepperProps) {
  return (
    <div
      role="group"
      aria-label={label}
      className={cx('flex h-11 items-center justify-between rounded-xl border border-line bg-white', className)}
    >
      <button type="button" onClick={onPrevious} disabled={disablePrevious} aria-label={previousLabel} className={STEP}>
        <IconChevronLeft />
      </button>
      <span className="select-none px-2 text-[15px] font-medium tabular-nums text-ink" aria-live="polite">
        {text}
      </span>
      <button type="button" onClick={onNext} disabled={disableNext} aria-label={nextLabel} className={STEP}>
        <IconChevronRight />
      </button>
    </div>
  );
}
