import type { Schema } from '@/shared/api';

export type SkillReputation = Schema<'TutorSkillReputation'>;

const STARS = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** "4.5": one decimal, the way the rest of the web writes stars. */
export function starsText(value: number): string {
  return STARS.format(value);
}

export type AverageView =
  | { kind: 'official'; value: number }
  | { kind: 'provisional'; value: number; missing: number }
  | { kind: 'none'; missing: number };

/**
 * What the average card says. Three ratings make an average; below that the tutor alone sees the
 * provisional one and how many are missing. Nothing is shown as an average when nobody has rated.
 */
export function averageView(skill: SkillReputation): AverageView {
  const missing = skill.ratingsMissingForAverage ?? Math.max(0, 3 - (skill.ratingsCount ?? 0));
  if (skill.averageStars !== null && skill.averageStars !== undefined) {
    return { kind: 'official', value: skill.averageStars };
  }
  if (skill.provisionalAverage !== null && skill.provisionalAverage !== undefined) {
    return { kind: 'provisional', value: skill.provisionalAverage, missing };
  }
  return { kind: 'none', missing };
}

/** "te faltan 2 calificaciones para el promedio". */
export function missingText(missing: number): string {
  return missing === 1
    ? 'Te falta 1 calificación para el promedio'
    : `Te faltan ${missing} calificaciones para el promedio`;
}

/** "1 sesión", "3 calificaciones". */
export function countText(count: number | undefined, one: string, many: string): string {
  const value = count ?? 0;
  return `${value} ${value === 1 ? one : many}`;
}

/** The warning of a skill at risk. Without the minimum it still warns, in general. */
export function riskText(minimumAverage: number | undefined): string {
  return minimumAverage === undefined
    ? 'Tu promedio está por debajo del mínimo'
    : `Tu promedio está por debajo de ${starsText(minimumAverage)}`;
}

/**
 * A skill at risk first, then the most taught: a tutor may offer many courses and teach few, and
 * what they want to read is not at the bottom of the list. Names break the ties, so it does not move.
 */
export function bySignificance(skills: readonly SkillReputation[]): SkillReputation[] {
  return [...skills].sort(
    (a, b) =>
      Number(b.atRisk ?? false) - Number(a.atRisk ?? false) ||
      (b.sessionsTaught ?? 0) - (a.sessionsTaught ?? 0) ||
      (b.ratingsCount ?? 0) - (a.ratingsCount ?? 0) ||
      (a.name ?? '').localeCompare(b.name ?? '', 'es'),
  );
}
