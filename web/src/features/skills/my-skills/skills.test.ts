import { describe, expect, it } from 'vitest';
import {
  categoryName,
  coursesToOffer,
  evidenceProblem,
  skillDetail,
  skillStatus,
  sortedSkills,
  toolsToValidate,
} from './skills';

const file = (name: string, type: string, size = 100) => ({ name, type, size });

describe('FE-42 skills logic', () => {
  it('writes the status in Spanish, and an unknown one too', () => {
    expect(skillStatus('ENABLED').label).toBe('Habilitada');
    expect(skillStatus('PENDING').label).toBe('En revisión');
    expect(skillStatus(undefined).label).toBe('Sin estado');
  });

  it('says what a skill is', () => {
    expect(skillDetail({ scope: 'UNIVERSITY', courseCode: 'CC215', accreditedGrade: 18.5 })).toBe('CC215 · nota 18.5');
    expect(skillDetail({ scope: 'UNIVERSITY' })).toBe('Curso');
    expect(skillDetail({ scope: 'GLOBAL' })).toBe('Herramienta');
  });

  it('puts what is taught first, then by name', () => {
    const sorted = sortedSkills([
      { name: 'B', status: 'WITHDRAWN' },
      { name: 'Z', status: 'ENABLED' },
      { name: 'A', status: 'ENABLED' },
      { name: 'C', status: 'PENDING' },
    ]);
    expect(sorted.map((skill) => skill.name)).toEqual(['A', 'Z', 'C', 'B']);
  });

  it('offers only tools that are not taught or under review', () => {
    const items = [
      { id: 'figma', scope: 'GLOBAL' },
      { id: 'excel', scope: 'GLOBAL' },
      { id: 'old', scope: 'GLOBAL' },
      { id: 'course', scope: 'UNIVERSITY' },
    ];
    const skills = [
      { catalogItemId: 'figma', status: 'ENABLED' },
      { catalogItemId: 'old', status: 'REJECTED' },
    ];
    expect(toolsToValidate(items, skills).map((item) => item.id)).toEqual(['excel', 'old']);
  });

  it('does not offer twice a course already on the list', () => {
    const suggestions = [{ item: { id: 'a' } }, { item: { id: 'b' } }];
    expect(coursesToOffer(suggestions, [{ catalogItemId: 'a', status: 'WITHDRAWN' }])).toEqual([{ item: { id: 'b' } }]);
  });

  it('names the file that is wrong', () => {
    expect(evidenceProblem([])).toBe('Elige al menos un archivo');
    expect(evidenceProblem(Array(4).fill(file('a.pdf', 'application/pdf')))).toBe('Puedes subir hasta 3 archivos');
    expect(evidenceProblem([file('a.exe', 'application/x-msdownload')])).toBe('a.exe: debe ser PDF, PNG o JPEG');
    expect(evidenceProblem([file('a.pdf', 'application/pdf', 0)])).toBe('a.pdf: el archivo está vacío');
    expect(evidenceProblem([file('a.png', 'image/png', 6 * 1024 * 1024)])).toBe('a.png: pesa más de 5 MB');
    expect(evidenceProblem([file('a.png', 'image/png'), file('b.jpg', 'image/jpeg')])).toBeUndefined();
  });

  it('translates the categories it knows', () => {
    expect(categoryName('Technology')).toBe('Tecnología');
    expect(categoryName('Music')).toBe('Music');
  });
});
