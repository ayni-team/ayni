import type { Schema } from '@/shared/api';
import { ratingTagLabel } from '../ratingTags';

export type RegisteredRating = Schema<'RegisteredRating'>;

export const STARS = [1, 2, 3, 4, 5] as const;
export const COMMENT_LIMIT = 500;

export interface TutorFacts {
  wasPunctual: boolean;
  connectionOk: boolean;
  sessionFlowed: boolean;
}

/** The three facts the tutor answers, in the order of the frame. */
export const FACTS: readonly { key: keyof TutorFacts; label: string }[] = [
  { key: 'wasPunctual', label: 'Fue puntual' },
  { key: 'connectionOk', label: 'La conexión funcionó' },
  { key: 'sessionFlowed', label: 'La sesión fluyó' },
];

/** Selects a tag, or takes it back. */
export function toggled(tags: readonly string[], tag: string): string[] {
  return tags.includes(tag) ? tags.filter((other) => other !== tag) : [...tags, tag];
}

/** What is typed, without the blanks around it, and nothing rather than an empty comment. */
export function commentOf(text: string): string | undefined {
  return text.trim() || undefined;
}

export function starsLabel(stars: number): string {
  return stars === 1 ? '1 estrella' : `${stars} estrellas`;
}

/** "Calificaste con 5 estrellas · Explica con claridad, Puntual." */
export function studentSummary(rating: RegisteredRating): string {
  const tags = (rating.tags ?? []).map(ratingTagLabel).join(', ');
  const stars = `Calificaste con ${starsLabel(rating.stars ?? 0)}`;
  return tags ? `${stars} · ${tags}.` : `${stars}.`;
}

/** The three facts of a tutor's rating, as they read after answering. */
export function factsSummary(rating: RegisteredRating): string[] {
  return [
    rating.wasPunctual ? 'Fue puntual' : 'No fue puntual',
    rating.connectionOk ? 'La conexión funcionó' : 'La conexión falló',
    rating.sessionFlowed ? 'La sesión fluyó' : 'La sesión no fluyó',
  ];
}
