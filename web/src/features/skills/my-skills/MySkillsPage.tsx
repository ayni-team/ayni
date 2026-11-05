import { useState } from 'react';
import { errorText, useApiMutation, useApiQuery } from '@/shared/api';
import {
  Badge,
  Button,
  Card,
  Combobox,
  DateTime,
  Dialog,
  EmptyState,
  ErrorState,
  Field,
  IconAward,
  PageHeader,
  Skeleton,
  useToast,
} from '@/shared/ui';
import { EvidenceDialog, type EvidenceTarget } from './EvidenceDialog';
import { ProposeTool } from './ProposeTool';
import {
  coursesToOffer,
  isTool,
  skillDetail,
  skillStatus,
  sortedSkills,
  toolsToValidate,
  type TutorSkill,
} from './skills';

function SkillRow({
  skill,
  onWithdraw,
  onEvidence,
  onOffer,
  offering,
}: {
  skill: TutorSkill;
  onWithdraw: (skill: TutorSkill) => void;
  onEvidence: (target: EvidenceTarget) => void;
  onOffer: (catalogItemId: string, name: string) => void;
  offering: boolean;
}) {
  const badge = skillStatus(skill.status);
  const name = skill.name ?? 'Habilidad';
  const out = skill.status === 'REJECTED' || skill.status === 'WITHDRAWN';
  const tool = isTool(skill.scope);
  return (
    <li className="py-3 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-semibold text-ink">{name}</p>
          <p className="text-xs text-ink-2">
            {skillDetail(skill)}
            {skill.statusChangedAt && (
              <>
                {' · desde '}
                <DateTime value={skill.statusChangedAt} format="date" />
              </>
            )}
          </p>
        </div>
        <Badge tone={badge.tone}>{badge.label}</Badge>
      </div>
      {skill.review?.decisionReason && (
        <p className="mt-2 rounded-xl bg-surface p-2.5 text-[13px] text-ink">
          <span className="font-semibold">Motivo: </span>
          {skill.review.decisionReason}
        </p>
      )}
      <div className="mt-2 flex flex-wrap justify-end gap-2">
        {out && tool && (
          <Button
            size="sm"
            variant="secondary"
            onClick={() => onEvidence({ catalogItemId: skill.catalogItemId ?? '', name })}
          >
            Subir nueva evidencia
          </Button>
        )}
        {out && !tool && (
          <Button
            size="sm"
            variant="secondary"
            loading={offering}
            onClick={() => onOffer(skill.catalogItemId ?? '', name)}
          >
            Ofrecer de nuevo
          </Button>
        )}
        {!out && (
          <Button size="sm" variant="ghost" aria-label={`Retirar ${name}`} onClick={() => onWithdraw(skill)}>
            Retirar
          </Button>
        )}
      </div>
    </li>
  );
}

/**
 * FE-42 · `/tutor/habilidades` · student as tutor.
 *
 * What the person teaches and how they got there: a course enables itself if the grade clears the
 * minimum of the university, and a tool of the catalogue needs evidence a coordinator reviews.
 */
