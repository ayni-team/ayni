import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { isApiError, useApiMutation, useApiQuery, type Schema } from '@/shared/api';
import { useUniversity } from '@/shared/brand';
import { ME_KEY, useMe } from '@/shared/session';
import {
  Button,
  Card,
  Checkbox,
  ErrorState,
  Field,
  IconCheck,
  Skeleton,
  Spinner,
  TextArea,
  TextField,
  useToast,
} from '@/shared/ui';

type OnboardingStep = NonNullable<Schema<'MyProfileView'>['onboardingStep']>;

type ActiveStep = Exclude<OnboardingStep, 'COMPLETED'>;

type SuggestedCourse = Schema<'SuggestedCourse'>;

type CatalogItem = Schema<'CatalogItem'>;

const SUGGESTIONS_KEY = ['skills', 'suggestions'] as const;

const CATALOG_DEBOUNCE_MS = 300;

const STEPS: readonly {
  value: ActiveStep;
  label: string;
}[] = [
  {
    value: 'PROFILE',
    label: 'Perfil',
  },
  {
    value: 'ACADEMIC_RECORD',
    label: 'Récord académico',
  },
  {
    value: 'LEARNING_INTERESTS',
    label: 'Intereses',
  },
];

function stepIndex(step: ActiveStep): number {
  return STEPS.findIndex((candidate) => candidate.value === step);
}

function WizardSteps({ active }: { active: ActiveStep }) {
  const activeIndex = stepIndex(active);

  return (
    <ol aria-label="Progreso del onboarding" className="flex max-w-xl items-center gap-2 text-sm">
      {STEPS.map((step, index) => {
        const done = index < activeIndex;

        const current = index === activeIndex;

        return (
          <li key={step.value} className="flex min-w-0 flex-1 items-center gap-2 last:flex-initial">
            <div className="flex min-w-0 items-center gap-2">
              <span
                aria-current={current ? 'step' : undefined}
                className={
                  done || current
                    ? 'inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand text-xs font-semibold text-brand-secondary'
                    : 'inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full border border-gray-300 bg-white text-xs font-semibold text-gray-500'
                }
              >
                {done ? <IconCheck className="h-4 w-4" /> : index + 1}
              </span>

              <span
                className={
                  current
                    ? 'truncate font-semibold text-brand-text'
                    : done
                      ? 'hidden truncate font-medium text-gray-800 sm:inline'
                      : 'hidden truncate text-gray-500 sm:inline'
                }
              >
                {step.label}
              </span>
            </div>

            {index < STEPS.length - 1 && (
              <span className={done ? 'h-px min-w-4 flex-1 bg-brand' : 'h-px min-w-4 flex-1 bg-gray-300'} />
            )}
          </li>
        );
      })}
    </ol>
  );
}

function WizardShell({ active, children }: { active: ActiveStep; children: ReactNode }) {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <WizardSteps active={active} />

      {children}
    </div>
  );
}

function OnboardingLoading() {
  return (
    <WizardShell active="PROFILE">
      <Card className="flex flex-col gap-5">
        <Skeleton className="h-4 w-52" />

        <Skeleton className="h-4 w-full max-w-lg" />

        <Skeleton className="h-4 w-3/4" />

        <div className="flex items-center gap-2 text-sm text-gray-600">
          <Spinner size="sm" decorative />

          <span>Cargando tu perfil…</span>
        </div>
      </Card>
    </WizardShell>
  );
}

