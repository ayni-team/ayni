import { useApiQuery } from '@/shared/api';
import {
  Badge,
  ButtonLink,
  Card,
  EmptyState,
  ErrorState,
  IconAlert,
  IconAward,
  IconStar,
  PageHeader,
  Skeleton,
} from '@/shared/ui';
import { ratingTagLabel } from '../ratingTags';
import {
  averageView,
  bySignificance,
  countText,
  missingText,
  riskText,
  starsText,
  type SkillReputation,
} from './reputation';

function Average({ skill }: { skill: SkillReputation }) {
  const average = averageView(skill);
  if (average.kind === 'none') {
    return <p className="text-sm text-ink-2">Aún no tienes calificaciones en esta habilidad.</p>;
  }
  return (
    <div>
      <p className="flex items-center gap-1.5">
        <IconStar className="h-5 w-5 text-credit-text" />
        <span className="font-display text-[28px] font-semibold tabular-nums leading-tight text-ink">
          {starsText(average.value)}
        </span>
        {average.kind === 'provisional' && <Badge tone="info">Provisional</Badge>}
      </p>
      {average.kind === 'provisional' && <p className="mt-1 text-xs text-ink-2">{missingText(average.missing)}</p>}
    </div>
  );
}

function SkillCard({ skill, minimumAverage }: { skill: SkillReputation; minimumAverage?: number }) {
  const tags = skill.topTags ?? [];
  return (
    <Card as="article" title={skill.name ?? 'Habilidad'}>
      <div className="flex flex-col gap-3">
        <Average skill={skill} />
        <p className="text-sm text-ink-2">
          {countText(skill.sessionsTaught, 'sesión enseñada', 'sesiones enseñadas')} ·{' '}
          {countText(skill.ratingsCount, 'calificación', 'calificaciones')}
        </p>
        {skill.atRisk && (
          <p className="flex items-start gap-2 rounded-xl bg-credit-soft p-3 text-sm text-ink">
            <IconAlert className="mt-0.5 h-4 w-4 shrink-0 text-credit-text" />
            {riskText(minimumAverage)}
          </p>
        )}
        {tags.length > 0 && (
          <div>
            <h3 className="text-[13px] font-semibold text-ink">Lo que más destacan</h3>
            <ul className="mt-2 flex flex-wrap gap-2">
              {tags.map((item) => (
                <li key={item.tag ?? 'tag'}>
                  <Badge>
                    {ratingTagLabel(item.tag ?? 'Sin etiqueta')}
                    {item.count !== undefined && ` · ${item.count}`}
                  </Badge>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </Card>
  );
}

/**
 * FE-43 · `/tutor/reputacion` · student as tutor. Read-only.
 *
 * One card per skill: sessions taught, the average (or the provisional one and how many ratings are
 * missing), the tags given the most and a warning when the skill is at risk.
 */
export default function ReputationPage() {
  const reputation = useApiQuery(['reputation', 'tutor'], '/api/v1/tutor/reputation');
  const skills = bySignificance(reputation.data?.skills ?? []);

  return (
    <>
      <PageHeader title="Reputación" description="Cómo valoran tus estudiantes las tutorías de cada habilidad." />
      {reputation.isPending ? (
        <div className="grid gap-4 md:grid-cols-2">
          {['a', 'b'].map((key) => (
            <Card key={key}>
              <Skeleton lines={3} label="Cargando tu reputación" />
            </Card>
          ))}
        </div>
      ) : reputation.isError ? (
        <ErrorState
          title="No pudimos cargar tu reputación"
          error={reputation.error}
          onRetry={() => void reputation.refetch()}
          retrying={reputation.isFetching}
        />
      ) : skills.length === 0 ? (
        <EmptyState
          icon={<IconAward className="h-8 w-8" />}
          title="Todavía no tienes reputación"
          description="Ofrece una habilidad y, cuando enseñes, aquí verás cómo te califican."
          action={<ButtonLink to="/tutor/habilidades">Ver mis habilidades</ButtonLink>}
        />
      ) : (
        <div className="grid items-start gap-4 md:grid-cols-2">
          {skills.map((skill) => (
            <SkillCard
              key={skill.catalogItemId ?? skill.name}
              skill={skill}
              minimumAverage={reputation.data?.minimumAverage}
            />
          ))}
        </div>
      )}
    </>
  );
}