export default function MySkillsPage() {
  const toast = useToast();
  const skills = useApiQuery(['skills', 'mine'], '/api/v1/tutor/skills');
  const suggestions = useApiQuery(['skills', 'suggestions'], '/api/v1/tutor/skills/suggestions');
  const catalog = useApiQuery(['skills', 'catalog', { size: 100 }], '/api/v1/catalog', { query: { size: 100 } });
  const offer = useApiMutation('post', '/api/v1/tutor/skills', { invalidates: [['skills']] });
  const withdraw = useApiMutation('delete', '/api/v1/tutor/skills/{id}', { invalidates: [['skills']] });
  const [leaving, setLeaving] = useState<TutorSkill | null>(null);
  const [evidence, setEvidence] = useState<EvidenceTarget | null>(null);
  const [chosenTool, setChosenTool] = useState('');
  const [offeringId, setOfferingId] = useState<string>();

  const mine = sortedSkills(skills.data ?? []);
  const tools = toolsToValidate(catalog.data?.items ?? [], skills.data ?? []);
  const courses = coursesToOffer(suggestions.data ?? [], skills.data ?? []);

  async function offerCourse(catalogItemId: string, name: string) {
    setOfferingId(catalogItemId);
    try {
      const skill = await offer.mutateAsync({ body: { catalogItemId } });
      if (skill.status === 'ENABLED') toast.success(`Ya puedes enseñar ${name}`);
      else toast.info(`${name} no quedó habilitado: su nota no alcanza el mínimo de tu universidad`);
    } catch (failure) {
      toast.error(errorText(failure));
    } finally {
      setOfferingId(undefined);
    }
  }

  async function confirmWithdraw() {
    if (!leaving?.id) return;
    try {
      await withdraw.mutateAsync({ path: { id: leaving.id } });
      toast.success(`Retiraste ${leaving.name ?? 'la habilidad'}`);
    } catch (failure) {
      toast.error(errorText(failure));
    } finally {
      setLeaving(null);
    }
  }

  return (
    <>
      <PageHeader
        title="Mis habilidades"
        description="Lo que enseñas. Un curso se habilita con tu nota; una herramienta, con evidencia que revisa tu coordinación."
      />
      <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_420px]">
        <div className="min-w-0">
          {skills.isPending ? (
            <Card>
              <Skeleton lines={4} label="Cargando tus habilidades" />
            </Card>
          ) : skills.isError ? (
            <ErrorState
              title="No pudimos cargar tus habilidades"
              error={skills.error}
              onRetry={() => void skills.refetch()}
              retrying={skills.isFetching}
            />
          ) : mine.length === 0 ? (
            <EmptyState
              icon={<IconAward className="h-8 w-8" />}
              title="Todavía no ofreces ninguna habilidad"
              description="Ofrece un curso que aprobaste o sube evidencia de una herramienta."
            />
          ) : (
            <Card title="Lo que ofreces">
              <ul className="divide-y divide-line">
                {mine.map((skill) => (
                  <SkillRow
                    key={skill.id}
                    skill={skill}
                    onWithdraw={setLeaving}
                    onEvidence={setEvidence}
                    onOffer={(id, name) => void offerCourse(id, name)}
                    offering={offeringId === skill.catalogItemId}
                  />
                ))}
              </ul>
            </Card>
          )}
        </div>
        <div className="flex min-w-0 flex-col gap-4">
          <Card title="Cursos que puedes ofrecer" description="Los que aprobaste con la nota que pide tu universidad.">
            {suggestions.isPending ? (
              <Skeleton lines={2} label="Cargando los cursos" />
            ) : suggestions.isError ? (
              <p className="text-sm text-ink-2">No pudimos cargar tus cursos. Vuelve a entrar en un momento.</p>
            ) : courses.length === 0 ? (
              <p className="text-sm text-ink-2">Ya ofreces todos los cursos que tu nota te permite.</p>
            ) : (
              <ul className="flex max-h-72 flex-col gap-3 overflow-y-auto">
                {courses.map((course) => (
                  <li key={course.item?.id} className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-ink">{course.item?.name ?? 'Curso'}</p>
                      <p className="text-xs text-ink-2">
                        {[course.item?.courseCode, course.grade === undefined ? null : `nota ${course.grade}`]
                          .filter(Boolean)
                          .join(' · ')}
                      </p>
                    </div>
                    <Button
                      size="sm"
                      variant="secondary"
                      aria-label={`Ofrecer ${course.item?.name ?? 'este curso'}`}
                      loading={offeringId === course.item?.id}
                      disabled={offeringId !== undefined}
                      onClick={() => void offerCourse(course.item?.id ?? '', course.item?.name ?? 'Este curso')}
                    >
                      Ofrecer
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <Card title="Enseñar una herramienta" description="Elige una del catálogo y sube tu evidencia.">
            <div className="flex flex-col gap-3">
              <Field label="Herramienta">
                <Combobox
                  options={tools.map((tool) => ({ value: tool.id ?? '', label: tool.name ?? 'Herramienta' }))}
                  value={chosenTool}
                  onChange={setChosenTool}
                  placeholder="Escribe para buscar"
                  emptyText="No está en el catálogo. Puedes proponerla abajo."
                />
              </Field>
              <div className="flex justify-end">
                <Button
                  variant="secondary"
                  disabled={!chosenTool}
                  onClick={() => {
                    const tool = tools.find((candidate) => candidate.id === chosenTool);
                    if (tool?.id) setEvidence({ catalogItemId: tool.id, name: tool.name ?? 'la herramienta' });
                    setChosenTool('');
                  }}
                >
                  Subir evidencia
                </Button>
              </div>
            </div>
          </Card>
          <ProposeTool onEvidence={setEvidence} />
        </div>
      </div>

      <EvidenceDialog target={evidence} onClose={() => setEvidence(null)} />

      <Dialog
        open={leaving !== null}
        onClose={() => setLeaving(null)}
        dismissible={!withdraw.isPending}
        title={`¿Retirar ${leaving?.name ?? 'esta habilidad'}?`}
        description="Dejarás de aparecer en las búsquedas de esta habilidad. Las tutorías ya confirmadas se mantienen."
        footer={
          <>
            <Button variant="danger" loading={withdraw.isPending} onClick={() => void confirmWithdraw()}>
              Retirar
            </Button>
            <Button variant="secondary" disabled={withdraw.isPending} onClick={() => setLeaving(null)}>
              Cancelar
            </Button>
          </>
        }
      />
    </>
  );
}
