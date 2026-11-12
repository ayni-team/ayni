import type { Schema } from '@/shared/api';
import type { BadgeTone } from '@/shared/ui';

export type TutorSkill = Schema<'TutorSkill'>;
export type Proposal = Schema<'SkillProposal'>;
export type CatalogItem = Schema<'CatalogItem'>;

const SKILL_STATUS: Record<string, { label: string; tone: BadgeTone }> = {
  ENABLED: { label: 'Habilitada', tone: 'success' },
  PENDING: { label: 'En revisión', tone: 'warning' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
  WITHDRAWN: { label: 'Retirada', tone: 'neutral' },
};

export function skillStatus(status: string | undefined): { label: string; tone: BadgeTone } {
  return (status && SKILL_STATUS[status]) || { label: 'Sin estado', tone: 'neutral' };
}

const PROPOSAL_STATUS: Record<string, { label: string; tone: BadgeTone }> = {
  PROPOSED: { label: 'En revisión', tone: 'warning' },
  APPROVED: { label: 'Aprobada', tone: 'success' },
  MERGED: { label: 'Unida a otra', tone: 'info' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
};

export function proposalStatus(status: string | undefined): { label: string; tone: BadgeTone } {
  return (status && PROPOSAL_STATUS[status]) || { label: 'Sin estado', tone: 'neutral' };
}

/** A tool of the catalogue is enabled by reviewed evidence; a course of the university, by its grade. */
export function isTool(scope: string | undefined): boolean {
  return scope === 'GLOBAL';
}

/** "CC215 · nota 18.54", or what kind of skill it is. */
export function skillDetail(skill: TutorSkill): string {
  if (isTool(skill.scope)) return 'Herramienta';
  return [
    skill.courseCode ?? 'Curso',
    skill.accreditedGrade === null || skill.accreditedGrade === undefined ? null : `nota ${skill.accreditedGrade}`,
  ]
    .filter(Boolean)
    .join(' · ');
}

const ORDER: Record<string, number> = { ENABLED: 0, PENDING: 1, REJECTED: 2, WITHDRAWN: 3 };

/** What is being taught first, then by name, so the page does not move. */
export function sortedSkills(skills: readonly TutorSkill[]): TutorSkill[] {
  return [...skills].sort(
    (a, b) =>
      (ORDER[a.status ?? ''] ?? 9) - (ORDER[b.status ?? ''] ?? 9) || (a.name ?? '').localeCompare(b.name ?? '', 'es'),
  );
}

/** The tools of the catalogue the person does not teach or have under review. */
export function toolsToValidate(items: readonly CatalogItem[], skills: readonly TutorSkill[]): CatalogItem[] {
  const busy = new Set(
    skills
      .filter((skill) => skill.status === 'ENABLED' || skill.status === 'PENDING')
      .map((skill) => skill.catalogItemId),
  );
  return items.filter((item) => isTool(item.scope) && !busy.has(item.id));
}

/** The courses to offer, without those already on the list, which offer themselves from their row. */
export function coursesToOffer<T extends { item?: { id?: string } }>(
  suggestions: readonly T[],
  skills: readonly TutorSkill[],
): T[] {
  const listed = new Set(skills.map((skill) => skill.catalogItemId));
  return suggestions.filter((course) => !listed.has(course.item?.id));
}

const CATEGORY_NAMES: Record<string, string> = {
  Technology: 'Tecnología',
  Languages: 'Idiomas',
  Design: 'Diseño',
};

/** The categories are stored in English; one the web does not know goes as it comes. */
export function categoryName(name: string | undefined): string {
  return (name && CATEGORY_NAMES[name]) || name || 'Sin nombre';
}

export const MAX_FILES = 3;
export const MAX_FILE_BYTES = 5 * 1024 * 1024;
export const MAX_NOTE = 1000;
const FILE_KINDS = ['application/pdf', 'image/png', 'image/jpeg'];
export const FILE_ACCEPT = '.pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg';

/** What is wrong with the files chosen, naming the file, before anything is sent. */
export function evidenceProblem(files: readonly Pick<File, 'name' | 'size' | 'type'>[]): string | undefined {
  if (files.length === 0) return 'Elige al menos un archivo';
  if (files.length > MAX_FILES) return `Puedes subir hasta ${MAX_FILES} archivos`;
  for (const file of files) {
    if (!FILE_KINDS.includes(file.type)) return `${file.name}: debe ser PDF, PNG o JPEG`;
    if (file.size === 0) return `${file.name}: el archivo está vacío`;
    if (file.size > MAX_FILE_BYTES) return `${file.name}: pesa más de 5 MB`;
  }
  return undefined;
}
