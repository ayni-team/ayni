import { useApiQuery, type Schema } from '@/shared/api';
import {
  Badge,
  Card,
  EmptyState,
  ErrorState,
  PageHeader,
  PageLoading,
} from '@/shared/ui';
import { ratingTagLabel } from '../ratingTags';

type TutorSkillReputation = Schema<'TutorSkillReputation'>;

const REPUTATION_KEY = ['reputation', 'tutor'] as const;

function formatAverage(value?: number | null): string {
  return value == null ? '—' : value.toFixed(1);
}

function ReputationCard({
                          skill,
                          minimumAverage,
                        }: {
  skill: TutorSkillReputation;
  minimumAverage?: number;
}) {
  const ratingsCount = skill.ratingsCount ?? 0;
  const sessionsTaught = skill.sessionsTaught ?? 0;

  const hasOfficialAverage = skill.averageStars != null;
  const average = hasOfficialAverage
    ? skill.averageStars
    : skill.provisionalAverage;

  const topTags = skill.topTags ?? [];

  const missingRatings =
    skill.ratingsMissingForAverage ?? Math.max(0, 3 - ratingsCount);

  return (
    <Card
      title={skill.name ?? 'Habilidad'}
      description="Tu desempeño como tutor en esta habilidad."
    >
      <div className="flex flex-col gap-5">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          <div className="rounded-xl bg-gray-50 p-4">
            <p className="text-xs font-medium text-gray-500">
              Promedio
            </p>

            <p className="mt-1 text-2xl font-semibold text-gray-900">
              {formatAverage(average)}
              {average != null && (
                <span className="ml-1 text-sm font-normal text-gray-500">
                  / 5
                </span>
              )}
            </p>

            {!hasOfficialAverage && (
              <p className="mt-1 text-xs text-gray-500">
                Promedio provisional
              </p>
            )}
          </div>

          <div className="rounded-xl bg-gray-50 p-4">
            <p className="text-xs font-medium text-gray-500">
              Sesiones enseñadas
            </p>

            <p className="mt-1 text-2xl font-semibold text-gray-900">
              {sessionsTaught}
            </p>
          </div>

          <div className="col-span-2 rounded-xl bg-gray-50 p-4 sm:col-span-1">
            <p className="text-xs font-medium text-gray-500">
              Calificaciones
            </p>

            <p className="mt-1 text-2xl font-semibold text-gray-900">
              {ratingsCount}
            </p>
          </div>
        </div>

        {!hasOfficialAverage && (
          <div className="rounded-lg border border-blue-200 bg-blue-50 px-4 py-3">
            <p className="text-sm text-blue-900">
              {ratingsCount === 0
                ? 'Aún no tienes calificaciones.'
                : `Te faltan ${missingRatings} ${
                  missingRatings === 1
                    ? 'calificación'
                    : 'calificaciones'
                } para el promedio.`}
            </p>
          </div>
        )}

        {skill.atRisk && (
          <div
            role="alert"
            className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3"
          >
            <Badge tone="warning">Promedio por debajo del mínimo</Badge>

            <p className="mt-2 text-sm text-amber-900">
              Tu promedio está por debajo de{' '}
              {minimumAverage != null
                ? minimumAverage
                : 'el mínimo requerido'}
              .
            </p>
          </div>
        )}

        <div>
          <h3 className="text-sm font-semibold text-gray-900">
            Lo que más destacan tus estudiantes
          </h3>

          {topTags.length > 0 ? (
            <div className="mt-3 flex flex-wrap gap-2">
              {topTags.map((item, index) => (
                <Badge key={`${item.tag ?? 'tag'}-${index}`} tone="neutral">
                  {ratingTagLabel(item.tag ?? 'Sin etiqueta')}
                  {item.count != null && (
                    <span className="ml-1 text-gray-500">
                      · {item.count}
                    </span>
                  )}
                </Badge>
              ))}
            </div>
          ) : (
            <p className="mt-2 text-sm text-gray-500">
              Todavía no hay etiquetas registradas.
            </p>
          )}
        </div>
      </div>
    </Card>
  );
}

export default function ReputationPage() {
  const reputation = useApiQuery(
    REPUTATION_KEY,
    '/api/v1/tutor/reputation',
  );

  if (reputation.isPending) {
    return <PageLoading label="Cargando tu reputación" />;
  }

  if (reputation.isError) {
    return (
      <div className="mx-auto w-full max-w-6xl">
        <PageHeader
          title="Reputación"
          description="Consulta cómo valoran tus estudiantes las tutorías que brindas."
        />

        <ErrorState
          title="No pudimos cargar tu reputación"
          description="Inténtalo nuevamente dentro de un momento."
          error={reputation.error}
          onRetry={() => void reputation.refetch()}
          retrying={reputation.isFetching}
        />
      </div>
    );
  }

  const skills = reputation.data?.skills ?? [];
  const minimumAverage = reputation.data?.minimumAverage;

  return (
    <div className="mx-auto w-full max-w-6xl">
      <PageHeader
        title="Reputación"
        description="Consulta cómo valoran tus estudiantes las tutorías que brindas y revisa tu progreso en cada habilidad."
      />

      {skills.length === 0 ? (
        <EmptyState
          title="Todavía no tienes reputación registrada"
          description="Cuando tengas habilidades habilitadas o hayas brindado tutorías, aquí podrás consultar tu desempeño."
        />
      ) : (
        <div className="flex flex-col gap-5">
          {skills.map((skill, index) => (
            <ReputationCard
              key={skill.catalogItemId ?? `${skill.name ?? 'skill'}-${index}`}
              skill={skill}
              minimumAverage={minimumAverage}
            />
          ))}
        </div>
      )}
    </div>
  );
}