function ReadOnlyAcademicData({ me }: { me: Schema<'MyProfileView'> }) {
  const fields = [
    ['Nombre', me.fullName ?? '—'],
    ['Carrera', me.career ?? '—'],
    ['Periodo', me.currentTerm ?? '—'],
    ['Código', me.studentCode ?? '—'],
  ] as const;

  return (
    <div className="rounded-xl bg-gray-50 p-4">
      <p className="mb-4 text-xs font-semibold text-gray-600">De tu universidad · no editable</p>

      <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {fields.map(([label, value]) => (
          <div key={label}>
            <dt className="text-xs text-gray-500">{label}</dt>

            <dd className="mt-1 text-sm font-semibold text-gray-900">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

function ProfileStep({ me }: { me: Schema<'MyProfileView'> }) {
  const [photoUrl, setPhotoUrl] = useState(me.photoUrl ?? '');

  const [bio, setBio] = useState(me.bio ?? '');

  const save = useApiMutation('put', '/api/v1/me/profile', {
    invalidates: [ME_KEY],
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    save.mutate({
      body: {
        photoUrl: photoUrl.trim() || undefined,
        bio: bio.trim() || undefined,
      },
    });
  }

  const firstName = me.fullName?.trim().split(' ')[0] ?? 'estudiante';

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-gray-900">Bienvenida a Ayni, {firstName}</h1>

        <p className="mt-2 text-sm text-gray-600">
          Primero, tu perfil. Lo que viene de tu universidad no se edita aquí.
        </p>
      </div>

      <Card>
        <form className="flex flex-col gap-5" onSubmit={submit} noValidate>
          <ReadOnlyAcademicData me={me} />

          <Field label="Foto (URL, opcional)" hint="Solo tú y tus tutores ven tu foto en tu perfil">
            <TextField
              type="url"
              inputMode="url"
              placeholder="https://…"
              value={photoUrl}
              onChange={(event) => setPhotoUrl(event.target.value)}
            />
          </Field>

          <Field label="Presentación (opcional)">
            <TextArea
              rows={4}
              placeholder="Cuéntales a tus tutores qué estudias y cómo aprendes mejor."
              value={bio}
              onChange={(event) => setBio(event.target.value)}
            />
          </Field>

          {save.isError && <p className="text-sm text-red-700">No pudimos guardar tu perfil. Inténtalo de nuevo.</p>}

          <div className="flex justify-end">
            <Button type="submit" loading={save.isPending}>
              Continuar
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}

function CourseLabel({ item, grade }: { item: CatalogItem; grade?: number }) {
  return (
    <div className="flex min-w-0 flex-1 items-start justify-between gap-4">
      <div className="min-w-0">
        <p className="font-medium text-gray-900">{item.name ?? 'Curso'}</p>

        {item.courseCode && <p className="text-xs text-gray-500">{item.courseCode}</p>}
      </div>

      {grade !== undefined && (
        <span className="shrink-0 text-xs text-gray-600">
          Nota{' '}
          {grade.toLocaleString('es-PE', {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })}
        </span>
      )}
    </div>
  );
}

function ImportedCourses({ suggestions }: { suggestions: SuggestedCourse[] }) {
  const valid = suggestions.filter((suggestion) => suggestion.item?.id);

  if (valid.length === 0) {
    return (
      <p className="rounded-xl border border-gray-200 px-4 py-5 text-sm text-gray-600">
        Tu récord se importó, pero no hay cursos habilitados para enseñar todavía.
      </p>
    );
  }

  return (
    <div className="overflow-hidden rounded-xl border border-gray-200">
      {valid.map((suggestion, index) => {
        const item = suggestion.item;

        if (!item?.id) {
          return null;
        }

        return (
          <div key={item.id} className={index === 0 ? 'px-4 py-3' : 'border-t border-gray-200 px-4 py-3'}>
            <CourseLabel item={item} grade={suggestion.grade} />
          </div>
        );
      })}
    </div>
  );
}

function AcademicRecordStep({
  importedThisVisit,
  onImported,
  onContinue,
}: {
  importedThisVisit: boolean;
  onImported: () => void;
  onContinue: () => void;
}) {
  const university = useUniversity();

  const suggestions = useApiQuery(SUGGESTIONS_KEY, '/api/v1/tutor/skills/suggestions', {
    enabled: importedThisVisit,
  });

  const importRecord = useApiMutation('post', '/api/v1/me/academic-record/import', {
    invalidates: [ME_KEY, SUGGESTIONS_KEY],
    onSuccess: () => {
      onImported();
    },
  });

  function importNow() {
    importRecord.reset();

    importRecord.mutate({});
  }

  const universityCode = university?.code ?? 'tu universidad';

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-gray-900">Tu récord académico</h1>

        <p className="mt-2 text-sm text-gray-600">
          Lo traemos de {universityCode} para saber qué cursos podrías enseñar. No guardamos tus notas en tu perfil.
        </p>
      </div>

      <Card className="flex flex-col gap-5">
        {importRecord.isError ? (
          <ErrorState
            title="No pudimos importar tu récord"
            description="Inténtalo nuevamente. Si el problema continúa, puedes volver a hacerlo más tarde desde tu perfil."
            onRetry={importNow}
            retrying={importRecord.isPending}
          />
        ) : importedThisVisit ? (
          <>
            <div className="flex items-center gap-2 rounded-lg bg-sky-50 px-4 py-3 text-sm font-medium text-sky-900">
              <IconCheck className="h-4 w-4" />

              <span>Récord importado. Estos cursos de tu récord podrías enseñarlos:</span>
            </div>

            {suggestions.isPending ? (
              <div className="flex items-center gap-2 py-4 text-sm text-gray-600">
                <Spinner size="sm" decorative />

                <span>Revisando tus cursos…</span>
              </div>
            ) : suggestions.isError ? (
              <ErrorState
                error={suggestions.error}
                onRetry={() => void suggestions.refetch()}
                retrying={suggestions.isFetching}
              />
            ) : (
              <ImportedCourses suggestions={suggestions.data ?? []} />
            )}

            <div className="flex flex-wrap justify-end gap-3">
              <Button variant="ghost" onClick={importNow} loading={importRecord.isPending}>
                Volver a importar
              </Button>

              <Button onClick={onContinue} disabled={suggestions.isPending || suggestions.isError}>
                Continuar
              </Button>
            </div>
          </>
        ) : (
          <>
            <div>
              <p className="text-sm font-semibold text-gray-900">Aún no importaste tu récord.</p>

              <p className="mt-2 text-sm text-gray-600">
                Toma unos segundos. Puedes volver a importarlo después desde tu perfil.
              </p>
            </div>

            <div className="flex justify-end">
              <Button onClick={importNow} loading={importRecord.isPending}>
                Importar mi récord
              </Button>
            </div>
          </>
        )}
      </Card>
    </div>
  );
}

function toggleId(current: string[], id: string): string[] {
  return current.includes(id) ? current.filter((candidate) => candidate !== id) : [...current, id];
}

function SelectableCatalog({
  items,
  selected,
  onToggle,
}: {
  items: CatalogItem[];
  selected: string[];
  onToggle: (id: string) => void;
}) {
  if (items.length === 0) {
    return <p className="text-sm text-gray-600">No encontramos cursos con esa búsqueda.</p>;
  }

  return (
    <div className="flex flex-col gap-3">
      {items.map((item) => {
        const id = item.id;

        if (!id) {
          return null;
        }

        return (
          <Checkbox
            key={id}
            checked={selected.includes(id)}
            onChange={() => onToggle(id)}
            label={`${item.name ?? 'Curso'}${item.courseCode ? ` · ${item.courseCode}` : ''}`}
          />
        );
      })}
    </div>
  );
}

function TeachingSuggestions({
  suggestions,
  selected,
  onToggle,
}: {
  suggestions: SuggestedCourse[];
  selected: string[];
  onToggle: (id: string) => void;
}) {
  const valid = suggestions.filter((suggestion) => suggestion.item?.id);

  if (valid.length === 0) {
    return <p className="text-sm text-gray-600">No hay cursos de tu récord habilitados para enseñar todavía.</p>;
  }

  return (
    <div className="flex flex-col gap-3">
      {valid.map((suggestion) => {
        const item = suggestion.item;

        const id = item?.id;

        if (!item || !id) {
          return null;
        }

        return (
          <Checkbox
            key={id}
            checked={selected.includes(id)}
            onChange={() => onToggle(id)}
            label={`${item.name ?? 'Curso'}${item.courseCode ? ` · ${item.courseCode}` : ''}`}
          />
        );
      })}
    </div>
  );
}

function InterestsStep({ onBack, refetchMe }: { onBack: () => void; refetchMe: () => void }) {
  const navigate = useNavigate();

  const toaster = useToast();

  const university = useUniversity();

  const [search, setSearch] = useState('');

  const [debouncedSearch, setDebouncedSearch] = useState('');

  const [learning, setLearning] = useState<string[]>([]);

  const [teaching, setTeaching] = useState<string[]>([]);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search.trim()), CATALOG_DEBOUNCE_MS);

    return () => {
      window.clearTimeout(timer);
    };
  }, [search]);

  const suggestions = useApiQuery(SUGGESTIONS_KEY, '/api/v1/tutor/skills/suggestions');

  const catalog = useApiQuery(
    [
      'skills',
      'catalog',
      {
        q: debouncedSearch,
      },
    ],
    '/api/v1/catalog',
    {
      query: {
        q: debouncedSearch || undefined,
        page: 0,
        size: 20,
      },
    },
  );

  const finish = useApiMutation('post', '/api/v1/me/onboarding/interests', {
    invalidates: [ME_KEY],

    onSuccess: (response) => {
      const enabledIds = new Set(
        (response.teaching ?? []).map((skill) => skill.catalogItemId).filter((id): id is string => Boolean(id)),
      );

      const skipped = (suggestions.data ?? [])
        .filter((suggestion) => {
          const id = suggestion.item?.id;

          return id !== undefined && teaching.includes(id) && !enabledIds.has(id);
        })
        .map((suggestion) => suggestion.item?.name ?? 'Un curso');

      if (skipped.length > 0) {
        toaster.info(`${skipped.join(', ')} no quedó habilitado: su nota no alcanza el mínimo de la universidad.`);
      }

      navigate('/buscar', {
        replace: true,
      });
    },

    onError: (error) => {
      if (isApiError(error, 409)) {
        refetchMe();
      }
    },
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    finish.mutate({
      body: {
        learning,
        teaching,
      },
    });
  }

  const catalogItems = catalog.data?.items ?? [];

  const universityCode = university?.code ?? 'tu universidad';

  if (suggestions.isPending) {
    return (
      <Card className="flex flex-col gap-4">
        <Skeleton className="h-5 w-56" />

        <Skeleton className="h-4 w-full max-w-lg" />

        <div className="flex items-center gap-2 text-sm text-gray-600">
          <Spinner size="sm" decorative />

          <span>Cargando tus cursos…</span>
        </div>
      </Card>
    );
  }

  if (suggestions.isError) {
    return (
      <ErrorState
        error={suggestions.error}
        onRetry={() => void suggestions.refetch()}
        retrying={suggestions.isFetching}
      />
    );
  }

  if (finish.isError && !isApiError(finish.error, 409)) {
    return (
      <ErrorState
        title="No pudimos cargar esto"
        description="No pudimos guardar tus intereses. Lo que marcaste sigue aquí; vuelve a intentarlo."
        onRetry={() =>
          finish.mutate({
            body: {
              learning,
              teaching,
            },
          })
        }
        retrying={finish.isPending}
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-gray-900">
          Lo que necesitas y lo que puedes enseñar
        </h1>

        <p className="mt-2 text-sm text-gray-600">
          Elige en las dos listas. Ambas pueden quedar vacías y las cambias después.
        </p>
      </div>

      <form onSubmit={submit} className="flex flex-col gap-5">
        <div className="grid gap-4 md:grid-cols-2">
          <Card className="flex flex-col gap-4">
            <div>
              <h2 className="text-base font-semibold text-gray-900">¿En qué necesitas ayuda?</h2>

              <p className="mt-1 text-xs text-gray-600">Del catálogo de {universityCode}.</p>
            </div>

            <Field label="Buscar en el catálogo">
              <TextField
                type="search"
                placeholder="Curso o tema"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </Field>

            {catalog.isPending || catalog.isFetching ? (
              <div className="flex items-center gap-2 py-3 text-sm text-gray-600">
                <Spinner size="sm" decorative />

                <span>Buscando cursos…</span>
              </div>
            ) : catalog.isError ? (
              <ErrorState error={catalog.error} onRetry={() => void catalog.refetch()} retrying={catalog.isFetching} />
            ) : (
              <SelectableCatalog
                items={catalogItems}
                selected={learning}
                onToggle={(id) => setLearning((current) => toggleId(current, id))}
              />
            )}
          </Card>

          <Card className="flex flex-col gap-4">
            <div>
              <h2 className="text-base font-semibold text-gray-900">¿Qué puedes enseñar?</h2>

              <p className="mt-1 text-xs text-gray-600">Solo cursos de tu récord.</p>
            </div>

            <TeachingSuggestions
              suggestions={suggestions.data ?? []}
              selected={teaching}
              onToggle={(id) => setTeaching((current) => toggleId(current, id))}
            />
          </Card>
        </div>

        {finish.isError && isApiError(finish.error, 409) && (
          <p className="text-sm text-red-700">Falta completar un paso anterior. Volviendo al paso pendiente…</p>
        )}

        <div className="flex justify-end gap-3">
          <Button variant="ghost" onClick={onBack}>
            Atrás
          </Button>

          <Button type="submit" loading={finish.isPending}>
            Terminar
          </Button>
        </div>
      </form>
    </div>
  );
}

function OnboardingWizard({ me, refetchMe }: { me: Schema<'MyProfileView'>; refetchMe: () => void }) {
  const [showImportedRecord, setShowImportedRecord] = useState(false);

  const serverStep = me.onboardingStep ?? 'PROFILE';

  if (serverStep === 'COMPLETED') {
    return <Navigate to="/buscar" replace />;
  }

  const visibleStep: ActiveStep = showImportedRecord ? 'ACADEMIC_RECORD' : serverStep;

  if (visibleStep === 'PROFILE') {
    return (
      <WizardShell active="PROFILE">
        <ProfileStep me={me} />
      </WizardShell>
    );
  }

  if (visibleStep === 'ACADEMIC_RECORD') {
    return (
      <WizardShell active="ACADEMIC_RECORD">
        <AcademicRecordStep
          importedThisVisit={showImportedRecord}
          onImported={() => setShowImportedRecord(true)}
          onContinue={() => setShowImportedRecord(false)}
        />
      </WizardShell>
    );
  }

  return (
    <WizardShell active="LEARNING_INTERESTS">
      <InterestsStep refetchMe={refetchMe} onBack={() => setShowImportedRecord(true)} />
    </WizardShell>
  );
}

/**
 * FE-03 · `/onboarding` · student.
 *
 * The server's onboardingStep
 * is always the source of truth.
 */
export default function OnboardingPage() {
  const me = useMe();

  const refetchMe = () => {
    void me.refetch();
  };

  if (me.isPending) {
    return <OnboardingLoading />;
  }

  if (me.isError) {
    return <ErrorState error={me.error} onRetry={refetchMe} retrying={me.isFetching} />;
  }

  const profile = me.data;

  if (!profile) {
    return <OnboardingLoading />;
  }

  return <OnboardingWizard me={profile} refetchMe={refetchMe} />;
}
